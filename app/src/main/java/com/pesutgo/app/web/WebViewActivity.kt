package com.pesutgo.app.web

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.pesutgo.app.BuildConfig
import com.pesutgo.app.core.AppConfig
import com.pesutgo.app.core.DomainPolicy
import com.pesutgo.app.core.SessionManager
import com.pesutgo.app.location.LocationForegroundService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WebViewActivity : ComponentActivity(), JavaScriptBridge.Callbacks {

    private lateinit var webView: WebView
    private lateinit var sessionManager: SessionManager

    private var role: String = AppConfig.ROLE_CUSTOMER

    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingGeoOrigin: String? = null

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null

    private var requestedPermissionAction: (() -> Unit)? = null
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var pendingFileChooserParams: WebChromeClient.FileChooserParams? = null

    private var locationReceiverRegistered = false
    private var nextNativeWatchId = 1

    private val nativeLocationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != LocationForegroundService.ACTION_LOCATION) return

            val latitude = intent.getDoubleExtra("latitude", Double.NaN)
            val longitude = intent.getDoubleExtra("longitude", Double.NaN)

            if (latitude.isNaN() || longitude.isNaN()) return

            val accuracy = intent.getFloatExtra("accuracy", 0f)
            val speed = intent.getFloatExtra("speed", 0f)
            val bearing = intent.getFloatExtra("bearing", 0f)
            val timestamp = intent.getLongExtra("timestamp", 0L)

            deliverNativeLocationToWeb(
                latitude,
                longitude,
                accuracy,
                speed,
                bearing,
                timestamp
            )
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->

        val granted =
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            val action = requestedPermissionAction
            requestedPermissionAction = null
            action?.invoke()
        } else {
            requestedPermissionAction = null

            pendingGeoCallback?.invoke(
                pendingGeoOrigin,
                false,
                false
            )

            pendingGeoCallback = null
            pendingGeoOrigin = null

            Toast.makeText(
                this,
                "Izin lokasi diperlukan untuk layanan PesutGo.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        val action = requestedPermissionAction
        requestedPermissionAction = null
        action?.invoke()
    }

    /**
     * Camera permission menggunakan Activity Result API.
     *
     * Ini menggantikan onRequestPermissionsResult()
     * yang menyebabkan error pada Kotlin/AndroidX terbaru.
     */
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->

        val webPermissionRequest = pendingWebPermissionRequest
        pendingWebPermissionRequest = null

        if (webPermissionRequest != null) {
            if (granted) {
                webPermissionRequest.grant(
                    arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                )
            } else {
                webPermissionRequest.deny()
            }

            return@registerForActivityResult
        }

        if (granted) {
            launchFileChooser(pendingFileChooserParams)
        } else {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            cameraUri = null
            pendingFileChooserParams = null
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->

        val callback = filePathCallback ?: return@registerForActivityResult

        val results = when {
            result.resultCode != Activity.RESULT_OK -> null

            result.data?.data != null ->
                arrayOf(result.data!!.data!!)

            cameraUri != null ->
                arrayOf(cameraUri!!)

            else -> null
        }

        callback.onReceiveValue(results)

        filePathCallback = null
        cameraUri = null
        pendingFileChooserParams = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)

        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        setContentView(webView)

        sessionManager = SessionManager(this)

        role =
            intent.getStringExtra(EXTRA_ROLE)
                ?: sessionManager.lastRole
                ?: AppConfig.ROLE_CUSTOMER

        val url =
            intent.getStringExtra(EXTRA_URL)
                ?: sessionManager.lastUrl
                ?: AppConfig.CUSTOMER_LOGIN_URL

        configureWebView()
        registerNativeLocationReceiver()

        webView.loadUrl(url)
    }

    private fun configureWebView() {

        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.allowContentAccess = true
        settings.allowFileAccess = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = false

        settings.userAgentString =
            settings.userAgentString + " PesutGoAndroid/1.0.0"

        CookieManager.getInstance().setAcceptCookie(true)

        CookieManager.getInstance()
            .setAcceptThirdPartyCookies(webView, true)

        if (BuildConfig.ENABLE_WEBVIEW_DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        if (
            WebViewFeature.isFeatureSupported(
                WebViewFeature.START_SAFE_BROWSING
            )
        ) {
            WebViewCompat.startSafeBrowsing(this, null)
        }

        webView.addJavascriptInterface(
            JavaScriptBridge(this, this),
            "PesutGoApp"
        )

        webView.webViewClient = PesutGoWebViewClient()
        webView.webChromeClient = PesutGoWebChromeClient()
    }

    private inner class PesutGoWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {

            val uri = request.url

            if (uri.scheme == "http" || uri.scheme == "https") {

                if (DomainPolicy.isTrusted(uri)) {
                    return false
                }

                openExternal(uri)
                return true
            }

            openExternal(uri)
            return true
        }

        override fun onPageStarted(
            view: WebView,
            url: String?,
            favicon: Bitmap?
        ) {
            super.onPageStarted(view, url, favicon)

            if (url != null && isExplicitLogoutUrl(url)) {
                requestLogout()
            }
        }

        override fun onPageFinished(
            view: WebView,
            url: String?
        ) {
            super.onPageFinished(view, url)

            CookieManager.getInstance().flush()

            installNativeGeolocationAdapter()

            val current = url ?: return

            if (DomainPolicy.isTrusted(Uri.parse(current))) {

                if (
                    !isLoginPage(current) &&
                    !isLogoutUrl(current)
                ) {

                    sessionManager.lastUrl = current
                    sessionManager.lastRole = role
                    sessionManager.wasAuthenticated = true

                } else if (
                    sessionManager.wasAuthenticated &&
                    isLoginPage(current)
                ) {
                    requestLogout()
                }
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {

            if (request.isForMainFrame) {
                Toast.makeText(
                    this@WebViewActivity,
                    "Koneksi PesutGo bermasalah. Silakan coba lagi.",
                    Toast.LENGTH_LONG
                ).show()
            }

            super.onReceivedError(view, request, error)
        }
    }

    private inner class PesutGoWebChromeClient : WebChromeClient() {

        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback?
        ) {

            if (
                origin == null ||
                !DomainPolicy.isTrusted(Uri.parse(origin))
            ) {

                callback?.invoke(
                    origin,
                    false,
                    false
                )

                return
            }

            if (hasLocationPermission()) {

                callback?.invoke(
                    origin,
                    true,
                    false
                )

                requestNotificationPermissionIfNeeded {
                    startLocationService()
                }

                return
            }

            pendingGeoOrigin = origin
            pendingGeoCallback = callback

            requestLocationPermission {

                val geoCallback = pendingGeoCallback
                val geoOrigin = pendingGeoOrigin

                pendingGeoCallback = null
                pendingGeoOrigin = null

                geoCallback?.invoke(
                    geoOrigin,
                    true,
                    false
                )

                requestNotificationPermissionIfNeeded {
                    startLocationService()
                }
            }
        }

        override fun onPermissionRequest(
            request: PermissionRequest
        ) {

            runOnUiThread {

                val requested =
                    request.resources
                        .filter {
                            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                        }
                        .toTypedArray()

                if (requested.isEmpty()) {
                    request.deny()
                    return@runOnUiThread
                }

                if (hasCameraPermission()) {
                    request.grant(requested)
                } else {
                    pendingWebPermissionRequest = request

                    cameraPermissionLauncher.launch(
                        Manifest.permission.CAMERA
                    )
                }
            }
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {

            this@WebViewActivity.filePathCallback
                ?.onReceiveValue(null)

            this@WebViewActivity.filePathCallback =
                filePathCallback

            pendingFileChooserParams = fileChooserParams

            if (
                fileChooserParams?.isCaptureEnabled == true &&
                !hasCameraPermission()
            ) {

                cameraPermissionLauncher.launch(
                    Manifest.permission.CAMERA
                )

            } else {
                launchFileChooser(fileChooserParams)
            }

            return true
        }

        override fun onJsAlert(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
        ): Boolean {

            Toast.makeText(
                this@WebViewActivity,
                message ?: "PesutGo",
                Toast.LENGTH_LONG
            ).show()

            result?.cancel()

            return true
        }
    }

    private fun launchFileChooser(
        params: WebChromeClient.FileChooserParams?
    ) {

        val accept =
            params?.acceptTypes
                ?.joinToString(",")
                ?.takeIf { it.isNotBlank() }
                ?: "*/*"

        val pick = Intent(
            Intent.ACTION_OPEN_DOCUMENT
        ).apply {

            addCategory(
                Intent.CATEGORY_OPENABLE
            )

            type =
                if (accept.contains("image/")) {
                    "image/*"
                } else {
                    "*/*"
                }
        }

        val capture = tryCreateCameraIntent()

        val chooser = Intent(
            Intent.ACTION_CHOOSER
        ).apply {

            putExtra(
                Intent.EXTRA_INTENT,
                pick
            )

            if (capture != null) {
                putExtra(
                    Intent.EXTRA_INITIAL_INTENTS,
                    arrayOf(capture)
                )
            }

            putExtra(
                Intent.EXTRA_TITLE,
                "Pilih dokumen PesutGo"
            )
        }

        try {

            filePickerLauncher.launch(chooser)

        } catch (_: ActivityNotFoundException) {

            filePickerLauncher.launch(pick)
        }
    }

    private fun tryCreateCameraIntent(): Intent? {

        if (!hasCameraPermission()) {
            return null
        }

        val photoFile = File.createTempFile(
            "pesutgo_${
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.US
                ).format(Date())
            }_",
            ".jpg",
            getExternalFilesDir(
                Environment.DIRECTORY_PICTURES
            )
        )

        cameraUri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            photoFile
        )

        return Intent(
            MediaStore.ACTION_IMAGE_CAPTURE
        ).apply {

            putExtra(
                MediaStore.EXTRA_OUTPUT,
                cameraUri
            )

            addFlags(
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun openExternal(uri: Uri) {

        try {

            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                )
            )

        } catch (_: ActivityNotFoundException) {

            Toast.makeText(
                this,
                "Tidak ada aplikasi yang dapat membuka tautan ini.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun registerNativeLocationReceiver() {

        if (locationReceiverRegistered) {
            return
        }

        val filter = IntentFilter(
            LocationForegroundService.ACTION_LOCATION
        )

        ContextCompat.registerReceiver(
            this,
            nativeLocationReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        locationReceiverRegistered = true
    }

    private fun unregisterNativeLocationReceiver() {

        if (!locationReceiverRegistered) {
            return
        }

        try {
            unregisterReceiver(
                nativeLocationReceiver
            )
        } catch (_: IllegalArgumentException) {
            // Already unregistered by the framework.
        }

        locationReceiverRegistered = false
    }

    private fun installNativeGeolocationAdapter() {

        val currentUrl = webView.url ?: return

        if (!DomainPolicy.isTrusted(Uri.parse(currentUrl))) {
            return
        }

        val script = """
            (function() {
              if (window.__pesutGoNativeGeoInstalled) return;

              window.__pesutGoNativeGeoInstalled = true;
              window.__pesutGoNativeGeoWatches = {};
              window.__pesutGoNativeGeoOneShots = [];
              window.__pesutGoNativeGeoNextId = 1;
              window.__pesutGoNativeGeoLast = null;

              function normalize(nativeLocation) {
                if (!nativeLocation) return null;

                return {
                  coords: {
                    latitude: Number(nativeLocation.latitude),
                    longitude: Number(nativeLocation.longitude),
                    accuracy: Number(nativeLocation.accuracy || 0),
                    altitude: null,
                    altitudeAccuracy: null,
                    heading: nativeLocation.heading == null
                      ? null
                      : Number(nativeLocation.heading),
                    speed: nativeLocation.speed == null
                      ? null
                      : Number(nativeLocation.speed)
                  },
                  timestamp: Number(
                    nativeLocation.timestamp || Date.now()
                  )
                };
              }

              window.__PesutGoNativeLocation = function(nativeLocation) {

                var position = normalize(nativeLocation);

                if (!position) return;

                window.__pesutGoNativeGeoLast = position;

                var oneShots =
                  window.__pesutGoNativeGeoOneShots.splice(0);

                oneShots.forEach(function(callback) {
                  try {
                    callback(position);
                  } catch (_) {}
                });

                Object.keys(
                  window.__pesutGoNativeGeoWatches
                ).forEach(function(id) {

                  try {
                    window.__pesutGoNativeGeoWatches[id]
                      .success(position);
                  } catch (_) {}
                });

                if (
                  oneShots.length > 0 &&
                  Object.keys(
                    window.__pesutGoNativeGeoWatches
                  ).length === 0
                ) {

                  try {
                    window.PesutGoApp
                      .stopLocationTracking();
                  } catch (_) {}
                }
              };

              window.__PesutGoNativeLocationError =
                function(code, message) {

                  var error = {
                    code: code || 2,
                    message:
                      message ||
                      'Lokasi tidak tersedia.'
                  };

                  Object.keys(
                    window.__pesutGoNativeGeoWatches
                  ).forEach(function(id) {

                    try {

                      if (
                        window.__pesutGoNativeGeoWatches[id]
                          .error
                      ) {

                        window.__pesutGoNativeGeoWatches[id]
                          .error(error);
                      }

                    } catch (_) {}
                  });
                };

              window.__pesutGoNativeGeoRequest =
                function(success, error, options) {

                  try {

                    var maxAge =
                      options &&
                      typeof options.maximumAge === 'number'
                        ? options.maximumAge
                        : 0;

                    var cached =
                      window.PesutGoApp
                        .getLastLocationJson();

                    if (cached) {

                      var parsed =
                        JSON.parse(cached);

                      var position =
                        normalize(parsed);

                      var age =
                        Date.now() -
                        Number(
                          position &&
                          position.timestamp || 0
                        );

                      if (
                        position &&
                        maxAge > 0 &&
                        age >= 0 &&
                        age <= maxAge
                      ) {

                        success(position);

                        if (
                          Object.keys(
                            window.__pesutGoNativeGeoWatches
                          ).length === 0
                        ) {
                          return;
                        }
                      }
                    }

                  } catch (_) {}

                  window.__pesutGoNativeGeoOneShots
                    .push(success);

                  try {

                    window.PesutGoApp
                      .requestNativeLocation();

                  } catch (_) {

                    if (error) {
                      error({
                        code: 2,
                        message:
                          'Native GPS tidak tersedia.'
                      });
                    }
                  }
                };

              navigator.geolocation.getCurrentPosition =
                function(success, error, options) {

                  window.__pesutGoNativeGeoRequest(
                    success,
                    error,
                    options
                  );
                };

              navigator.geolocation.watchPosition =
                function(success, error, options) {

                  var id =
                    window.__pesutGoNativeGeoNextId++;

                  window.__pesutGoNativeGeoWatches[id] = {
                    success: success,
                    error: error
                  };

                  window.__pesutGoNativeGeoRequest(
                    success,
                    error,
                    options
                  );

                  return id;
                };

              navigator.geolocation.clearWatch =
                function(id) {

                  delete window.__pesutGoNativeGeoWatches[
                    String(id)
                  ];

                  try {

                    if (
                      Object.keys(
                        window.__pesutGoNativeGeoWatches
                      ).length === 0 &&
                      window.PesutGoApp
                    ) {

                      window.PesutGoApp
                        .stopLocationTracking();
                    }

                  } catch (_) {}
                };

            })();
        """.trimIndent()

        webView.evaluateJavascript(
            script,
            null
        )
    }

    private fun deliverNativeLocationToWeb(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        speed: Float,
        bearing: Float,
        timestamp: Long
    ) {

        if (isFinishing || isDestroyed) {
            return
        }

        val payload = """
            {
                "latitude":$latitude,
                "longitude":$longitude,
                "accuracy":$accuracy,
                "speed":$speed,
                "heading":$bearing,
                "timestamp":$timestamp
            }
        """.trimIndent()

        val js =
            "window.__PesutGoNativeLocation && " +
                "window.__PesutGoNativeLocation($payload);"

        webView.post {
            webView.evaluateJavascript(
                js,
                null
            )
        }
    }

    override fun requestStartLocation() {

        requestLocationPermission {

            requestNotificationPermissionIfNeeded {
                startLocationService()
            }
        }
    }

    override fun requestStopLocation() {

        stopService(
            Intent(
                this,
                LocationForegroundService::class.java
            )
        )
    }

    override fun requestLogout() {

        val cm = CookieManager.getInstance()

        cm.removeAllCookies {

            cm.flush()

            webView.clearHistory()
            webView.clearCache(false)

            webView.evaluateJavascript(
                "try{localStorage.clear();sessionStorage.clear();}catch(e){}",
                null
            )

            sessionManager.clearLocalAuthState()

            stopLocationService()

            finish()
        }
    }

    private fun startLocationService() {

        if (!hasLocationPermission()) {
            return
        }

        val intent =
            Intent(
                this,
                LocationForegroundService::class.java
            ).setAction(
                LocationForegroundService.ACTION_START
            )

        try {

            ContextCompat.startForegroundService(
                this,
                intent
            )

        } catch (_: SecurityException) {

            Toast.makeText(
                this,
                "Lokasi belum memenuhi izin Android untuk tracking.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopLocationService() {

        stopService(
            Intent(
                this,
                LocationForegroundService::class.java
            )
        )
    }

    private fun requestNotificationPermissionIfNeeded(
        after: () -> Unit
    ) {

        if (
            android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            after()
            return
        }

        requestedPermissionAction = after

        notificationPermissionLauncher.launch(
            Manifest.permission.POST_NOTIFICATIONS
        )
    }

    private fun requestLocationPermission(
        after: () -> Unit
    ) {

        requestedPermissionAction = after

        val fine =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarse =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (fine || coarse) {

            requestedPermissionAction = null

            after()

            return
        }

        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun hasLocationPermission(): Boolean {

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||

            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    override fun onStart() {

        super.onStart()

        registerNativeLocationReceiver()
    }

    override fun onPause() {

        CookieManager.getInstance().flush()

        super.onPause()
    }

    override fun onDestroy() {

        unregisterNativeLocationReceiver()

        CookieManager.getInstance().flush()

        webView.removeJavascriptInterface(
            "PesutGoApp"
        )

        webView.destroy()

        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {

        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    private fun isLoginPage(
        url: String
    ): Boolean =
        Uri.parse(url)
            .path
            ?.trimEnd('/') == "/login"

    private fun isLogoutUrl(
        url: String
    ): Boolean {

        val uri = Uri.parse(url)

        return uri.path?.contains(
            "logout",
            ignoreCase = true
        ) == true ||

            uri.getQueryParameter("logout") == "1"
    }

    private fun isExplicitLogoutUrl(
        url: String
    ): Boolean =
        isLogoutUrl(url)

    companion object {

        const val EXTRA_URL = "extra_url"

        const val EXTRA_ROLE = "extra_role"
    }
}
