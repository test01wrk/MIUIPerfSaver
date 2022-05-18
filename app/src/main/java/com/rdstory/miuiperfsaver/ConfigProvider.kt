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

        private const val COLUMN_PROFILE_RULE = "profile_rule"
        private const val COLUMN_PKG = "package_name"
        private const val COLUMN_PKG_FPS = "package_fps"
        private val APP_LIST_URI = Uri.parse("content://${CONFIG_PROVIDER_AUTHORITY}/app_list")
        private val JOYOSE_CONFIG_URI = Uri.parse("content://${CONFIG_PROVIDER_AUTHORITY}/joyose_config")
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(CONFIG_PROVIDER_AUTHORITY, "app_list", URI_CODE_APP_LIST)
            addURI(CONFIG_PROVIDER_AUTHORITY, "joyose_config", URI_CODE_JOYOSE_CONFIG)
        }

        fun getSavedAppConfig(context: Context): Map<String, Int>? {
            val cursor = context.contentResolver.query(APP_LIST_URI, null, null, null)
            cursor ?: return null
            val appFpsMap = mutableMapOf<String, Int>()
            if (cursor.moveToFirst()) {
                do {
                    val pkgIndex = cursor.getColumnIndex(COLUMN_PKG).takeIf { it >= 0 } ?: continue
                    val pkgFpsIndex = cursor.getColumnIndex(COLUMN_PKG_FPS).takeIf { it >= 0 } ?: continue
                    appFpsMap[cursor.getString(pkgIndex)] = cursor.getInt(pkgFpsIndex)
                } while (cursor.moveToNext())
            }
            cursor.close()
            return appFpsMap
        }

        fun observeSavedAppChange(context: Context, callback: () -> Unit) {
            context.contentResolver.registerContentObserver(
                APP_LIST_URI,
                false,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        callback()
                    }
                }
            )
        }

        fun getJoyoseConfig(context: Context): JoyoseProfileRule? {
            val cursor = context.contentResolver.query(JOYOSE_CONFIG_URI, null, null, null)
            cursor ?: return null
            var rule: JoyoseProfileRule? = null
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(COLUMN_PROFILE_RULE)
                if (index >= 0) {
                    val name = cursor.getString(index)
                    rule = JoyoseProfileRule.values().find { it.value == name }
                }
            }
            cursor.close()
            return rule
        }

        fun observeJoyoseConfigChange(context: Context, callback: () -> Unit) {
            context.contentResolver.registerContentObserver(
                JOYOSE_CONFIG_URI,
                false,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        callback()
                    }
                }
            )
        }

        fun notifyAppListChange(context: Context) {
            context.contentResolver.notifyChange(APP_LIST_URI, null)
        }

        fun notifyJoyoseConfigChange(context: Context) {
            context.contentResolver.notifyChange(JOYOSE_CONFIG_URI, null)
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
                val apps = Configuration.getAll()
                return MatrixCursor(arrayOf(COLUMN_PKG, COLUMN_PKG_FPS)).apply {
                    apps.forEach { newRow().add(it.key).add(it.value) }
                }
            }
            URI_CODE_JOYOSE_CONFIG -> {
                return MatrixCursor(arrayOf(COLUMN_PROFILE_RULE)).apply {
                    newRow().add(Configuration.joyoseProfileRule)
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
        return 0
    }
}
