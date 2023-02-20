package com.rdstory.miuiperfsaver

import android.util.Log

object Constants {
    val LOG_LEVEL = if (BuildConfig.DEBUG) Log.INFO else Log.DEBUG
    const val LOG_TAG = "MIUIPerfSaver"

    const val SETTINGS_SP_KEY = "settings"
    const val SORT_ORDER_LABEL = 0
    const val SORT_ORDER_PACKAGE_NAME = 1
    const val SORT_ORDER_INSTALL_TIME = 2
    const val SORT_ORDER_UPDATE_TIME = 3
    const val PREF_KEY_SORT_ORDER = "preference_sort_order"
    const val PREF_KEY_SHOW_PERF_SAVED_FIRST = "preference_show_saved_first"
    const val PREF_KEY_SHOW_SYSTEM = "preference_show_system"
    const val PREF_KEY_SAVED_APP_LIST = "preference_saved_app_list"
    const val PREF_KEY_JOYOSE_PROFILE_RULE = "preference_joyose_profile_rule"
    const val PREF_KEY_DC_FPS_LIMIT = "preference_dc_fps_limit"
    const val PREF_KEY_DC_BRIGHTNESS = "preference_dc_brightness"

    const val FAKE_PKG_DEFAULT_FPS = "${BuildConfig.APPLICATION_ID}_FAKE_PKG_DEFAULT_FPS"
    const val FAKE_PKG_DC_COMPAT = "${BuildConfig.APPLICATION_ID}_FAKE_PKG_DC_COMPAT"
    const val JOYOSE_PKG = "com.xiaomi.joyose"
    const val JOYOSE_ACTIVITY = ".cloud.LocalCtrlActivity"
    const val JOYOSE_SERVICE = ".smartop.SmartOpService"
    const val START_JOYOSE_CMD = "am start -n $JOYOSE_PKG/$JOYOSE_ACTIVITY"
    const val START_JOYOSE_SERVICE_CMD = "am startservice -n $JOYOSE_PKG/$JOYOSE_SERVICE"
    const val STOP_JOYOSE_SERVICE_CMD = "am stopservice -n $JOYOSE_PKG/$JOYOSE_SERVICE"
    const val CLEAR_JOYOSE_CMD = "pm clear $JOYOSE_PKG"
    const val JOYOSE_PROFILE_RULE_BLOCK_ALL = "block_all"
    const val JOYOSE_PROFILE_RULE_RM_APP_LIST = "remove_app_list"
    const val JOYOSE_PROFILE_RULE_RM_APP_DFPS = "remove_app_dynamic_fps"
    const val JOYOSE_PROFILE_RULE_MOD_APP_DFPS = "modify_app_dynamic_fps"
    const val JOYOSE_PROFILE_RULE_KEEP_ALL = "keep_all"

    const val FPS_COOKIE_DEFAULT = 244
    const val FPS_COOKIE_EXCLUDE = 247
}

enum class JoyoseProfileRule(val value: String) {
    BLOCK_ALL(Constants.JOYOSE_PROFILE_RULE_BLOCK_ALL),
    RM_APP_LIST(Constants.JOYOSE_PROFILE_RULE_RM_APP_LIST),
    RM_APP_DFPS(Constants.JOYOSE_PROFILE_RULE_RM_APP_DFPS),
    MOD_APP_DFPS(Constants.JOYOSE_PROFILE_RULE_MOD_APP_DFPS),
    KEEP_ALL(Constants.JOYOSE_PROFILE_RULE_KEEP_ALL)
}