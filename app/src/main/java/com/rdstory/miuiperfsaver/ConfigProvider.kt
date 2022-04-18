package com.rdstory.miuiperfsaver

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.rdstory.miuiperfsaver.Constants.CONTENT_PROVIDER_AUTH

class ConfigProvider : ContentProvider() {
    companion object {
        private const val COLUMN_PKG = "package_name"

        fun getSavedAppList(context: Context): Collection<String> {
            val cursor = context.contentResolver.query(
                Uri.parse("content://${CONTENT_PROVIDER_AUTH}/app_list"),
                null,
                null,
                null
            )
            cursor ?: return emptyList()
            val appList = arrayListOf<String>()
            if (cursor.moveToFirst()) {
                do {
                    val index = cursor.getColumnIndex(COLUMN_PKG)
                    if (index >= 0) {
                        appList.add(cursor.getString(index))
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()
            return appList
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