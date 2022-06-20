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
import com.rdstory.miuiperfsaver.ConfigProvider
import com.rdstory.miuiperfsaver.ConfigProvider.Companion.COLUMN_DC_BRIGHTNESS
import com.rdstory.miuiperfsaver.ConfigProvider.Companion.COLUMN_DC_FPS_LIMIT
import com.rdstory.miuiperfsaver.ConfigProvider.Companion.DC_COMPAT_CONFIG_URI
import com.rdstory.miuiperfsaver.Constants.FPS_COOKIE_DEFAULT
import com.rdstory.miuiperfsaver.Constants.FPS_COOKIE_EXCLUDE
import com.rdstory.miuiperfsaver.Constants.LOG_LEVEL
import com.rdstory.miuiperfsaver.Constants.LOG_TAG
import com.rdstory.miuiperfsaver.Utils
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/**
 * Some device's DC backlight feature is not compatible with high refresh rate.
 * Here we can limit the max refresh rate down to a DC compatible value.
 * While DC with high refresh rate may work, there maybe other bugs because
 * the system is not tweaked for that, eg. Xiaomi 12X's ambient light sensor
 * would return strange value.
 */
object DCFPSCompat {

    interface Callback {
        fun setFpsLimit(fpsLimit: Int?)
    }

    private val isDcIncompatible = Utils.isDCIncompatible()
    private const val ACTION_DELAY = 500L
    private lateinit var contextRef: WeakReference<Context>
    private var displayFeatureMan: Any? = null
    private var setMethodInternalIIS: Method? = null
    private var setMethodInternalII: Method? = null
    private var setMethodII: Method? = null
    private val updateHandler = Handler(Looper.getMainLooper())
    private var shouldLimitFps: Boolean? = null
    private var dcEnabled = false
    private var minRefreshRate = 60
    private lateinit var callback: Callback
    private lateinit var frameSettingObject: Any
    private var dcFpsLimit = 0
    private var dcBrightness = 0

    fun init(context: Context, frameSettingObject: Any, callback: Callback) {
        if (!isDcIncompatible) {
            return
        }
        contextRef = WeakReference(context)
        this.frameSettingObject = frameSettingObject
        this.callback = callback
        val updateConfig = fun() {
            val config = ConfigProvider.getDCCompatConfig(context) ?: emptyMap()
            XposedBridge.log("[$LOG_TAG] dc compat config updated. $config")
            val fpsLimit = config[COLUMN_DC_FPS_LIMIT] ?: 0
            val brightness = config[COLUMN_DC_BRIGHTNESS] ?: 0
            dcFpsLimit = fpsLimit
            dcBrightness = brightness
            checkShouldLimitFps(context, true)
        }
        ConfigProvider.observeChange(context, DC_COMPAT_CONFIG_URI, updateConfig)
        initReflections(context)
        updateConfig()
        startObserving(context)
    }

    private fun setHardwareDcEnabled(enable: Boolean) {
        // the "dc_back_light" setting only changes the UI, this method is what actually behind the scene
        displayFeatureMan?.callMethod<Unit>("setScreenEffect", 20, if (enable) 1 else 0)
    }

    private fun initReflections(context: Context) {
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
        displayFeatureMan = XposedHelpers.findClassIfExists(
            "miui.hardware.display.DisplayFeatureManager",
            context.classLoader
        )?.callStaticMethod("getInstance")
    }

    private fun startObserving(context: Context) {
        val observerHandler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(observerHandler) {
            override fun onChange(selfChange: Boolean) {
                checkShouldLimitFps(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor("dc_back_light"), false, observer)
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer)
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor("min_refresh_rate"), false, observer)
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // restore DC after user unlocked keyguard
                observerHandler.postDelayed({ checkShouldLimitFps(context, true) }, 500)
            }
        }, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    private fun updateFpsLimit(retry: Int = 10) {
        if (Log.isLoggable(LOG_TAG, LOG_LEVEL)) {
            XposedBridge.log("[$LOG_TAG] updateFpsLimit. " +
                    "shouldLimit=$shouldLimitFps, limit=$dcFpsLimit")
        }
        val shouldLimitFps = shouldLimitFps ?: return
        updateHandler.removeCallbacksAndMessages(null)
        // firstly, restore to 60Hz, refresh rate has to be 60 when enabling DC
        if (shouldLimitFps) setMinRefreshRate(60)
        callback.setFpsLimit(if (shouldLimitFps) 60 else null)
        updateCurrentFps()
        setHardwareDcEnabled(dcEnabled)
        // then, enable DC and set to target fps
        updateHandler.postDelayed({
            callback.setFpsLimit(if (shouldLimitFps) dcFpsLimit else null)
            if (!updateCurrentFps() && retry > 0) {
                updateHandler.postDelayed({ updateFpsLimit(retry - 1) }, ACTION_DELAY)
            }
            setHardwareDcEnabled(dcEnabled)
        }, ACTION_DELAY)
    }

    private fun checkShouldLimitFps(context: Context, forceUpdate: Boolean = false) {
        val dcEnabled = Settings.System.getInt(context.contentResolver, "dc_back_light", 0) == 1
        val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        minRefreshRate = Settings.System.getInt(context.contentResolver, "min_refresh_rate", 60)
        this.dcEnabled = dcEnabled
        val wasLimit = shouldLimitFps
        shouldLimitFps = dcEnabled && brightness <= dcBrightness && dcFpsLimit > 0
        if (wasLimit != shouldLimitFps || forceUpdate) {
            updateHandler.post { updateFpsLimit() }
        }
        if (Log.isLoggable(LOG_TAG, LOG_LEVEL)) {
            XposedBridge.log("[$LOG_TAG] checkShouldLimitFps. dcEnabled=$dcEnabled, " +
                    "brightness=$brightness, dcBrightness=$dcBrightness, " +
                    "dcFpsLimit=$dcFpsLimit, forceUpdate=$forceUpdate, " +
                    "wasLimit=$wasLimit, shouldLimitFps=$shouldLimitFps")
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
        // make sure there's change, void getting ignored for no change
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

    private fun setMinRefreshRate(fps: Int) {
        val context = contextRef.get() ?: return
        Settings.System.putInt(context.contentResolver, "min_refresh_rate", fps)
    }

    fun beforeApplyFps(fps: Int) {
        if (!isDcIncompatible) return
        // set min refresh rate to target fps, avoid refresh rate change due to backlight bug
        if (minRefreshRate != fps) {
            setMinRefreshRate(fps)
        }
    }
}