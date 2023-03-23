package com.rdstory.miuiperfsaver

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

object Utils {
    fun getSupportFps(context: Context): List<Int> {
        return (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(0)
            .supportedModes?.mapTo(mutableSetOf()) { it.refreshRate.toInt() }?.sorted()?.reversed()
            ?: listOf(60)
    }

    fun getMaxFPS(context: Context): Int {
        return getSupportFps(context)[0]
    }

    fun getSystemBrightness(context: Context): Int {
        return Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 0)
    }

    fun isDCIncompatible(): Boolean {
        return try {
            Class.forName("miui.util.FeatureParser").getDeclaredMethod(
                "getBoolean", String::class.java, Boolean::class.java
            ).apply { isAccessible = true }
                .invoke(null, "dc_backlight_fps_incompatible", false) as Boolean
        } catch (e: Throwable) {
            false
        }
    }

    fun alertAppNotWorking(context: Context, pkg: String) {
        val appInfo = try {
            context.packageManager.getApplicationInfo(pkg, 0)
        } catch (ignore: Throwable) {
            null
        }
        if (appInfo?.enabled != true) {
            val appName = appInfo?.let { context.packageManager.getApplicationLabel(it) } ?: pkg
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.app_package_not_working, appName))
                .setMessage(R.string.app_package_not_working_desc)
                .setPositiveButton(R.string.confirm) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

}