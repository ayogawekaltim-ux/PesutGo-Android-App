# PesutGo Android

Official Android shell for **PesutGo** using **native Kotlin + WebView + persistent WebView session + native foreground location service**.

Package: `com.pesutgo.app`

Website: `https://www.pesutgo.com`

## Architecture

```text
Native Android Kotlin
        |
        +-- Native Splash
        |
        +-- Native Home
        |      +-- Login Mitra
        |      +-- Login Customer
        |      +-- Pesanan
        |      +-- Aktivitas
        |      +-- Profil
        |
        +-- WebView
        |      +-- Persistent cookies / storage
        |      +-- File upload / camera
        |      +-- Web geolocation
        |      +-- JavaScript bridge
        |
        +-- Foreground Location Service
               +-- Real device GPS updates
               +-- Native → WebView location bridge
```

## Important: persistent login

The app does **not** clear WebView cookies, cache, local storage, or session data during normal startup, Activity recreation, backgrounding, or app close.

The app stores only non-secret local state such as the last WebView URL and the last role. It does **not** store the user's password.

The server remains authoritative. If the PesutGo server expires a session, the website can require login again.

Explicit logout clears the WebView authentication state and returns to the native home screen.

## Login URLs

Provider:

`https://pesutgo.com/login?role=provider&redirect=%2Fprovider%2Fkyc`

Customer:

`https://pesutgo.com/login?role=customer&redirect=%2Fcustomer%2Forder`

Bottom navigation:

- Pesanan: `https://pesutgo.com/customer/order`
- Aktivitas: `https://pesutgo.com/customer/history`
- Profil: `https://pesutgo.com/customer/profile`

## Native GPS

Website JavaScript can call:

```javascript
PesutGoApp.startLocationTracking();
PesutGoApp.stopLocationTracking();
PesutGoApp.logout();
```

The bridge is exposed only to the trusted PesutGo WebView context and the native app validates location permissions before starting the foreground service.

### Background tracking

The provider/customer web page can request native tracking while the app is visible. Android then keeps the location work in a foreground service when the app moves to the background.

The service uses Android's `location` foreground-service type and a visible low-priority notification.

### Native GPS ↔ WebView logic

PesutGo does **not** have a separate location API. Therefore the Android app does not invent, guess, or call a fake GPS endpoint.

The native foreground service obtains real device GPS and caches the latest coordinate locally. The trusted PesutGo WebView receives those coordinates through the JavaScript bridge. The adapter also provides the native coordinates through `navigator.geolocation.getCurrentPosition()` and `navigator.geolocation.watchPosition()`.

This means the website remains the actual PesutGo application and remains responsible for its own existing server-side logic. If the website already sends browser geolocation to its PHP/server logic, the native GPS values can flow through that existing website logic.

The bridge exposes:

```javascript
PesutGoApp.startLocationTracking();
PesutGoApp.stopLocationTracking();
PesutGoApp.requestNativeLocation();
PesutGoApp.getLastLocationJson();
PesutGoApp.logout();
PesutGoApp.getAppVersion();
```

There is no `PESUTGO_LOCATION_API_URL` setting in this project.

## WebView security

- HTTPS-only network security config.
- Safe Browsing enabled where supported.
- JavaScript enabled because PesutGo requires it.
- DOM storage enabled.
- File access disabled; content URI access remains available for upload.
- Main PesutGo hosts are trusted: `pesutgo.com` and `www.pesutgo.com`.
- Untrusted web URLs are opened externally.
- No API secrets are embedded in the app.

## File upload and KYC

The WebView supports HTML file chooser flows and camera capture where Android permissions allow it. Captured files are stored in the app's external pictures area through `FileProvider` and passed to the WebView as content URIs.

## Build locally

Requirements:

- Android Studio with JDK 17
- Android SDK Platform 36
- Gradle 8.13 (AGP 8.13.2)

Open the project in Android Studio and sync Gradle.

This generated repository also contains `gradlew`/`gradlew.bat` bootstrap scripts. The current environment could not vendor the official `gradle-wrapper.jar`, so the GitHub workflow provisions Gradle 8.13 directly; Android Studio can sync the project normally.

Release APK:

```bash
gradle :app:assembleRelease
```

Release AAB:

```bash
gradle :app:bundleRelease
```

## GitHub Actions

`.github/workflows/android-build.yml` builds the release APK with JDK 17 and Gradle 8.13 and publishes it as a workflow artifact.

Signing keys are intentionally not stored in GitHub. Add a proper encrypted GitHub secret/keystore workflow before publishing a signed Play Store AAB.

## App Links

The manifest contains HTTP App Link intent filters for `pesutgo.com` and `www.pesutgo.com`.

For verified App Links, the production website must also publish a matching:

`https://www.pesutgo.com/.well-known/assetlinks.json`

containing the release application ID and signing certificate fingerprint.

## Testing checklist

- [x] Native home
- [x] PesutGo logo resource
- [x] Provider login URL
- [x] Customer login URL
- [x] Persistent WebView cookies/storage
- [x] No startup cookie clearing
- [x] Last URL persistence
- [x] Explicit logout flow
- [x] WebView back navigation
- [x] File picker
- [x] Camera permission flow
- [x] Web geolocation permission
- [x] Native foreground location service
- [x] Location notification
- [x] JavaScript bridge
- [x] HTTPS-only network security
- [x] GitHub Actions build workflow

### Production dependency to verify

The native location service is intentionally **not wired to an invented PesutGo server endpoint**. PesutGo has no separate location API in this project; GPS is delivered to the real website WebView instead.


## GPS architecture

This project treats `https://www.pesutgo.com` as the actual application UI inside WebView. Android provides the device GPS through a native Foreground Service. Location updates are bridged back into the trusted PesutGo WebView and exposed through `navigator.geolocation` plus the `PesutGoApp` JavaScript bridge.

There is intentionally **no invented PesutGo REST/API endpoint**. The native layer captures and caches GPS locally, then supplies it to the website. If the existing PesutGo website already sends location to its own PHP/server logic, that existing logic can consume the browser geolocation stream.

The bridge supports:
- `PesutGoApp.startLocationTracking()`
- `PesutGoApp.stopLocationTracking()`
- `PesutGoApp.requestNativeLocation()`
- `PesutGoApp.getLastLocationJson()`
- `PesutGoApp.logout()`
- `PesutGoApp.getAppVersion()`

The app does not store passwords and does not clear cookies/cache on startup. Session data remains under WebView/Android storage until the user explicitly logs out or the server session expires.
