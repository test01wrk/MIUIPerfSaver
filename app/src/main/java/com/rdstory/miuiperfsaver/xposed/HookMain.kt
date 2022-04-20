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
        // primary hook: onForegroundChanged
        // exclude selected package name
        XposedHelpers.findAndHookMethod(
            CLASS_DISPLAYFRAMESETTING,
            lpparam.classLoader,
            "onForegroundChanged",
            XposedHelpers.findClass("miui.process.ForegroundInfo", lpparam.classLoader),
            object : XC_MethodHook() {
                private var cleanup: (() -> Unit)? = null
                override fun beforeHookedMethod(param: MethodHookParam) {
                    FPSSaver.ensureInit(param.thisObject)
                    cleanup = FPSSaver.setExcludeMyApps(param.args[0])
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    cleanup?.invoke()
                }
            }
        )
        // secondary hook: setScreenEffect
        // replace fps parameter
        XposedHelpers.findAndHookMethod(
            CLASS_DISPLAYFRAMESETTING,
            lpparam.classLoader,
            "setScreenEffect",
            String::class.java,
            Int::class.java,
            Int::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val pkg = param.args[0] as String
                    val fps = param.args[1] as Int
                    FPSSaver.ensureInit(param.thisObject)
                    FPSSaver.getTargetFPS(pkg, fps).takeIf { it > fps }?.let { param.args[1] = it }
                }
            }
        )
    }

    private object FPSSaver {
        private var initialized = false
        private val savedApps = mutableSetOf<String>()
        private var maxFPS: Int? = null
        private var hookedExcludeAppSet: ArraySet<String>? = null
        private var hookedInstance: Any? = null

        fun ensureInit(thisObject: Any) {
            if (initialized) {
                return
            }
            initialized = true
            hookedInstance = thisObject
            maxFPS = try {
                XposedHelpers.callMethod(thisObject, "getMaxFPS") as? Int
            } catch (e: Exception) {
                XposedBridge.log("[${LOG_TAG}] failed to get max fps. ${e.message}")
                null
            } ?: return
            val context = try {
                XposedHelpers.getObjectField(thisObject, "mContext") as? Context
            } catch (e: Exception) {
                XposedBridge.log("[${LOG_TAG}] failed to get context. ${e.message}")
                null
            } ?: return
            try {
                @Suppress("UNCHECKED_CAST")
                hookedExcludeAppSet = XposedHelpers.getObjectField(thisObject, "mExcludeApps") as? ArraySet<String>
            } catch (e: Exception) {
                XposedBridge.log("[${LOG_TAG}] failed to get excluded app list. ${e.message}")
            }
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

        fun getTargetFPS(pkg: String, fps: Int): Int {
            val maxFPS = maxFPS?.takeIf { fps < it && savedApps.contains(pkg) } ?: return fps
            XposedBridge.log("[${LOG_TAG}] [fps: $fps -> $maxFPS] perf saved: $pkg")
            return maxFPS
        }

        fun setExcludeMyApps(foregroundInfo: Any?): (() -> Unit)? {
            foregroundInfo ?: return null
            val hookedAppSet = hookedExcludeAppSet ?: return null
            try {
                XposedHelpers.getObjectField(foregroundInfo, "mForegroundPackageName") as? String
            } catch (e: Exception) {
                null
            }?.takeIf { pkg -> savedApps.contains(pkg) && hookedAppSet.add(pkg) }?.let { pkg ->
                XposedBridge.log("[${LOG_TAG}] [excluded] perf saved: $pkg")
                return { hookedAppSet.remove(pkg) }
            }
            return null
        }
    }
}
