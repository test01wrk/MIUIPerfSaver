package com.rdstory.miuiperfsaver.xposed

import com.rdstory.miuiperfsaver.Constants
import de.robv.android.xposed.XC_MethodHook
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

fun <T> Class<*>.callStaticMethod(methodName: String, vararg args: Any?): T? {
    return try {
        @Suppress("UNCHECKED_CAST")
        XposedHelpers.callStaticMethod(this, methodName, *args) as? T
    } catch (e: Exception) {
        XposedBridge.log("[${Constants.LOG_TAG}] failed to call static method: ${this.javaClass.name}.$methodName. ${e.message}")
        null
    }
}

fun safeFindAndHookMethod(clazz: Class<*>, methodName: String , vararg parameterTypesAndCallback: Any): XC_MethodHook.Unhook? {
    return try {
        XposedHelpers.findAndHookMethod(clazz, methodName, *parameterTypesAndCallback)
    } catch (e: Exception) {
        XposedBridge.log("[${Constants.LOG_TAG}] findAndHookMethod failed: ${e.message}")
        null
    }
}

fun safeFindAndHookMethod(className: String, classLoader: ClassLoader, methodName: String , vararg parameterTypesAndCallback: Any): XC_MethodHook.Unhook? {
    return try {
        XposedHelpers.findAndHookMethod(className, classLoader, methodName, *parameterTypesAndCallback)
    } catch (e: Exception) {
        XposedBridge.log("[${Constants.LOG_TAG}] findAndHookMethod failed: ${e.message}")
        null
    }
}

fun String.findClass(classLoader: ClassLoader): Class<*> {
    return XposedHelpers.findClass(this, classLoader)
}