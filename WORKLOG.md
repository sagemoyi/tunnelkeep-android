# Worklog

## 2026-07-17 — v0.1.0 implementation

- **Build system**: Gradle 8.10.2 with AGP 8.7.3, Kotlin 2.0.21, AndroidX, Material 3
- **Activity stack**: `MainActivity` (WebView), `SettingsActivity`, `HelpActivity` — singleTask launch mode for notification tap return
- **Foreground service**: `TunnelKeepService` with `specialUse` type for Android 14+, partial wake lock, connectivity listener
- **WebView**: `TunnelWebView` subclass with HTTPS-only enforcement, renderer crash recovery, auth popup delegation, file chooser, download support
- **URL management**: `WebViewConfig` backed by SharedPreferences; validates HTTPS-only; reset to default
- **Keep-alive**: `KeepAliveManager` persists toggle state; service manages wake lock with periodic health check
- **Connectivity**: `ConnectivityMonitor` using `ConnectivityManager.NetworkCallback`
- **Security**: `network_security_config.xml` denies cleartext; `usesCleartextTraffic="false"`; no `allowFileAccess` or `allowContentAccess`; no `addJavascriptInterface`
- **UI**: Dark Material 3 theme with teal primary, adaptive icon, notification channel
- **Settings**: Workspace URL edit/reset, keep-alive toggle, reconnect button, Android app info + battery optimization shortcuts
- **Help**: Version display, HyperOS/HyperOS setup steps (no-restrictions, autostart, lock in recents), disclaimer, limitations
- **Tests**: `WebViewConfigTest` (URL validation, set/get/reset), `KeepAliveManagerTest` (enable/disable), `ReconnectStateLogicTest` (reconnect/state/crash logic)
- **CI**: GitHub Actions workflow with lint, test, and debug/release APK build jobs
- **Dependabot**: Weekly Gradle and GitHub Actions updates
- **Docs**: `README.md`, `docs/ARCHITECTURE.md`, `docs/TESTING.md`, `docs/PRIOR_ART.md`

### Build status on this host

- User-local JDK 17 and Android SDK 35 were used; the ARM64 host runs the x86_64 AAPT2 binary through QEMU.
- Main-agent release gate after review: `./gradlew clean test lint assembleDebug assembleRelease` passed.
- Unit tests: 39 per build variant (78 executions total), 0 failures.
- Debug and unsigned release APKs were produced successfully.
- Review fixes included real Settings→service wiring, notification-channel application setup, preserving WebView activity during backgrounding, stronger URL validation, authenticated downloads, renderer termination handling, service cleanup, and corrected project/version metadata.

### Device-only risks

- Real Foreground Service behavior under HyperOS battery optimization varies by ROM version
- WebView renderer suspend during long screen-off periods — partial wake lock helps but does not guarantee active rendering
- OAuth flow requires user to return to app after browser-based login
- Notification permission must be granted on Android 13+ for keep-alive to function
