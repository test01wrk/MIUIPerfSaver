package com.rdstory.miuiperfsaver.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.rdstory.miuiperfsaver.Constants.FPS_COOKIE_DEFAULT
import com.rdstory.miuiperfsaver.Constants.FPS_COOKIE_EXCLUDE
import com.rdstory.miuiperfsaver.Constants.LOG_TAG
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

object DCFPSCompat {

    interface Callback {
        fun setFpsLimit(fpsLimit: Int?)
    }

    private var setMethodInternalIIS: Method? = null
    private var setMethodInternalII: Method? = null
    private var setMethodII: Method? = null
    private val updateHandler = Handler(Looper.getMainLooper())
    private var shouldLimitFps: Boolean? = null
    private lateinit var callback: Callback
    private lateinit var frameSettingObject: Any

    fun init(context: Context, frameSettingObject: Any, callback: Callback) {
        if (!isDcFpsInCompat(context)) {
            return
        }
        this.frameSettingObject = frameSettingObject
        this.callback = callback
        initFrameSettingMethods()
        checkShouldLimitFps(context)
        startObserving(context)
    }

    private fun isDcFpsInCompat(context: Context): Boolean {
        return try {
            XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("miui.util.FeatureParser", context.classLoader),
                "getBoolean",
                "dc_backlight_fps_incompatible",
                false
            ) as Boolean
        } catch (e: Throwable) {
            false
        }
    }

    private fun initFrameSettingMethods() {
        val classDisplayFrameSetting = frameSettingObject::class.java
        setMethodInternalIIS = XposedHelpers.findMethodExactIfExists(
            classDisplayFrameSetting,
            "setScreenEffectInternal",
            Int::class.java, Int::class.java, String::class.java
        )
        setMethodInternalII = XposedHelpers.findMethodExactIfExists(
            classDisplayFrameSetting,
            "setScreenEffectInternal",
            Int::class.java, Int::class.java
        )
        setMethodII = XposedHelpers.findMethodExactIfExists(
            classDisplayFrameSetting,
            "setScreenEffect",
            Int::class.java, Int::class.java
        )
    }

    private fun startObserving(context: Context) {
        val observerHandler = Handler(Looper.getMainLooper())
        val dcURI = Settings.System.getUriFor("dc_back_light")
        context.contentResolver.registerContentObserver(dcURI, false,
            object : ContentObserver(observerHandler) {
                override fun onChange(selfChange: Boolean) {
                    checkShouldLimitFps(context)
                }
            })
        val brightnessURI = Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS)
        context.contentResolver.registerContentObserver(brightnessURI, false,
            object : ContentObserver(observerHandler) {
                override fun onChange(selfChange: Boolean) {
                    checkShouldLimitFps(context)
                }
            })
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.i(LOG_TAG, "restore dc, user present set user_refresh_rate. shouldLimitFps=$shouldLimitFps")
                updateFpsLimit()
            }
        }, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    private fun updateFpsLimit(retry: Int = 10) {
        Log.i(LOG_TAG, "updateFpsLimit shouldLimitFps: $shouldLimitFps")
        updateHandler.removeCallbacksAndMessages(null)
        if (shouldLimitFps == true) {
            callback.setFpsLimit(60)
            updateCurrentFps()
            updateHandler.postDelayed({
                callback.setFpsLimit(90)
                if (!updateCurrentFps() && retry > 0) {
                    updateHandler.postDelayed({ updateFpsLimit(retry - 1) }, 500)
                }
            }, 500)
        } else if (shouldLimitFps == false) {
            callback.setFpsLimit(null)
            updateCurrentFps()
        }
    }

    private fun checkShouldLimitFps(context: Context) {
        val dcEnabled = Settings.System.getInt(context.contentResolver, "dc_back_light", 0) == 1
        val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        Log.i(LOG_TAG, "settings changed. dcEnabled: $dcEnabled, brightness: $brightness")
        val wasLimit = shouldLimitFps
        shouldLimitFps = dcEnabled && brightness < 400
        if (wasLimit != shouldLimitFps) {
            updateFpsLimit()
        }
    }

    private fun updateCurrentFps(): Boolean {
        val fgResult = frameSettingObject.getObjectField<Any>("mCurrentFgInfo")?.let {
            frameSettingObject.callMethod<Unit>("onForegroundChanged", it)
            true
        } ?: false
        frameSettingObject.getObjectField<Int>("mCurrentFps")?.let {
            return setFps(it) && fgResult
        }
        return false
    }

    private fun setFps(fps: Int): Boolean {
        val curCookie = frameSettingObject.getObjectField<Int>("mCurrentCookie")
        val cookie = if (curCookie == FPS_COOKIE_EXCLUDE) FPS_COOKIE_DEFAULT else FPS_COOKIE_EXCLUDE
        setMethodInternalIIS?.let { method ->
            frameSettingObject.getObjectField<Any>("mCurrentFgPkg")?.let {
                method.invoke(frameSettingObject, fps, cookie, it)
                return true
            }
            return false
        }
        (setMethodInternalII ?: setMethodII)?.let { method ->
            method.invoke(fps, cookie)
            return true
        }
        return false
    }
}