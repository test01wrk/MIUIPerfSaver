package com.rdstory.miuiperfsaver.xposed

import com.rdstory.miuiperfsaver.Constants
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

fun <T> Any.getObjectField(filedName: String): T? {
    return try {
        @Suppress("UNCHECKED_CAST")
        XposedHelpers.getObjectField(this, filedName) as? T
    } catch (e: Exception) {
        XposedBridge.log("[${Constants.LOG_TAG}] failed to get object: ${this.javaClass.name}.$filedName. ${e.message}")
        null
    }
}

fun <T> Any.callMethod(methodName: String, vararg args: Any?): T? {
    return try {
        @Suppress("UNCHECKED_CAST")
        XposedHelpers.callMethod(this, methodName, *args) as? T
    } catch (e: Exception) {
        XposedBridge.log("[${Constants.LOG_TAG}] failed to call method: ${this.javaClass.name}.$methodName. ${e.message}")
        null
    }
}