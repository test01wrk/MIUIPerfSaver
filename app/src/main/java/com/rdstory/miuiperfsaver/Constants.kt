package com.rdstory.miuiperfsaver

object Constants {
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

    const val FAKE_PKG_DEFAULT_FPS = "${BuildConfig.APPLICATION_ID}_FAKE_PKG_DEFAULT_FPS"
    const val JOYOSE_PKG = "com.xiaomi.joyose"
    const val JOYOSE_ACTIVITY = ".cloud.LocalCtrlActivity"
    const val START_JOYOSE_CMD = "am start -n $JOYOSE_PKG/$JOYOSE_ACTIVITY"
}