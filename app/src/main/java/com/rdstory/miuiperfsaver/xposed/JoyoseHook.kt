package com.rdstory.miuiperfsaver.xposed

import com.rdstory.miuiperfsaver.Constants.LOG_TAG
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

object JoyoseHook {
    fun initHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.xiaomi.joyose") return
        XposedHelpers.findAndHookConstructor(HttpURLConnection::class.java, URL::class.java,
            object : XC_MethodHook() {
                var httpImplHooked = false
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (httpImplHooked) return
                    httpImplHooked = true
                    hookHttpImpl(param.thisObject::class.java)
                }
            })
        XposedBridge.log("[${LOG_TAG}] process hooked: ${lpparam.processName}")
    }

    /**
     * Hook HttpURLConnection used to request the cloud profile, obfuscated business code may change
     */
    private fun hookHttpImpl(httpImplClass: Class<*>) {
        val inputStreamMap = WeakHashMap<HttpURLConnection, InputStream>()
        XposedHelpers.findAndHookMethod(httpImplClass, "getInputStream", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val con = param.thisObject as HttpURLConnection
                val url = con.url?.toString() ?: return
                if (url.contains("sys.miui.com/api/profile/getProfile.do?")) {
                    // return inputStream of hacked profile
                    inputStreamMap[con]?.let {
                        param.result = it
                        return
                    }
                    try {
                        val bytes = (param.result as InputStream).use { it.readBytes() }
                        val hackProfile = hackProfile(String(bytes, Charsets.UTF_8))
                        val newStream = ByteArrayInputStream(hackProfile.toByteArray(Charsets.UTF_8))
                        inputStreamMap[con] = newStream
                        param.result = newStream
                    } catch (e: Exception) {
                        XposedBridge.log("[$LOG_TAG] failed to modify joyose profile. e: ${e.message}")
                    }
                }
            }
        })
        XposedHelpers.findAndHookMethod(httpImplClass, "connect",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val con = param.thisObject as HttpURLConnection
                    val url = con.url?.toString() ?: return
                    if (url.contains("tracking.miui.com/") || url.contains("ad.xiaomi.com/")) {
                        param.throwable = IOException() // we don't need tracking and ad
                    }
                }
            })
        XposedHelpers.findAndHookMethod(httpImplClass, "disconnect",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val con = param.thisObject as HttpURLConnection
                    inputStreamMap.remove(con) // remove inputStream cache
                }
            })
        XposedBridge.log("[${LOG_TAG}] HttpURLConnection hooked, impl: ${httpImplClass.name}")
    }

    /**
     * Hack joyose profile here.
     * Not sure what it does exactly, but "dynamic_fps" is known to be used to limit game FPS
     */
    private fun hackProfile(profileResp: String): String {
        val config = JSONObject(profileResp)
        if (config.optString("status") == "true" && config.optInt("msgCode") == 200) {
            val profile = JSONObject(config.optString("profile"))
            val boosterConfig = profile.optJSONObject("booster_config")
                ?.optJSONObject("params")
                ?.optJSONObject("game_booster")
                ?.optJSONObject("booster_config")
            val overrideConfig = boosterConfig?.optJSONArray("ovrride_config")
            if (overrideConfig != null){
                for (i in 0 until overrideConfig.length()) {
                    overrideConfig.optJSONObject(i)?.let { o ->
                        o.remove("dynamic_fps")
                        o.remove("dynamic_fps_M")
                    }
                }
                XposedBridge.log("[$LOG_TAG] hack ovrride_config: ${overrideConfig.length()}")
            }
            config.put("profile", profile.toString())
            return config.toString()
        }
        return profileResp
    }
}

