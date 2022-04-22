package com.rdstory.miuiperfsaver.utils

import android.os.SystemClock
import android.util.Log
import com.rdstory.miuiperfsaver.Constants.LOG_TAG

object Performance {
    fun timedAction(name: String, action: () -> Unit) {
        val start = SystemClock.uptimeMillis()
        action()
        val end = SystemClock.uptimeMillis()
        Log.i(LOG_TAG, "action: $name, time: ${end - start}ms")
    }
}