package com.rdstory.miuiperfsaver.xposed

import androidx.annotation.Keep
import com.rdstory.miuiperfsaver.BuildConfig.APPLICATION_ID
import com.rdstory.miuiperfsaver.MainApplication
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

@Keep
class HookSelf : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (APPLICATION_ID != lpparam.packageName) return
        XposedHelpers.findAndHookMethod(
            MainApplication.Companion::class.java.name,
            lpparam.classLoader,
            "getActiveXposedVersion",
            XC_MethodReplacement.returnConstant(XposedBridge.getXposedVersion())
        )
    }
}