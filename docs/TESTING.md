# Testing

## Unit Tests

Unit tests target pure logic and easily-mocked Android dependencies:

### WebViewConfigTest

Tests for URL configuration management:
- Default URL retrieval when nothing is stored
- Valid HTTPS URL set/get
- Fallback to default when invalid URL is stored
- URL validation (HTTPS prefix, no spaces/newlines, minimum length)
- URL set rejection for HTTP, malformed, empty
- URL reset behavior
- Custom URL detection (`isCustomUrl`)

### KeepAliveManagerTest

Tests for keep-alive toggle preference:
- Default state (disabled)
- Enable/disable persistence
- Toggle transitions

### ReconnectStateLogicTest

Pure-logic tests for reconnect behavior:
- URL validation (mirrors WebViewConfig)
- Reconnect uses validated URL
- Fallback to default on invalid URL
- Post-crash URL preservation
- Blind reload avoidance (design intent)

## Running Tests

```bash
# Unit tests only (no device needed)
./gradlew test

# Reports: app/build/reports/tests/testDebugUnitTest/
```

Tests use Mockito for Android dependency mocking (Context, SharedPreferences). No Robolectric dependency to keep the build slim.

## Lint

```bash
# Run Android lint checks
./gradlew lint

# Reports: app/build/reports/lint-results*.html
```

## Instrumentation Tests

Android instrumentation tests require a device or emulator. These are scaffolded but not yet implemented:

```bash
# Requires connected device/emulator
./gradlew connectedAndroidTest
```

Planned instrumentation tests:
- WebView loads default URL successfully
- Settings screen navigation
- Keep-alive toggle starts/stops foreground service
- Renderer crash UI appears (mock with `loadUrl("chrome://crash")`)

## Real-Device Testing Checklist

Required for validating behavior that cannot be simulated in unit tests:

### Setup
- [ ] Install debug APK on device running HyperOS/Xiaomi
- [ ] Grant notification permission (Android 13+)
- [ ] Grant battery optimization exception (Settings → Apps → TunnelKeep → Battery → No restrictions)
- [ ] Enable autostart (Settings → Apps → TunnelKeep → Autostart)

### Features
- [ ] Default URL loads VS Code Tunnel workspace
- [ ] Custom URL is accepted and loaded
- [ ] Invalid URL is rejected with error
- [ ] HTTP URL is blocked
- [ ] Back button navigates WebView history
- [ ] Back button exits app from root page

### Keep-alive
- [ ] Toggle creates foreground service notification
- [ ] Notification shows "Maintaining connection" text
- [ ] Screen off for 5 minutes → re-open → workspace still loaded
- [ ] Stop via notification action removes notification and service
- [ ] Notification tap opens app to workspace

### Connectivity
- [ ] Toggle airplane mode → workspace shows reconnect state
- [ ] Re-enable connectivity → workspace reconnects via VS Code
- [ ] WiFi → cellular handoff → workspace recovers

### Downloads & Uploads
- [ ] File download triggers DownloadManager notification
- [ ] File upload opens system file picker
- [ ] Selected file is uploaded to workspace

### Auth
- [ ] GitHub login opens in system browser
- [ ] Microsoft login opens in system browser
- [ ] Returning to app after auth loads workspace

### Edge Cases
- [ ] Low memory scenario — app handles gracefully
- [ ] Force-stop → relaunch → URL preserved
- [ ] App switcher → lock app → survives memory pressure
- [ ] Device reboot → autostart (if configured) → keep-alive resumes

## Signing for Release

Release APKs built by CI are unsigned. To sign for distribution:

1. Generate a keystore:
   ```bash
   keytool -genkey -v -keystore release.jks -alias tunnelkeep \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Create `keystore.properties`:
   ```properties
   storeFile=release.jks
   storePassword=your_store_password
   keyAlias=tunnelkeep
   keyPassword=your_key_password
   ```

3. Add signing config to `app/build.gradle.kts` and reference `keystore.properties`.

4. Never commit `release.jks` or `keystore.properties` to version control.
