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
            XposedBridge.log("[${LOG_TAG}] hooked $MIUI_POWER_KEEPER")
        } catch (e: Exception) {
            XposedBridge.log("[${LOG_TAG}] failed to hook $MIUI_POWER_KEEPER. ${e.message}")
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
                var initialized = false
                val savedApps = mutableSetOf<String>()
                var maxFPS: Int? = null

                private fun init(context: Context?, thisObj: Any) {
                    context ?: return
                    try {
                        maxFPS = XposedHelpers.callMethod(thisObj, "getMaxFPS") as? Int
                    } catch (e: Exception) {
                        XposedBridge.log("[${LOG_TAG}] failed to get max fps. ${e.message}")
                    }
                    ConfigProvider.observeSavedAppList(context) {
                        ConfigProvider.getSavedAppList(context)?.let {
                            XposedBridge.log("[${LOG_TAG}] receive saved app list update: ${savedApps.size} -> ${it.size}")
                            savedApps.clear()
                            savedApps.addAll(it)
                        }
                    }
                    ConfigProvider.getSavedAppList(context)?.let { savedApps.addAll(it) }
                    XposedBridge.log("[${LOG_TAG}] initialized. maxFPS: $maxFPS, apps: ${savedApps.size}")
                }

                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!initialized) {
                        initialized = true
                        val context = try {
                            XposedHelpers.getObjectField(param.thisObject, "mContext") as? Context
                        } catch (e: Exception) {
                            XposedBridge.log("[${LOG_TAG}] failed to get context. ${e.message}")
                            null
                        }
                        init(context, param.thisObject)
                    }
                    val maxFPS = maxFPS ?: return
                    val pkg = param.args?.getOrNull(0) as? String ?: return
                    val fps = param.args?.getOrNull(1) as? Int ?: return
                    if (fps < maxFPS && savedApps.contains(pkg)) {
                        param.args[1] = maxFPS
                        XposedBridge.log("[${LOG_TAG}] [fps: $fps -> $maxFPS] perf saved: $pkg")
                    }
                }
            }
        )
    }
}
