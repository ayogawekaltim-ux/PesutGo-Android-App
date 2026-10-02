package com.pesutgo.app.web

import android.content.Context
import android.webkit.JavascriptInterface
import com.pesutgo.app.BuildConfig
import com.pesutgo.app.location.LocationStore

class JavaScriptBridge(
    private val context: Context,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun requestStartLocation()
        fun requestStopLocation()
        fun requestLogout()
    }

    @JavascriptInterface
    fun startLocationTracking() = callbacks.requestStartLocation()

    @JavascriptInterface
    fun stopLocationTracking() = callbacks.requestStopLocation()

    @JavascriptInterface
    fun requestNativeLocation() = callbacks.requestStartLocation()

    @JavascriptInterface
    fun getLastLocationJson(): String = LocationStore.readJson(context) ?: ""

    @JavascriptInterface
    fun logout() = callbacks.requestLogout()

    @JavascriptInterface
    fun getAppVersion(): String = BuildConfig.VERSION_NAME
}
