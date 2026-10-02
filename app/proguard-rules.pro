# PesutGo release rules.
# Keep the JavaScript bridge class and its methods discoverable by WebView.
-keepclassmembers class com.pesutgo.app.web.JavaScriptBridge {
    <methods>;
}
-keepattributes JavascriptInterface
