package com.rdstory.miuiperfsaver

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.rdstory.miuiperfsaver.Constants.ACTION_UPDATE_SAVED_LIST
import com.rdstory.miuiperfsaver.Constants.EXTRA_SAVED_LIST
import com.rdstory.miuiperfsaver.Constants.SETTINGS_SP_KEY
import com.rdstory.miuiperfsaver.Constants.PREF_KEY_SAVED_APP_LIST

object Configuration {
    private lateinit var sharedPreferences: SharedPreferences
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
    }

    private fun save() {
        sharedPreferences.edit().putStringSet(PREF_KEY_SAVED_APP_LIST, savedApps).apply()
    }

    private fun notifyChange() {
        application.sendBroadcast(Intent(ACTION_UPDATE_SAVED_LIST).apply {
            val appList = arrayListOf<String>().apply { addAll(savedApps) }
            putStringArrayListExtra(EXTRA_SAVED_LIST, appList)
        })
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
}