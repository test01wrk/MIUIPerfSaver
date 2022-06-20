package com.rdstory.miuiperfsaver

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings

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

}