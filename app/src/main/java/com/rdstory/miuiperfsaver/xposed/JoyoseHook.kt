package com.rdstory.miuiperfsaver.xposed

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import com.rdstory.miuiperfsaver.ConfigProvider
import com.rdstory.miuiperfsaver.ConfigProvider.Companion.JOYOSE_CONFIG_URI
import com.rdstory.miuiperfsaver.Constants.JOYOSE_PKG
import com.rdstory.miuiperfsaver.Constants.LOG_TAG
import com.rdstory.miuiperfsaver.JoyoseProfileRule
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

object JoyoseHook {
    fun initHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != JOYOSE_PKG) return

        val joyoseProfileRuleHolder = arrayOf(JoyoseProfileRule.BLOCK_ALL)
        XposedHelpers.findAndHookMethod(Application::class.java, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = param.thisObject as Context
                    val updateJoyoseRule = fun() {
                        ConfigProvider.getJoyoseConfig(context)?.let {
                            val old = joyoseProfileRuleHolder[0]
                            joyoseProfileRuleHolder[0] = it
                            XposedBridge.log("[${LOG_TAG}] joyose profile rule updated: ${old.value} -> ${it.value}")
                        }
                    }
                    ConfigProvider.observeChange(context, JOYOSE_CONFIG_URI, updateJoyoseRule)
                    updateJoyoseRule()
                }
            })
        var unHook: XC_MethodHook.Unhook? = null
        unHook = XposedHelpers.findAndHookConstructor(HttpURLConnection::class.java, URL::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    hookHttpImpl(param.thisObject::class.java, joyoseProfileRuleHolder)
                    unHook?.unhook() // hook once
                }
            })
        val jsonReaderClass = "com.google.gson.stream.JsonReader".findClass(lpparam.classLoader)
        safeFindAndHookMethod(
            "com.google.gson.JsonParser".findClass(lpparam.classLoader),
            "parse",
            jsonReaderClass,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = parseCloudConfig(param.result) ?: return
                    val hackProfile = replaceCloudConfig(param.result, hackProfile(config, joyoseProfileRuleHolder[0]))
                    val reader = XposedHelpers.findConstructorExact(jsonReaderClass, Reader::class.java)
                        .newInstance(StringReader(hackProfile.toString()))
                    param.result = XposedBridge.invokeOriginalMethod(param.method, param.thisObject, arrayOf(reader))
                }
            })
        safeFindAndHookMethod(
            "android.provider.MiuiSettings\$SettingsCloudData".findClass(lpparam.classLoader),
            "getCloudDataList",
            ContentResolver::class.java, String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            }
        )
        XposedBridge.log("[${LOG_TAG}] process hooked: ${lpparam.processName}")
    }

    /**
     * parse cloud configs from Gson's JsonObject to JSONObject
     */
    private fun parseCloudConfig(gsonJsonObject: Any?): JSONObject? {
        gsonJsonObject ?: return null
        val resultJson = JSONObject(gsonJsonObject.toString())
        val commonConfig = resultJson.optJSONObject("common_config")
            ?.takeIf { it.optString("config_name") == "common_config" }
            ?: resultJson.optJSONObject("data")?.optJSONObject("common_config")
                ?.takeIf { it.optString("config_name") == "common_config" }
        val boosterConfig = resultJson.optJSONObject("booster_config")
            ?.takeIf { it.optString("config_name") == "booster_config" }
        if (commonConfig == null && boosterConfig == null) {
            return null
        }
        return JSONObject().apply {
            commonConfig?.let { put("common_config", it) }
            boosterConfig?.let { put("booster_config", it) }
        }
    }

    /**
     * replace cloud configs witch hacked values
     */
    private fun replaceCloudConfig(gsonJsonObject: Any, hackProfile: JSONObject): JSONObject {
        val resultJson = JSONObject(gsonJsonObject.toString())
        val commonConfig = hackProfile.optJSONObject("common_config") ?: JSONObject()
        val boosterConfig = hackProfile.optJSONObject("booster_config") ?: JSONObject()
        if (resultJson.optJSONObject("common_config") != null) {
            resultJson.put("common_config", commonConfig)
        } else if (resultJson.optJSONObject("data")?.optJSONObject("common_config") != null) {
            resultJson.getJSONObject("data").put("common_config", commonConfig)
        }
        if (resultJson.optJSONObject("booster_config") != null) {
            resultJson.put("booster_config", boosterConfig)
        }
        return resultJson
    }

    /**
     * Hook HttpURLConnection used to block tracking and ad request
     */
    private fun hookHttpImpl(httpImplClass: Class<*>, ruleHolder: Array<JoyoseProfileRule>) {
        XposedHelpers.findAndHookMethod(httpImplClass, "connect",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val con = param.thisObject as HttpURLConnection
                    val url = con.url?.toString() ?: return
                    if (url.contains("tracking.miui.com/") || url.contains("ad.xiaomi.com/")) {
                        param.throwable = IOException() // stop tracking and ad requests
                    }
                }
            })
        XposedBridge.log("[${LOG_TAG}] HttpURLConnection hooked, impl: ${httpImplClass.name}")
    }

    /**
     * Hack joyose profile here.
     * Not sure what it does exactly, but "dynamic_fps" is known to be used to limit game FPS
     */
    private fun hackProfile(profile: JSONObject, profileRule: JoyoseProfileRule): JSONObject {
        XposedBridge.log("[$LOG_TAG] apply cloud profile rule: ${profileRule.value}")
        if (profileRule == JoyoseProfileRule.KEEP_ALL) {
            XposedBridge.log("[$LOG_TAG] cloud profile kept")
            return profile
        }
        if (profileRule == JoyoseProfileRule.BLOCK_ALL) {
            XposedBridge.log("[$LOG_TAG] cloud profile blocked")
            // return empty profile with correct name and version
            return JSONObject().apply {
                arrayOf("common_config", "booster_config").forEach { name ->
                    profile.optJSONObject(name)?.let { oldConf ->
                        put(name, JSONObject().also { newConf ->
                            newConf.put("config_name", oldConf.opt("config_name"))
                            newConf.put("version", oldConf.optInt("version"))
                            newConf.put("group_name", oldConf.opt("group_name"))
                            newConf.put("enable", false)
                            newConf.put("with_model", false)
                            newConf.put("params", JSONObject())
                        })
                    }
                }
            }
        }
        val boosterConfig = profile.optJSONObject("booster_config")
            ?.optJSONObject("params")
            ?.optJSONObject("game_booster")
            ?.optJSONObject("booster_config")
        val overrideConfig = boosterConfig?.optJSONArray("ovrride_config")
        if (overrideConfig != null){
            if (profileRule == JoyoseProfileRule.RM_APP_DFPS) {
                for (i in 0 until overrideConfig.length()) {
                    overrideConfig.optJSONObject(i)?.let { o ->
                        val iterator = o.keys()
                        while (iterator.hasNext()) {
                            val key = iterator.next();
                            if (key.contains("dynamic", true) && key.contains("fps", true)) {
                                iterator.remove()
                            }
                        }
                    }
                }
            } else if (profileRule == JoyoseProfileRule.RM_APP_LIST) {
                boosterConfig.put("ovrride_config", JSONArray())
            }
            XposedBridge.log("[$LOG_TAG] cloud profile rule processed: ${overrideConfig.length()}")
        }
        return profile
    }
}

