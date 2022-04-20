package com.rdstory.miuiperfsaver

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.rdstory.miuiperfsaver.BuildConfig.CONFIG_PROVIDER_AUTHORITY

class ConfigProvider : ContentProvider() {
    companion object {
        private const val COLUMN_PKG = "package_name"
        private val APP_LIST_URI = Uri.parse("content://${CONFIG_PROVIDER_AUTHORITY}/app_list")

        fun getSavedAppList(context: Context): Collection<String>? {
            val cursor = context.contentResolver.query(APP_LIST_URI, null, null, null)
            cursor ?: return null
            val appList = arrayListOf<String>()
            if (cursor.moveToFirst()) {
                do {
                    cursor.getColumnIndex(COLUMN_PKG).takeIf { it >= 0 }?.let {
                        appList.add(cursor.getString(it))
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()
            return appList
        }

        fun observeSavedAppList(context: Context, callback: () -> Unit) {
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

        fun notifyChange(context: Context) {
            context.contentResolver.notifyChange(APP_LIST_URI, null)
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
    ): Cursor {
        val apps = Configuration.getAll()
        return MatrixCursor(arrayOf(COLUMN_PKG)).apply {
            apps.forEach { newRow().add(it) }
        }
    }

    override fun getType(uri: Uri): String {
        return "plain"
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
