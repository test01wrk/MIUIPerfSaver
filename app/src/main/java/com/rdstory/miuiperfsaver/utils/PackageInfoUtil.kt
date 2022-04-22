package com.rdstory.miuiperfsaver.utils

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rdstory.miuiperfsaver.Configuration
import com.rdstory.miuiperfsaver.Constants.LOG_TAG
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap


object PackageInfoUtil {
    private val cache = ConcurrentHashMap<String, PackageInfoCache>()
    private val gson = Gson()
    fun getPackageInfo(pkg: String): PackageInfoCache? {
        return cache[pkg]
    }

    fun load() {
        Performance.timedAction("load cache") {
            Configuration.sharedPreferences.getString("packageInfoCache", null)?.let { json ->
                val type = object : TypeToken<Map<String, PackageInfoCache>>() {}.type
                gson.fromJson<Map<String, PackageInfoCache>>(json, type)?.let {
                    this.cache.putAll(it)
                }
            }
            Log.i(LOG_TAG, "package info cache load: ${cache.size}")
        }
    }

    fun load(pm: PackageManager, packages: List<PackageInfo>) {
        TaskExecutor.execute {
            Performance.timedAction("update all labels") {
                val cache = mutableMapOf<String, PackageInfoCache>()
                packages.forEach {
                    cache[it.packageName] = PackageInfoCache().apply {
                        pkg = it.packageName
                        name = pm.getApplicationLabel(it.applicationInfo).toString()
                    }
                }
                Configuration.sharedPreferences.edit().putString("packageInfoCache", gson.toJson(cache)).apply()
                this.cache.clear()
                this.cache.putAll(cache)
            }
        }
    }

}

class PackageInfoCache : Serializable {
    var pkg: String? = null
    var name: String? = null
}