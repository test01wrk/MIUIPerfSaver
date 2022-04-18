package com.rdstory.miuiperfsaver

import android.app.Application
import android.util.Log
import androidx.annotation.Keep
import com.rdstory.miuiperfsaver.Constants.LOG_TAG

class MainApplication: Application() {
    companion object {
        @Keep
        fun getActiveXposedVersion(): Int {
            Log.d(LOG_TAG, "Xposed framework is inactive.")
            return -1
        }
    }

    override fun onCreate() {
        super.onCreate()
        Configuration.init(this)
    }
}