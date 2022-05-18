package com.rdstory.miuiperfsaver

import android.content.Context
import android.content.SharedPreferences
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

    fun init() {
        val context = MainApplication.application
        Utils.getSupportFps(context)?.let { supportedFPSBackend.addAll(it) }
        sharedPreferences =
            context.getSharedPreferences(SETTINGS_SP_KEY, Context.MODE_PRIVATE)
        joyoseProfileRule = sharedPreferences.getString(PREF_KEY_JOYOSE_PROFILE_RULE, null)
            ?.takeIf { r -> JoyoseProfileRule.values().indexOfFirst { it.value == r } >= 0 }
            ?: JoyoseProfileRule.BLOCK_ALL.value
        try {
            sharedPreferences.getStringSet(PREF_KEY_SAVED_APP_LIST, null)
        } catch (ignore: Exception) {
            null
        }?.forEach { pkg ->
            val pkgAndFps = pkg.split(SEPARATOR)
            val fps = pkgAndFps.getOrNull(1)?.toInt() ?: supportedFPS.getOrNull(0)
            fps?.let { savedApps[pkgAndFps[0]] = it }
        }
        ConfigProvider.notifyAppListChange(context)
        ConfigProvider.notifyJoyoseConfigChange(context)
    }

    private fun save() {
        val saveSet = savedApps.map { "${it.key}$SEPARATOR${it.value}" }.toSet()
        sharedPreferences.edit().putStringSet(PREF_KEY_SAVED_APP_LIST, saveSet).apply()
    }

    fun fpsIndex(packageName: String): Int {
        return getFps(packageName)?.let { supportedFPS.indexOf(it) }?.takeIf { it >= 0 } ?: -1
    }

    fun getFps(packageName: String): Int? {
        return savedApps[packageName]
    }

    fun setFps(packageName: String, fps: Int?) {
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
            save()
            ConfigProvider.notifyAppListChange(MainApplication.application)
        }
    }

    fun isPerfSaved(packageName: String): Boolean {
        return savedApps.contains(packageName)
    }

    fun getAll(): Map<String, Int> {
        return savedApps
    }

    fun setJoyoseProfileRule(rule: JoyoseProfileRule) {
        if (rule.value != joyoseProfileRule) {
            joyoseProfileRule = rule.value
            sharedPreferences.edit().putString(PREF_KEY_JOYOSE_PROFILE_RULE, joyoseProfileRule).apply()
            ConfigProvider.notifyJoyoseConfigChange(MainApplication.application)
        }
    }
}