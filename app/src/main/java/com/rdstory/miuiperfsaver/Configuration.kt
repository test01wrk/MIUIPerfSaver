package com.rdstory.miuiperfsaver

import android.content.Context
import android.content.SharedPreferences
import com.rdstory.miuiperfsaver.ConfigProvider.Companion.APP_LIST_URI
import com.rdstory.miuiperfsaver.ConfigProvider.Companion.DC_COMPAT_CONFIG_URI
import com.rdstory.miuiperfsaver.ConfigProvider.Companion.JOYOSE_CONFIG_URI
import com.rdstory.miuiperfsaver.ConfigProvider.Companion.REFRESH_RATE_URI
import com.rdstory.miuiperfsaver.Constants.PREF_KEY_DC_BRIGHTNESS
import com.rdstory.miuiperfsaver.Constants.PREF_KEY_DC_FPS_LIMIT
import com.rdstory.miuiperfsaver.Constants.PREF_KEY_FIXED_REFRESH_RATE
import com.rdstory.miuiperfsaver.Constants.PREF_KEY_JOYOSE_PROFILE_RULE
import com.rdstory.miuiperfsaver.Constants.SETTINGS_SP_KEY
import com.rdstory.miuiperfsaver.Constants.PREF_KEY_SAVED_APP_LIST

object Configuration {
    private lateinit var sharedPreferences: SharedPreferences
    private val savedApps = mutableMapOf<String, Int>()
    private const val SEPARATOR = '|'
    private val supportedFPSBackend = arrayListOf<Int>()
    val supportedFPS: List<Int>
        get() = supportedFPSBackend
    var joyoseProfileRule: String = JoyoseProfileRule.BLOCK_ALL.value
        private set
    var dcFpsLimit = 0
        private set
    var dcBrightness = 0
        private set
    var fixedFps = 0
        private set

    fun init() {
        val context = MainApplication.application
        supportedFPSBackend.addAll(Utils.getSupportFps(context))
        sharedPreferences =
            context.getSharedPreferences(SETTINGS_SP_KEY, Context.MODE_PRIVATE)
        joyoseProfileRule = sharedPreferences.getString(PREF_KEY_JOYOSE_PROFILE_RULE, null)
            ?.takeIf { r -> JoyoseProfileRule.values().indexOfFirst { it.value == r } >= 0 }
            ?: JoyoseProfileRule.BLOCK_ALL.value
        dcFpsLimit = sharedPreferences.getInt(PREF_KEY_DC_FPS_LIMIT, 0)
            .takeIf { supportedFPS.contains(it) } ?: 0
        dcBrightness = sharedPreferences.getInt(PREF_KEY_DC_BRIGHTNESS, 0)
        fixedFps = sharedPreferences.getInt(PREF_KEY_FIXED_REFRESH_RATE, 0)
            .takeIf { supportedFPS.contains(it) } ?: 0
        try {
            sharedPreferences.getStringSet(PREF_KEY_SAVED_APP_LIST, null)
        } catch (ignore: Exception) {
            null
        }?.forEach { pkg ->
            val pkgAndFps = pkg.split(SEPARATOR)
            val fps = pkgAndFps.getOrNull(1)?.toInt() ?: supportedFPS.getOrNull(0)
            fps?.let { savedApps[pkgAndFps[0]] = it }
        }
        ConfigProvider.notifyChange(context)
    }

    private fun savePkgFpsMap() {
        val saveSet = savedApps.map { "${it.key}$SEPARATOR${it.value}" }.toSet()
        sharedPreferences.edit().putStringSet(PREF_KEY_SAVED_APP_LIST, saveSet).apply()
    }

    fun fpsIndex(packageName: String): Int {
        return getPkgFps(packageName)?.let { supportedFPS.indexOf(it) }?.takeIf { it >= 0 } ?: -1
    }

    fun getPkgFps(packageName: String): Int? {
        return savedApps[packageName]
    }

    fun setPkgFps(packageName: String, fps: Int?) {
        var changed = false
        val oldFps = savedApps[packageName]
        if (fps != null && (oldFps == null || oldFps != fps)) {
            savedApps[packageName] = fps
            changed = true
        } else if (fps == null && oldFps != null) {
            savedApps.remove(packageName)
            changed = true
        }
        if (changed) {
            savePkgFpsMap()
            ConfigProvider.notifyChange(MainApplication.application, APP_LIST_URI)
        }
    }

    fun isPkgHasFps(packageName: String): Boolean {
        return savedApps.contains(packageName)
    }

    fun getPkgFpsMap(): Map<String, Int> {
        return savedApps
    }

    fun setJoyoseProfileRule(rule: JoyoseProfileRule) {
        if (rule.value != joyoseProfileRule) {
            joyoseProfileRule = rule.value
            sharedPreferences.edit().putString(PREF_KEY_JOYOSE_PROFILE_RULE, joyoseProfileRule).apply()
            ConfigProvider.notifyChange(MainApplication.application, JOYOSE_CONFIG_URI)
        }
    }

    fun setDCFpsLimit(fps: Int?) {
        if (dcFpsLimit != fps) {
            dcFpsLimit = fps ?: 0
            sharedPreferences.edit().putInt(PREF_KEY_DC_FPS_LIMIT, dcFpsLimit).apply()
            ConfigProvider.notifyChange(MainApplication.application, DC_COMPAT_CONFIG_URI)
        }
    }

    fun setDCBrightness(brightness: Int) {
        if (dcBrightness != brightness) {
            dcBrightness = brightness
            sharedPreferences.edit().putInt(PREF_KEY_DC_BRIGHTNESS, dcBrightness).apply()
            ConfigProvider.notifyChange(MainApplication.application, DC_COMPAT_CONFIG_URI)
        }
    }

    fun setFixedFps(fps: Int) {
        val validFps = fps.takeIf { supportedFPS.contains(it) } ?: 0
        if (fixedFps != validFps) {
            fixedFps = validFps
            sharedPreferences.edit().putInt(PREF_KEY_FIXED_REFRESH_RATE, fps).apply()
            ConfigProvider.notifyChange(MainApplication.application, REFRESH_RATE_URI)
        }
    }

}