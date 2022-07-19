package com.rdstory.miuiperfsaver

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.ContentObserver
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.rdstory.miuiperfsaver.BuildConfig.CONFIG_PROVIDER_AUTHORITY


class ConfigProvider : ContentProvider() {
    companion object {
        private const val URI_CODE_APP_LIST = 1
        private const val URI_CODE_JOYOSE_CONFIG = 2
        private const val URI_CODE_DC_COMPAT_CONFIG = 3
        private const val URI_CODE_REFRESH_RATE = 4

        private const val COLUMN_PROFILE_RULE = "profile_rule"
        private const val COLUMN_PKG = "package_name"
        private const val COLUMN_PKG_FPS = "package_fps"
        const val COLUMN_DC_FPS_LIMIT = "dc_fps_limit"
        const val COLUMN_DC_BRIGHTNESS = "dc_brightness"
        const val COLUMN_REFRESH_RATE = "refresh_rate"
        private val ALL_URI: Uri = Uri.parse("content://${CONFIG_PROVIDER_AUTHORITY}")
        val APP_LIST_URI: Uri = Uri.parse("content://${CONFIG_PROVIDER_AUTHORITY}/app_list")
        val JOYOSE_CONFIG_URI: Uri = Uri.parse("content://${CONFIG_PROVIDER_AUTHORITY}/joyose_config")
        val DC_COMPAT_CONFIG_URI: Uri = Uri.parse("content://${CONFIG_PROVIDER_AUTHORITY}/dc_compat_config")
        val REFRESH_RATE_URI: Uri = Uri.parse("content://${CONFIG_PROVIDER_AUTHORITY}/refresh_rate")
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(CONFIG_PROVIDER_AUTHORITY, "app_list", URI_CODE_APP_LIST)
            addURI(CONFIG_PROVIDER_AUTHORITY, "joyose_config", URI_CODE_JOYOSE_CONFIG)
            addURI(CONFIG_PROVIDER_AUTHORITY, "dc_compat_config", URI_CODE_DC_COMPAT_CONFIG)
            addURI(CONFIG_PROVIDER_AUTHORITY, "refresh_rate", URI_CODE_REFRESH_RATE)
        }

        fun notifyChange(context: Context, uri: Uri = ALL_URI) {
            context.contentResolver.notifyChange(uri, null)
        }

        fun observeChange(context: Context, uri: Uri, callback: () -> Unit) {
            context.contentResolver.registerContentObserver(
                uri, false,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        callback()
                    }
                }
            )
        }

        fun getSavedAppConfig(context: Context): Map<String, Int>? {
            val cursor = context.contentResolver.query(APP_LIST_URI, null, null, null)
            cursor?.use {
                if (!cursor.moveToFirst()) return@use
                val appFpsMap = mutableMapOf<String, Int>()
                do {
                    val pkgIndex = cursor.getColumnIndex(COLUMN_PKG).takeIf { it >= 0 } ?: continue
                    val pkgFpsIndex =
                        cursor.getColumnIndex(COLUMN_PKG_FPS).takeIf { it >= 0 } ?: continue
                    appFpsMap[cursor.getString(pkgIndex)] = cursor.getInt(pkgFpsIndex)
                } while (cursor.moveToNext())
                return appFpsMap
            }
            return null
        }

        fun getJoyoseConfig(context: Context): JoyoseProfileRule? {
            val cursor = context.contentResolver.query(JOYOSE_CONFIG_URI, null, null, null)
            var rule: JoyoseProfileRule? = null
            cursor?.use {
                if (!cursor.moveToFirst()) return@use
                val index = cursor.getColumnIndex(COLUMN_PROFILE_RULE)
                if (index >= 0) {
                    val name = cursor.getString(index)
                    rule = JoyoseProfileRule.values().find { it.value == name }
                }
            }
            return rule
        }

        fun getDCCompatConfig(context: Context): Map<String, Int>? {
            val cursor = context.contentResolver.query(DC_COMPAT_CONFIG_URI, null, null, null)
            cursor?.use {
                if (!cursor.moveToFirst()) return@use
                val fpsLimitIndex = cursor.getColumnIndex(COLUMN_DC_FPS_LIMIT)
                val brightnessIndex = cursor.getColumnIndex(COLUMN_DC_BRIGHTNESS)
                if (fpsLimitIndex >= 0 && brightnessIndex >= 0) {
                    val fpsLimit = cursor.getInt(fpsLimitIndex)
                    val brightness = cursor.getInt(brightnessIndex)
                    return mapOf(
                        Pair(COLUMN_DC_FPS_LIMIT, fpsLimit),
                        Pair(COLUMN_DC_BRIGHTNESS, brightness)
                    )
                }
            }
            return null
        }

        fun getFixedRefreshRate(context: Context): Int {
            val cursor = context.contentResolver.query(REFRESH_RATE_URI, null, null, null)
            cursor?.use {
                if (!cursor.moveToFirst()) return@use
                val fpsIndex = cursor.getColumnIndex(COLUMN_REFRESH_RATE)
                if (fpsIndex >= 0) {
                    return cursor.getInt(fpsIndex)
                }
            }
            return 0
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        when (uriMatcher.match(uri)) {
            URI_CODE_APP_LIST -> {
                val apps = Configuration.getPkgFpsMap()
                return MatrixCursor(arrayOf(COLUMN_PKG, COLUMN_PKG_FPS)).apply {
                    apps.forEach { newRow().add(it.key).add(it.value) }
                }
            }
            URI_CODE_JOYOSE_CONFIG -> {
                return MatrixCursor(arrayOf(COLUMN_PROFILE_RULE)).apply {
                    newRow().add(Configuration.joyoseProfileRule)
                }
            }
            URI_CODE_DC_COMPAT_CONFIG -> {
                return MatrixCursor(arrayOf(COLUMN_DC_FPS_LIMIT, COLUMN_DC_BRIGHTNESS)).apply {
                    newRow()
                        .add(Configuration.dcFpsLimit)
                        .add(Configuration.dcBrightness)
                }
            }
            URI_CODE_REFRESH_RATE -> {
                return MatrixCursor(arrayOf(COLUMN_REFRESH_RATE)).apply {
                    newRow().add(Configuration.fixedFps)
                }
            }
        }
        return null
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        when (uriMatcher.match(uri)) {
            URI_CODE_REFRESH_RATE -> {
                val fps = values?.getAsInteger(COLUMN_REFRESH_RATE) ?: 0
                Configuration.setFixedFps(fps)
                return 1
            }
        }
        return 0
    }
}
