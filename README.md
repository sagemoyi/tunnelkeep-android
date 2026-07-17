# TunnelKeep

A native Android Kotlin WebView client for keeping a remote VS Code Tunnel workspace usable on a phone, especially across screen-off and transient network changes.

> **Not affiliated with or endorsed by Microsoft, GitHub, OpenAI, or Anthropic.**

[![CI](https://github.com/sagemoyi/tunnelkeep-android/actions/workflows/ci.yml/badge.svg)](https://github.com/sagemoyi/tunnelkeep-android/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

## Features

- **Foreground keep-alive** — Reduces suspension during screen-off via foreground service, partial wake lock, and persistent notification; Android/WebView can still interrupt the connection
- **Connectivity recovery** — Monitors network state; allows VS Code's own WebSocket reconnect to restore sessions without blind reload loops
- **HTTPS-only** — Rejects all cleartext and mixed-content traffic; never approves TLS bypass
- **Renderer crash recovery** — Detects WebView renderer crashes and offers manual reload with preserved URL
- **Auth popup support** — Supports WebView popup windows and delegates explicit external schemes to installed apps
- **File chooser & downloads** — Supports file uploads from device and downloads to Downloads directory
- **Configurable URL** — Default workspace URL with ability to change and reset; always HTTPS-validated
- **Settings panel** — URL editing/reset, keep-alive toggle, reconnect, Android battery settings shortcuts
- **HyperOS guidance** — Built-in help with Xiaomi HyperOS/HyperOS no-restrictions and autostart setup steps
- **Non-Microsoft branding** — Distinct "TunnelKeep" brand with teal color scheme
- **Back navigation** — WebView history-aware back button
- **Notification tap** — Returns to workspace from notification

## Default Workspace

`https://vscode.dev/tunnel/instance-20260610-18`

The URL is configurable via Settings → Edit URL. Only `https://` URLs are accepted.

## Requirements

- Android 8.0 (API 26) or later
- Internet connection
- A running VS Code Tunnel server

## Development

### Prerequisites

- JDK 17 or later
- Android SDK (compile SDK 35, build tools 35.0.0)
- Android SDK Platform 35
- Gradle (wrapper included)

### Building

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (unsigned)
./gradlew assembleRelease

# Run tests
./gradlew test

# Run lint
./gradlew lint
```

APKs are output to `app/build/outputs/apk/`.

### Signing Release APKs

To sign release builds, create `keystore.properties` in the project root:

```properties
storeFile=release.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

Then place your `release.jks` keystore in the project root. The current public workflow intentionally publishes debug-signed test APKs and unsigned release APKs; production signing remains opt-in and must never commit the keystore or passwords.

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for component design and data flow.

## Testing

See [docs/TESTING.md](docs/TESTING.md) for test strategy and real-device testing notes.

## Security

- **HTTPS only** — All navigation attempts to HTTP URLs are blocked at the WebView level
- **No cleartext traffic** — `android:usesCleartextTraffic="false"` in manifest
- **Network security config** — `@xml/network_security_config` disallows cleartext globally
- **No third-party JavaScript bridges** — No `addJavascriptInterface` exposure
- **File access disabled** — `allowFileAccess` and `allowContentAccess` are both false
- **No credentials committed** — No tokens, cookies, keystores, or private URLs in source
- **Safe intents** — External URL handling validates and delegates safely

## License

Apache 2.0 — see [LICENSE](LICENSE).
