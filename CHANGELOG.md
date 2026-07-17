# Changelog

## [0.1.0] — 2026-07-17

### Added

- Initial release of TunnelKeep Android app
- Native Kotlin WebView client for VS Code Tunnel workspaces
- Foreground service with persistent notification for keep-alive
- Partial CPU wake lock management to reduce suspension during screen-off
- Connectivity monitoring via `ConnectivityManager.NetworkCallback`
- HTTPS-only enforcement — all cleartext and mixed-content traffic blocked
- WebView renderer crash detection and recovery UI
- GitHub/Microsoft OAuth popup handling via system browser
- Multi-file chooser support for uploads via Android's document picker
- Download support via `DownloadManager`
- Configurable workspace URL with edit/reset controls
- Settings screen with URL editing, keep-alive toggle, reconnect, device shortcuts
- Help screen with HyperOS setup guidance and limitations
- Dark Material 3 theme with teal accent (non-Microsoft branding)
- Safe external intent handling
- WebView history-aware back navigation
- Notification tap returns to workspace
- State preservation across config changes and process recreation
- Unit tests for URL config, keep-alive, and reconnect logic
- GitHub Actions CI: lint, test, build (debug + unsigned release APK)
- Dependabot configuration for dependency updates
- Apache 2.0 license
- Documentation: README, ARCHITECTURE, TESTING, CHANGELOG, WORKLOG, PRIOR_ART
