package com.pesutgo.app.core

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)

    var lastUrl: String?
        get() = prefs.getString(AppConfig.KEY_LAST_URL, null)
        set(value) = prefs.edit().putString(AppConfig.KEY_LAST_URL, value).apply()

    var lastRole: String?
        get() = prefs.getString(AppConfig.KEY_LAST_ROLE, null)
        set(value) = prefs.edit().putString(AppConfig.KEY_LAST_ROLE, value).apply()

    var wasAuthenticated: Boolean
        get() = prefs.getBoolean(AppConfig.KEY_WAS_AUTHENTICATED, false)
        set(value) = prefs.edit().putBoolean(AppConfig.KEY_WAS_AUTHENTICATED, value).apply()

    fun clearLocalAuthState() {
        prefs.edit()
            .remove(AppConfig.KEY_LAST_URL)
            .remove(AppConfig.KEY_LAST_ROLE)
            .putBoolean(AppConfig.KEY_WAS_AUTHENTICATED, false)
            .apply()
    }
}
