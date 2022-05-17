package com.rdstory.miuiperfsaver.xposed

import android.content.Context
import android.util.ArraySet
import android.util.Log
import com.rdstory.miuiperfsaver.ConfigProvider
import com.rdstory.miuiperfsaver.Constants
import com.rdstory.miuiperfsaver.Constants.LOG_TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object PowerKeeperHook {
    private const val MIUI_POWER_KEEPER = "com.miui.powerkeeper"
    private const val CLASS_DISPLAYFRAMESETTING = "com.miui.powerkeeper.statemachine.DisplayFrameSetting"

    fun initHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (MIUI_POWER_KEEPER != lpparam.packageName) return
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
        XposedBridge.log("[${LOG_TAG}] process hooked: ${lpparam.processName}")
    }

    private object FPSSaver {
        private var initialized = false
        private val savedApps = mutableMapOf<String, Int>()
        private var supportFps: IntArray? = null
        var excludeCookie = 247
            private set
        private var hookedExcludeAppSet: ArraySet<String>? = null

        fun ensureInit(thisObject: Any) {
            if (initialized) {
                return
            }
            initialized = true
            supportFps = thisObject.callMethod("getSupportFps") ?: return
            val context: Context = thisObject.getObjectField("mContext") ?: return
            hookedExcludeAppSet = thisObject.getObjectField("mExcludeApps")
            thisObject.getObjectField<Int>("COOKIE_EXCLUDE")?.let { excludeCookie = it }
            ConfigProvider.observeSavedAppChange(context) {
                ConfigProvider.getSavedAppConfig(context)?.let {
                    XposedBridge.log("[$LOG_TAG] config updated. " +
                            "global: ${it[Constants.FAKE_PKG_DEFAULT_FPS]}, " +
                            "apps: ${savedApps.size} -> ${it.size}")
                    savedApps.clear()
                    savedApps.putAll(it)
                }
            }
            ConfigProvider.getSavedAppConfig(context)?.let { savedApps.putAll(it) }
            XposedBridge.log("[$LOG_TAG] initialized. supportFps: ${supportFps?.toList()}, " +
                    "global: ${savedApps[Constants.FAKE_PKG_DEFAULT_FPS]}, " +
                    "apps: ${savedApps.size}, excludes: ${hookedExcludeAppSet?.size}")
        }

        fun getTargetFPS(pkg: String, fps: Int, cookie: Int): Int? {
            val pkgFps = (savedApps[pkg] ?: savedApps[Constants.FAKE_PKG_DEFAULT_FPS])?.takeIf {
                (fps != it || cookie != excludeCookie) && supportFps?.contains(it) == true
            } ?: return null
            if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                var changed = ""
                changed += if (fps != pkgFps) "[fps: $fps -> $pkgFps]" else "[fps: $fps]"
                changed += if (cookie != excludeCookie) "[cookie: $cookie -> $excludeCookie]" else "[cookie: $cookie]"
                XposedBridge.log("[$LOG_TAG] $changed perf saved: $pkg")
            }
            return pkgFps
        }

        fun excludeIfMatch(foregroundInfo: Any?): (() -> Unit)? {
            val hookedAppSet = hookedExcludeAppSet ?: return null
            foregroundInfo?.getObjectField<String>("mForegroundPackageName")
                ?.takeIf { pkg ->
                    (savedApps.contains(pkg) || savedApps.contains(Constants.FAKE_PKG_DEFAULT_FPS))
                            && hookedAppSet.add(pkg)
                }?.let { pkg ->
                    if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                        XposedBridge.log("[$LOG_TAG] [excluded] perf saved: $pkg")
                    }
                    return { hookedAppSet.remove(pkg) }
                }
            return null
        }
    }
}