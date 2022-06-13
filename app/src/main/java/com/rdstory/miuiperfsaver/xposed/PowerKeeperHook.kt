package com.rdstory.miuiperfsaver.xposed

import android.content.Context
import android.os.Looper
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
        val classFGInfo =
            XposedHelpers.findClass("miui.process.ForegroundInfo", lpparam.classLoader)

        val methods = classDisplayFrameSetting.declaredMethods.filter { it.name.contains("setScreenEffect") }
        Log.i(LOG_TAG, "powerkeeper methods: ${methods}")

        XposedHelpers.findAndHookConstructor(
            classDisplayFrameSetting,
            Context::class.java, Looper::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val context = param.args[0] as Context
                    Log.i(LOG_TAG, "powerkeeper DisplayFrameSetting created: ${lpparam.processName}")
                    DCFPSCompat.init(context, object : DCFPSCompat.Callback {
                        override fun setFps(fps: Int) {
                            try {
                                param.thisObject.callMethod<Unit>("setScreenEffectInternal", fps, 244, "")
                            } catch (e: Throwable) {
                                try {
                                    param.thisObject.callMethod<Unit>("setScreenEffectInternal", fps, 244)
                                } catch (t: Throwable) {
                                    param.thisObject.callMethod<Unit>("setScreenEffect", fps, 244)
                                }
                            }
                        }
                        override fun setFpsLimit(fpsLimit: Int) {
                            FPSSaver.tempFpsLimit = fpsLimit
                        }
                        override fun updateCurrentFps() {
                            val mCurrentFgInfo = param.thisObject.getObjectField<Any>("mCurrentFgInfo")
                            param.thisObject.callMethod<Unit>("onForegroundChanged", mCurrentFgInfo)
                        }
                    })
                }
            })
        // primary hook: onForegroundChanged
        // exclude selected package name
        XposedHelpers.findAndHookMethod(
            classDisplayFrameSetting,
            "onForegroundChanged",
            classFGInfo,
            object : XC_MethodHook() {
                private var cleanup: (() -> Unit)? = null
                override fun beforeHookedMethod(param: MethodHookParam) {
                    Log.i(LOG_TAG, "onForegroundChanged: ${param.args[0]}")
                    FPSSaver.ensureInit(param.thisObject)
                    cleanup = FPSSaver.excludeIfMatch(param.args[0])
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    cleanup?.invoke()
                }
            }
        )
        XposedHelpers.findAndHookMethod(
            classDisplayFrameSetting,
            "notifyFGChange",
            XposedHelpers.findClass("miui.process.ForegroundInfo", lpparam.classLoader),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    Log.i(LOG_TAG, "notifyFGChange: ${param.args[0]}")
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
                private val fpsAndCookie = IntArray(2)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val pkg = param.args[0] as String
                    val fps = param.args[1] as Int
                    val cookie = param.args[2] as Int
                    Log.i(LOG_TAG, "setScreenEffect: pkg=$pkg, fps=$fps, cookie=$cookie, overrideFps=${FPSSaver.tempFpsLimit}")
                    FPSSaver.ensureInit(param.thisObject)
                    fpsAndCookie[0] = fps
                    fpsAndCookie[1] = cookie
                    FPSSaver.getTargetFPS(pkg, fpsAndCookie)
                    param.args[1] = fpsAndCookie[0]
                    param.args[2] = fpsAndCookie[1]
                }
            }
        )
        XposedBridge.log("[${LOG_TAG}] process hooked: ${lpparam.processName}")
    }

    private object FPSSaver {

        private var initialized = false
        private val savedApps = mutableMapOf<String, Int>()
        private var supportFps: IntArray? = null
        private var excludeCookie = 247
        private var hookedExcludeAppSet: ArraySet<String>? = null
        var tempFpsLimit = Int.MAX_VALUE

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
                    thisObject.getObjectField<Any>("mCurrentFgInfo")?.let { fg ->
                        thisObject.callMethod<Unit>("onForegroundChanged", fg)
                    }
                }
            }
            ConfigProvider.getSavedAppConfig(context)?.let { savedApps.putAll(it) }
            XposedBridge.log("[$LOG_TAG] initialized. supportFps: ${supportFps?.toList()}, " +
                    "global: ${savedApps[Constants.FAKE_PKG_DEFAULT_FPS]}, " +
                    "apps: ${savedApps.size}, excludes: ${hookedExcludeAppSet?.size}")
        }

        fun getTargetFPS(pkg: String, outFpsAndCookie: IntArray) {
            val fps = outFpsAndCookie[0]
            val cookie = outFpsAndCookie[1]
            outFpsAndCookie[0] = fps.coerceAtMost(tempFpsLimit)
            val pkgFps = (savedApps[pkg] ?: savedApps[Constants.FAKE_PKG_DEFAULT_FPS])?.takeIf {
                (fps != it || cookie != excludeCookie) && supportFps?.contains(it) == true
            } ?: return
            if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                var changed = ""
                changed += if (fps != pkgFps) "[fps: $fps -> $pkgFps]" else "[fps: $fps]"
                changed += if (cookie != excludeCookie) "[cookie: $cookie -> $excludeCookie]" else "[cookie: $cookie]"
                XposedBridge.log("[$LOG_TAG] $changed perf saved: $pkg")
            }
            outFpsAndCookie[0] = pkgFps.coerceAtMost(tempFpsLimit)
            outFpsAndCookie[1] = excludeCookie // avoid getting ignored
        }

        fun excludeIfMatch(foregroundInfo: Any?): (() -> Unit)? {
            val hookedAppSet = hookedExcludeAppSet ?: return null
            foregroundInfo?.getObjectField<String>("mForegroundPackageName")
                ?.takeIf { pkg ->
                    (savedApps.contains(pkg) || savedApps.contains(Constants.FAKE_PKG_DEFAULT_FPS))
                            && hookedAppSet.add(pkg)
                }?.let { pkg ->
                    if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                        XposedBridge.log("[$LOG_TAG] [excluded] perf saved: $pkg")
                    }
                    return { hookedAppSet.remove(pkg) }
                }
            return null
        }
    }
}