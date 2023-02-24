package com.rdstory.miuiperfsaver.xposed

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.ArraySet
import android.util.Log
import com.rdstory.miuiperfsaver.ConfigProvider
import com.rdstory.miuiperfsaver.ConfigProvider.Companion.APP_LIST_URI
import com.rdstory.miuiperfsaver.ConfigProvider.Companion.COLUMN_PKG_FPS
import com.rdstory.miuiperfsaver.ConfigProvider.Companion.COLUMN_PKG_IGNORE_DC_LIMIT
import com.rdstory.miuiperfsaver.Constants
import com.rdstory.miuiperfsaver.Constants.FPS_COOKIE_EXCLUDE
import com.rdstory.miuiperfsaver.Constants.LOG_LEVEL
import com.rdstory.miuiperfsaver.Constants.LOG_TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject

object PowerKeeperHook {
    private const val MIUI_POWER_KEEPER = "com.miui.powerkeeper"
    private const val CLASS_DISPLAYFRAMESETTING = "com.miui.powerkeeper.statemachine.DisplayFrameSetting"

    fun initHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (MIUI_POWER_KEEPER != lpparam.packageName) return
        val classDisplayFrameSetting =
            XposedHelpers.findClass(CLASS_DISPLAYFRAMESETTING, lpparam.classLoader)
        XposedHelpers.findAndHookConstructor(
            classDisplayFrameSetting,
            Context::class.java, Looper::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val context = param.args[0] as Context
                    DCFPSCompat.init(context, param.thisObject, object : DCFPSCompat.Callback {
                        override fun setFpsLimit(fpsLimit: Int?) {
                            FPSSaver.globalFpsLimit = fpsLimit
                        }
                    })
                }
            })
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
                    FPSSaver.ensureInit(param.thisObject)
                    FPSSaver.getTargetFPS(param.args)
                    DCFPSCompat.beforeApplyFps(param.args[1] as Int)
                }
            }
        )
        XposedBridge.log("[${LOG_TAG}] process hooked: ${lpparam.processName}")
    }

    private object FPSSaver {
        private var initialized = false
        private val savedApps = mutableMapOf<String, JSONObject>()
        private var supportFps: Set<Int>? = null
        private var hookedExcludeAppSet: ArraySet<String>? = null
        var globalFpsLimit: Int? = null

        fun ensureInit(thisObject: Any) {
            if (initialized) {
                return
            }
            initialized = true
            supportFps = thisObject.callMethod<IntArray?>("getSupportFps")?.let {
                it.sortDescending()
                it.toSet()
            } ?: return
            val context: Context = thisObject.getObjectField("mContext") ?: return
            hookedExcludeAppSet = thisObject.getObjectField("mExcludeApps")
            val handler = Handler(Looper.getMainLooper())
            val updateConfig = fun() {
                val pkgFpsMap = ConfigProvider.getSavedAppConfig(context) ?: emptyMap()
                XposedBridge.log("[$LOG_TAG] config updated. " +
                        "supportFps: $supportFps, " +
                        "global: ${pkgFpsMap[Constants.FAKE_PKG_DEFAULT_FPS]?.optInt(COLUMN_PKG_FPS)}, " +
                        "fpsMap: ${pkgFpsMap.size}")
                savedApps.clear()
                savedApps.putAll(pkgFpsMap)
                handler.post {
                    thisObject.getObjectField<Any>("mCurrentFgInfo")?.let { fg ->
                        thisObject.callMethod<Unit>("onForegroundChanged", fg)
                    }
                }
            }
            ConfigProvider.observeChange(context, APP_LIST_URI, updateConfig)
            updateConfig()
        }

        fun getTargetFPS(outPkgFpsCookie: Array<Any>) {
            val pkg = outPkgFpsCookie[0] as String
            val sysFps = outPkgFpsCookie[1] as Int
            val cookie = outPkgFpsCookie[2] as Int
            val appConfig = savedApps[pkg] ?: savedApps[Constants.FAKE_PKG_DEFAULT_FPS]
            val ignoreGlobalFpsLimit = appConfig?.optBoolean(COLUMN_PKG_IGNORE_DC_LIMIT, false)
            val fpsLimit = globalFpsLimit?.takeIf { ignoreGlobalFpsLimit != true }
                ?: supportFps?.firstOrNull() ?: Int.MAX_VALUE
            val setFps = appConfig?.optInt(COLUMN_PKG_FPS, -1)?.takeIf { it >= 0 }
            val pkgFps = setFps?.takeIf {
                    (sysFps != it || cookie != FPS_COOKIE_EXCLUDE) && supportFps?.contains(it) == true
                } ?: (setFps ?: sysFps).takeIf { it > fpsLimit } ?: return
            outPkgFpsCookie[1] = pkgFps.coerceAtMost(fpsLimit)
            outPkgFpsCookie[2] = FPS_COOKIE_EXCLUDE
            if (Log.isLoggable(LOG_TAG, LOG_LEVEL)) {
                XposedBridge.log("[$LOG_TAG] sysFps: $sysFps, setFps: $setFps, " +
                        "limit: $fpsLimit, out: ${outPkgFpsCookie.toList()}")
            }
        }

        fun excludeIfMatch(foregroundInfo: Any?): (() -> Unit)? {
            val hookedAppSet = hookedExcludeAppSet ?: return null
            foregroundInfo?.getObjectField<String>("mForegroundPackageName")
                ?.takeIf { pkg ->
                    (savedApps.contains(pkg) || savedApps.contains(Constants.FAKE_PKG_DEFAULT_FPS))
                            && hookedAppSet.add(pkg)
                }?.let { pkg ->
                    if (Log.isLoggable(LOG_TAG, LOG_LEVEL)) {
                        XposedBridge.log("[$LOG_TAG] force exclude pkg: $pkg")
                    }
                    return { hookedAppSet.remove(pkg) }
                }
            return null
        }
    }
}