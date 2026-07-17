# Architecture

## Overview

TunnelKeep is a single-activity Android app that wraps a VS Code Tunnel workspace in a WebView with foreground service keep-alive.

```
┌─────────────────────────────────────────────┐
│                 MainActivity                 │
│  ┌───────────────────────────────────────┐  │
│  │          TunnelWebView                │  │
│  │  ┌─────────────────────────────────┐  │  │
│  │  │  WebViewClient                  │  │  │
│  │  │  - HTTPS enforcement            │  │  │
│  │  │  - Renderer crash recovery      │  │  │
│  │  │  - External URL delegation      │  │  │
│  │  ├─────────────────────────────────┤  │  │
│  │  │  WebChromeClient               │  │  │
│  │  │  - File chooser                 │  │  │
│  │  │  - Auth popup windows           │  │  │
│  │  │  - Progress tracking            │  │  │
│  │  ├─────────────────────────────────┤  │  │
│  │  │  DownloadListener               │  │  │
│  │  │  - DownloadManager              │  │  │
│  │  └─────────────────────────────────┘  │  │
│  └───────────────────────────────────────┘  │
│  ┌───────────────────────────────────────┐  │
│  │  TunnelKeepService (bind)             │  │
│  │  - PartialWakeLock                    │  │
│  │  - Foreground notification            │  │
│  │  - ConnectivityMonitor                │  │
│  └───────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

## Component Design

### TunnelKeepApplication

Application subclass that initializes a `NotificationChannel` for the foreground service. Uses `IMPORTANCE_LOW` to avoid intrusive notification sounds.

### MainActivity

The single-task launcher activity. It hosts:

- **TunnelWebView**: The core WebView wrapper
- **Progress bar**: Shows page load progress
- **Error overlay**: Shown on renderer crash with manual reload button
- **Status indicator**: Shows keep-alive active/inactive state

Lifecycle:
- **onCreate**: Initializes WebView, loads URL from `WebViewConfig`, checks notification permission
- **onResume**: Resumes WebView timers, re-binds to service
- **onPause**: Pauses WebView timers
- **onDestroy**: Unbinds service, unregisters receivers
- **onNewIntent**: Handles notification tap (returns to foreground)
- **onBackPressed**: WebView history-aware back navigation

### TunnelWebView

`WebView` subclass encapsulating all WebView configuration:

**Security**:
- `javaScriptEnabled = true` (required by VS Code)
- `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW`
- `allowFileAccess = false`
- `allowContentAccess = false`
- All URL loading goes through `loadUrlSafe()` which enforces `https://` prefix

**Renderer crash recovery**:
- `WebViewClient.onRenderProcessGone()` detects crashes
- `Listener.onRendererCrashed()` notifies activity
- Activity shows error overlay; user manually reloads
- New WebView instance is created (old one cannot be recovered)

**Auth popups**:
- `WebChromeClient.onCreateWindow()` creates child WebView for popups
- GitHub/Microsoft OAuth URLs delegate to system browser via `onExternalUrl()`

**File chooser**:
- `WebChromeClient.onShowFileChooser()` captures callback
- Delegates to `ActivityResultContracts.OpenDocument`

**Downloads**:
- `DownloadListener.onDownloadStart()` → `DownloadManager.Request`

### TunnelKeepService

Foreground service with bounded lifecycle:

- **start**: Posts notification with stop action, acquires `PARTIAL_WAKE_LOCK` (30min timeout, auto-refreshed), begins connectivity monitoring
- **Periodic health check** (every 30s): Re-bumps wake lock, verifies keep-alive preference is still enabled
- **stop**: Removes notification, releases wake lock, stops connectivity monitoring
- **onTaskRemoved**: Continues running (user must explicitly stop)

**Service type**: `specialUse` with property explaining the use case — compliant with Android 14+ foreground service restrictions.

### WebViewConfig

SharedPreferences-backed URL storage:

- Validates all URLs against `https://` prefix and basic hygiene (no spaces, newlines, CR)
- Falls back to `DEFAULT_URL` if stored URL is invalid
- Tracks whether URL was manually set (`url_set_manually` flag)
- `resetToDefault()` clears both stored URL and manual flag

### KeepAliveManager

SharedPreferences-backed boolean toggle for keep-alive preference. Does NOT interact with the service directly — the service itself queries this preference in its health check loop.

### ConnectivityMonitor

Wraps `ConnectivityManager.NetworkCallback`:

- `onAvailable`: Network with internet capability detected → `onConnected()`
- `onLost`: Network lost → `onDisconnected()`
- `onCapabilitiesChanged`: Re-evaluates internet + validated status
- `isConnected()`: Synchronous check for current state

The monitor does NOT trigger WebView reloads — VS Code's own WebSocket reconnect handles recovery.

## Data Flow

```
User toggles keep-alive ON
  → KeepAliveManager.setEnabled(true)
  → MainActivity.startKeepAliveService()
  → TunnelKeepService.onStartCommand()
    → Acquires PARTIAL_WAKE_LOCK
    → Posts persistent notification
    → Starts connectivity monitoring
    → Begins periodic health check (30s)

Network changes
  → ConnectivityMonitor callback
  → No automatic WebView reload
  → VS Code WebSocket handles reconnect

Renderer crashes
  → TunnelWebViewClient.onRenderProcessGone()
  → Listener.onRendererCrashed()
  → MainActivity shows error overlay
  → User manually reloads
  → New WebView created, URL from WebViewConfig

URL change
  → SettingsActivity shows dialog
  → WebViewConfig.setUrl(newUrl)
  → Broadcast sent to MainActivity
  → MainActivity loads new URL via loadUrlSafe()
```

## Thread Model

- **Main thread**: All UI, WebView lifecycle, service bind/unbind
- **Handler**: Service health check loop (30s period, runs on main looper)
- **Network callback**: Runs on ConnectivityManager's internal thread, posts to listener

## Android API Compatibility

| API Range | Behavior |
|-----------|----------|
| 26-28 | Basic foreground service, no type required |
| 29-33 | Foreground service with 2-arg `startForeground`; `DATA_SYNC` type (optional) |
| 34+ (Android 14) | Must specify `SPECIAL_USE` type → `startForeground(id, notif, SPECIAL_USE)` |
| 33+ (Android 13) | Requires `POST_NOTIFICATIONS` runtime permission |
