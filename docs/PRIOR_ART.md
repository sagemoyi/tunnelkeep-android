# Prior art and constraints

## Visualcodeandroid

Reviewed `Baran3575/Visualcodeandroid` at commit `5c19f77c768c1467c02a2ba070bfdc52599e87d9`.

Useful ideas:

- Small native Kotlin WebView shell.
- Configurable start URL.
- Mobile-oriented CSS injection.
- GitHub Actions APK build.

Reasons not to fork:

- No license is declared.
- The repository was created on 2026-07-14 and has no adoption history.
- It has no foreground service, wake lock, connectivity recovery, renderer recovery, or session-health logic.
- It accepts all TLS certificate errors and allows cleartext/mixed content, which is inappropriate for a public reusable client.

## Android constraints

- A foreground service and partial CPU wake lock can reduce suspension and process death, but cannot guarantee that Chromium WebView JavaScript/renderers remain active indefinitely while the screen is off.
- Reconnection must prefer the VS Code web client's own reconnect path. Blind reload loops can destroy useful frontend state.
- OEM battery controls, especially HyperOS, require explicit user-facing setup guidance and real-device testing.
- The app should recover safely after renderer death, network transitions, and process recreation while preserving cookies and URL/workspace state where Android permits.
