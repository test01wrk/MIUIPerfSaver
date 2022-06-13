package com.rdstory.miuiperfsaver.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.rdstory.miuiperfsaver.Constants.DEVICE_XIAOMI_12X
import com.rdstory.miuiperfsaver.Constants.LOG_TAG

object DCFPSCompat {

    interface Callback {
        fun setFpsLimit(fpsLimit: Int)
        fun setFps(fps: Int)
        fun updateCurrentFps()
    }

    fun init(context: Context, callback: Callback) {
        if (Build.DEVICE != DEVICE_XIAOMI_12X) {
            return // Xiaomi 12x, DC backlight not compatible with 120Hz, limit to 90Hz
        }
        val mainHandler = Handler(Looper.getMainLooper())
        var shouldLimitFps: Boolean? = null
        fun updateFpsLimit() {
            Log.i(LOG_TAG, "updateFpsLimit shouldLimitFps: $shouldLimitFps")
            if (shouldLimitFps == true) {
                callback.setFpsLimit(60)
                callback.setFps(60)
                mainHandler.postDelayed({
                    callback.setFpsLimit(90)
                    callback.setFps(90)
                    callback.updateCurrentFps()
                }, 300)
            } else if (shouldLimitFps == false) {
                callback.setFpsLimit(Int.MAX_VALUE)
                callback.updateCurrentFps()
            }
        }
        fun checkShouldLimitFps() {
            val dcEnabled = Settings.System.getInt(context.contentResolver, "dc_back_light", 0) == 1
            val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            Log.i(LOG_TAG, "settings changed. dcEnabled: $dcEnabled, brightness: $brightness")
            val wasLimit = shouldLimitFps
            shouldLimitFps = dcEnabled && brightness < 400
            if (wasLimit != shouldLimitFps) {
                updateFpsLimit()
            }
        }
        checkShouldLimitFps()
        val dcURI = Settings.System.getUriFor("dc_back_light")
        context.contentResolver.registerContentObserver(dcURI, false,
            object : ContentObserver(mainHandler) {
                override fun onChange(selfChange: Boolean) {
                    checkShouldLimitFps()
                }
            })
        val brightnessURI = Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS)
        context.contentResolver.registerContentObserver(brightnessURI, false,
            object : ContentObserver(mainHandler) {
                override fun onChange(selfChange: Boolean) {
                    checkShouldLimitFps()
                }
            })
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.i(LOG_TAG, "restore dc, user present set user_refresh_rate. shouldLimitFps=$shouldLimitFps")
                updateFpsLimit()
            }
        }, IntentFilter(Intent.ACTION_USER_PRESENT))
    }
}