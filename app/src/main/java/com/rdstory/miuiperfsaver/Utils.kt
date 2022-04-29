package com.rdstory.miuiperfsaver

import android.content.Context
import android.hardware.display.DisplayManager

object Utils {
    fun getSupportFps(context: Context): List<Int>? {
        return (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(0)
            .supportedModes?.mapTo(mutableSetOf()) { it.refreshRate.toInt() }?.sorted()?.reversed()
    }

    fun getMaxFPS(context: Context): Int? {
        return getSupportFps(context)?.getOrNull(0)
    }
}