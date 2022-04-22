package com.rdstory.miuiperfsaver

import android.content.Context
import android.content.SharedPreferences
import com.rdstory.miuiperfsaver.Constants.SETTINGS_SP_KEY
import com.rdstory.miuiperfsaver.Constants.PREF_KEY_SAVED_APP_LIST
import com.rdstory.miuiperfsaver.utils.PackageInfoUtil

object Configuration {
    lateinit var sharedPreferences: SharedPreferences
        private set
    private val savedApps = mutableSetOf<String>()
    private lateinit var application: Context

    fun init(context: Context) {
        application = context.applicationContext
        sharedPreferences =
            context.getSharedPreferences(SETTINGS_SP_KEY, Context.MODE_PRIVATE)
        try {
            sharedPreferences.getStringSet(PREF_KEY_SAVED_APP_LIST, null)
        } catch (ignore: Exception) {
            null
        }?.let {
            savedApps.addAll(it)
        }
        notifyChange()
        PackageInfoUtil.load()
    }

    private fun save() {
        sharedPreferences.edit().putStringSet(PREF_KEY_SAVED_APP_LIST, savedApps).apply()
    }

    private fun notifyChange() {
        ConfigProvider.notifyChange(application)
    }

    fun add(packageName: String) {
        if (savedApps.add(packageName)) {
            save()
            notifyChange()
        }
    }

    fun remove(packageName: String) {
        if (savedApps.remove(packageName)) {
            save()
            notifyChange()
        }
    }

    fun isEnabled(packageName: String): Boolean {
        return savedApps.contains(packageName)
    }

    fun getAll(): Collection<String> {
        return savedApps
    }
}