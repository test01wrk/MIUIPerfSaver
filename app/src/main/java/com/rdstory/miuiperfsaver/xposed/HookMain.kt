package com.rdstory.miuiperfsaver.xposed

import android.content.Context
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

        fun ensureInit(thisObject: Any) {
            if (initialized) {
                return
            }
            initialized = true
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
            ConfigProvider.observeSavedAppList(context) {
                ConfigProvider.getSavedAppList(context)?.let {
                    XposedBridge.log("[${LOG_TAG}] saved app list updated: ${savedApps.size} -> ${it.size}")
                    savedApps.clear()
                    savedApps.addAll(it)
                }
            }
            ConfigProvider.getSavedAppList(context)?.let { savedApps.addAll(it) }
            XposedBridge.log("[${LOG_TAG}] initialized. maxFPS: $maxFPS, apps: ${savedApps.size}")
        }

        fun getTargetFPS(pkg: String, fps: Int): Int {
            val maxFPS = maxFPS?.takeIf { fps < it && savedApps.contains(pkg) } ?: return fps
            XposedBridge.log("[${LOG_TAG}] [fps: $fps -> $maxFPS] perf saved: $pkg")
            return maxFPS
        }
    }
}
