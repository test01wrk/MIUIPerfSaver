package com.rdstory.miuiperfsaver

import android.content.Context
import android.content.SharedPreferences
import com.rdstory.miuiperfsaver.Constants.SETTINGS_SP_KEY
import com.rdstory.miuiperfsaver.Constants.PREF_KEY_SAVED_APP_LIST

object Configuration {
    private lateinit var sharedPreferences: SharedPreferences
    private val savedApps = mutableMapOf<String, Int>()
    private lateinit var application: Context
    private const val SEPARATOR = '|'
    private val supportedFPSBackend = arrayListOf<Int>()
    val supportedFPS: List<Int>
        get() = supportedFPSBackend

    fun init(context: Context) {
        application = context.applicationContext
        Utils.getSupportFps(context)?.let { supportedFPSBackend.addAll(it) }
        sharedPreferences =
            context.getSharedPreferences(SETTINGS_SP_KEY, Context.MODE_PRIVATE)
        try {
            sharedPreferences.getStringSet(PREF_KEY_SAVED_APP_LIST, null)
        } catch (ignore: Exception) {
            null
        }?.forEach { pkg ->
            val pkgAndFps = pkg.split(SEPARATOR)
            val fps = pkgAndFps.getOrNull(1)?.toInt() ?: supportedFPS.getOrNull(0)
            fps?.let { savedApps[pkgAndFps[0]] = it }
        }
        notifyChange()
    }

    private fun save() {
        val saveSet = savedApps.map { "${it.key}$SEPARATOR${it.value}" }.toSet()
        sharedPreferences.edit().putStringSet(PREF_KEY_SAVED_APP_LIST, saveSet).apply()
    }

    private fun notifyChange() {
        ConfigProvider.notifyChange(application)
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
            notifyChange()
        }
    }

    fun isPerfSaved(packageName: String): Boolean {
        return savedApps.contains(packageName)
    }

    fun getAll(): Map<String, Int> {
        return savedApps
    }
}