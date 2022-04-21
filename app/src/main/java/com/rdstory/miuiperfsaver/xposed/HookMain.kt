package com.rdstory.miuiperfsaver.xposed

import android.content.Context
import android.util.ArraySet
import androidx.annotation.Keep
import com.rdstory.miuiperfsaver.ConfigProvider
import com.rdstory.miuiperfsaver.Constants.LOG_TAG
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

@Keep
class HookMain : IXposedHookLoadPackage {

    companion object {
        const val MIUI_POWER_KEEPER = "com.miui.powerkeeper"
        const val CLASS_DISPLAYFRAMESETTING = "com.miui.powerkeeper.statemachine.DisplayFrameSetting"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (MIUI_POWER_KEEPER != lpparam.packageName) return
        try {
            hook(lpparam)
            XposedBridge.log("[${LOG_TAG}] process hooked: ${lpparam.processName}")
        } catch (e: Exception) {
            XposedBridge.log("[${LOG_TAG}] failed to hook process: ${lpparam.processName}. ${e.message}")
        }
    }

    private fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classDisplayFrameSetting =
            XposedHelpers.findClass(CLASS_DISPLAYFRAMESETTING, lpparam.classLoader)
        // primary hook: onForegroundChanged
        // exclude selected package name
        XposedHelpers.findAndHookMethod(
            classDisplayFrameSetting,
            "onForegroundChanged",
            XposedHelpers.findClass("miui.process.ForegroundInfo", lpparam.classLoader),
            object : XC_MethodHook() {
                private var cleanup: (() -> Unit)? = null
                override fun beforeHookedMethod(param: MethodHookParam) {
                    FPSSaver.ensureInit(param.thisObject)
                    cleanup = FPSSaver.excludeIfMatch(param.args[0])
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    cleanup?.invoke()
                }
            }
        )
        // secondary hook: setScreenEffect
        // replace fps parameter
        XposedHelpers.findAndHookMethod(
            classDisplayFrameSetting,
            "setScreenEffect",
            String::class.java,
            Int::class.java,
            Int::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val pkg = param.args[0] as String
                    val fps = param.args[1] as Int
                    val cookie = param.args[2] as Int
                    FPSSaver.ensureInit(param.thisObject)
                    FPSSaver.getTargetFPS(pkg, fps, cookie)?.let {
                        param.args[1] = it
                        param.args[2] = FPSSaver.excludeCookie // avoid getting ignored
                    }
                }
            }
        )
    }

    private object FPSSaver {
        private var initialized = false
        private val savedApps = mutableSetOf<String>()
        private var maxFPS: Int? = null
        var excludeCookie = 247
            private set
        private var hookedExcludeAppSet: ArraySet<String>? = null

        fun ensureInit(thisObject: Any) {
            if (initialized) {
                return
            }
            initialized = true
            maxFPS = thisObject.callMethod("getMaxFPS") ?: return
            val context: Context = thisObject.getObjectField("mContext") ?: return
            hookedExcludeAppSet = thisObject.getObjectField("mExcludeApps")
            thisObject.getObjectField<Int>("COOKIE_EXCLUDE")?.let { excludeCookie = it }
            ConfigProvider.observeSavedAppList(context) {
                ConfigProvider.getSavedAppList(context)?.let {
                    XposedBridge.log("[${LOG_TAG}] saved app list updated: ${savedApps.size} -> ${it.size}")
                    savedApps.clear()
                    savedApps.addAll(it)
                }
            }
            ConfigProvider.getSavedAppList(context)?.let { savedApps.addAll(it) }
            XposedBridge.log("[${LOG_TAG}] initialized. maxFPS: $maxFPS, apps: ${savedApps.size}, excludes: ${hookedExcludeAppSet?.size}")
        }

        fun getTargetFPS(pkg: String, fps: Int, cookie: Int): Int? {
            val maxFPS = maxFPS?.takeIf {
                (fps < it || cookie != excludeCookie) && savedApps.contains(pkg)
            } ?: return null
            var changed = ""
            changed += if (fps != maxFPS) "[fps: $fps -> $maxFPS]" else "[fps: $fps]"
            changed += if (cookie != excludeCookie) "[cookie: $cookie -> $excludeCookie]" else "[cookie: $cookie]"
            XposedBridge.log("[${LOG_TAG}] $changed perf saved: $pkg")
            return maxFPS
        }

        fun excludeIfMatch(foregroundInfo: Any?): (() -> Unit)? {
            val hookedAppSet = hookedExcludeAppSet ?: return null
            foregroundInfo?.getObjectField<String>("mForegroundPackageName")
                ?.takeIf { pkg -> savedApps.contains(pkg) && hookedAppSet.add(pkg) }
                ?.let { pkg ->
                    XposedBridge.log("[${LOG_TAG}] [excluded] perf saved: $pkg")
                    return { hookedAppSet.remove(pkg) }
                }
            return null
        }
    }
}

private fun <T> Any.getObjectField(filedName: String): T? {
    return try {
        @Suppress("UNCHECKED_CAST")
        XposedHelpers.getObjectField(this, filedName) as? T
    } catch (e: Exception) {
        XposedBridge.log("[${LOG_TAG}] failed to get object: ${this.javaClass.name}.$filedName. ${e.message}")
        null
    }
}

private fun <T> Any.callMethod(methodName: String, vararg args: Any): T? {
    return try {
        @Suppress("UNCHECKED_CAST")
        XposedHelpers.callMethod(this, methodName, *args) as? T
    } catch (e: Exception) {
        XposedBridge.log("[${LOG_TAG}] failed to call method: ${this.javaClass.name}.$methodName. ${e.message}")
        null
    }
}
