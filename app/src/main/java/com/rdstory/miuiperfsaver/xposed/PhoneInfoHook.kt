package com.rdstory.miuiperfsaver.xposed

import android.os.Build
import com.rdstory.miuiperfsaver.Constants.LOG_TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object PhoneInfoHook {
    private val gamePackages = arrayOf(
        "com.tencent.tmgp.sgame",
        "com.tencent.tmgp.pubgmhd"
    )

    fun initHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!gamePackages.contains(lpparam.packageName)) return
        val build = BuildImpl(lpparam)
        // emulate "Xiaomi 11 Ultra"
        build.MODEL("M2102K1C")
        build.BRAND("Xiaomi")
        build.PRODUCT("Star")
        build.DEVICE("Star")
        build.MANUFACTURER("Xiaomi")
        XposedBridge.log("[${LOG_TAG}] process hooked: ${lpparam.processName}")
    }

    /**
     * BuildImpl class from https://github.com/kingsollyu/AppEnv
     */
    private class BuildImpl(lpparam: XC_LoadPackage.LoadPackageParam) {
        private val hashMap = HashMap<String, String>()

        init {
            XposedBridge.hookAllMethods(
                XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader),
                "get",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args?.getOrNull(0)?.toString() ?: return
                        hashMap[key]?.let { param.result = it }
                    }
                })
        }

        fun MANUFACTURER(value: String) {
            XposedHelpers.setStaticObjectField(Build::class.java, "MANUFACTURER", value)
            hashMap["ro.product.manufacturer"] = value
        }

        fun BRAND(value: String) {
            XposedHelpers.setStaticObjectField(Build::class.java, "BRAND", value)
            hashMap["ro.product.brand"] = value
        }

        fun BOOTLOADER(value: String) {
            XposedHelpers.setStaticObjectField(Build::class.java, "BOOTLOADER", value)
            hashMap["ro.bootloader"] = value
        }

        fun MODEL(value: String) {
            XposedHelpers.setStaticObjectField(Build::class.java, "MODEL", value)
            hashMap["ro.product.model"] = value
        }

        fun DEVICE(value: String) {
            XposedHelpers.setStaticObjectField(Build::class.java, "DEVICE", value)
            hashMap["ro.product.device"] = value
        }

        fun DISPLAY(value: String) {
            XposedHelpers.setStaticObjectField(Build::class.java, "DISPLAY", value)
            hashMap["ro.build.display.id"] = value
        }

        fun PRODUCT(value: String) {
            XposedHelpers.setStaticObjectField(Build::class.java, "PRODUCT", value)
            hashMap["ro.product.name"] = value
        }

        fun BOARD(value: String) {
            XposedHelpers.setStaticObjectField(Build::class.java, "BOARD", value)
            hashMap["ro.product.board"] = value
        }

        fun HARDWARE(value: String) {
            XposedHelpers.setStaticObjectField(Build::class.java, "HARDWARE", value)
            hashMap["ro.hardware"] = value
        }

        fun SERIAL(value: String) {
            XposedHelpers.setStaticObjectField(Build::class.java, "SERIAL", value)
            hashMap["ro.serialno"] = value
        }

        fun TYPE(value: String) {
            XposedHelpers.setStaticObjectField(Build::class.java, "TYPE", value)
            hashMap["ro.build.type"] = value
        }

        fun TAGS(value: String) {
            XposedHelpers.setStaticObjectField(Build::class.java, "TAGS", value)
            hashMap["ro.build.tags"] = value
        }

        fun FINGERPRINT(value: String) {
            XposedHelpers.setStaticObjectField(Build::class.java, "FINGERPRINT", value)
            hashMap["ro.build.fingerprint"] = value
        }

        fun USER(value: String) {
            XposedHelpers.setStaticObjectField(Build::class.java, "USER", value)
            hashMap["ro.build.user"] = value
        }

        fun HOST(value: String) {
            XposedHelpers.setStaticObjectField(Build::class.java, "HOST", value)
            hashMap["ro.build.host"] = value
        }
    }
}
