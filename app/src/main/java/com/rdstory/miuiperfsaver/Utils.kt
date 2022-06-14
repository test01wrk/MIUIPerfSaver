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

    fun getDCEnabled(context: Context): Boolean {
        return Settings.System.getInt(context.contentResolver, "dc_back_light", 0) == 1
    }

    fun getSystemBrightness(context: Context): Int {
        return Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 0)
    }

}