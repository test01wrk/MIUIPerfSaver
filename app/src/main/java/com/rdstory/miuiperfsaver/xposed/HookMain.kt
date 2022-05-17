package com.rdstory.miuiperfsaver.xposed

import androidx.annotation.Keep
import com.rdstory.miuiperfsaver.Constants.LOG_TAG
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

@Keep
class HookMain : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            JoyoseHook.initHook(lpparam)
            PowerKeeperHook.initHook(lpparam)
        } catch (e: Throwable) {
            XposedBridge.log("[${LOG_TAG}] failed to hook process: ${lpparam.processName}. ${e.message}")
        }
    }
}

