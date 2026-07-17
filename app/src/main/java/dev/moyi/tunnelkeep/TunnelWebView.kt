package dev.moyi.tunnelkeep

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.ViewGroup
import android.webkit.*

/**
 * Custom WebView preconfigured for VS Code Tunnel usage with:
 * - HTTPS-only enforcement
 * - Cookie/DOM storage preservation
 * - Renderer crash recovery
 * - Auth popup handling
 * - File chooser delegation
 * - Download handling
 * - Safe external intent handling
 * - No blind reload loops on error
 */
class TunnelWebView(context: Context) : WebView(context) {

    companion object {
        private const val TAG = "TunnelWebView"
    }

    interface Listener {
        fun onPageStarted()
        fun onPageFinished(url: String?)
        fun onProgressChanged(progress: Int)
        fun onRendererCrashed()
        fun onFileChooserRequest(
            filePathCallback: ValueCallback<Array<Uri>>,
            acceptTypes: Array<String>,
            multiple: Boolean
        )
        fun onExternalUrl(url: String): Boolean
    }

    var webViewListener: Listener? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun initialize(userAgent: String? = null) {

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setDatabasePath(context.applicationContext.getDir("databases", Context.MODE_PRIVATE).path)

            // Security: no arbitrary file access
            allowFileAccess = false
            allowContentAccess = false

            // Never allow mixed content
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // Safe browsing
            @Suppress("DEPRECATION")
            safeBrowsingEnabled = true

            // Allow multiple windows for auth popups
            setSupportMultipleWindows(true)

            // Cache and storage
            cacheMode = WebSettings.LOAD_DEFAULT

            // User agent
            userAgentString?.let {
                if (it.isNotBlank()) {
                    this.userAgentString = it
                }
            }

            // Viewport
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // Text scaling
            textZoom = 100

            // Geolocation is unnecessary for a tunnel editor and stays disabled.
            setGeolocationEnabled(false)

            // Media playback
            mediaPlaybackRequiresUserGesture = false
        }

        webViewClient = TunnelWebViewClient()
        webChromeClient = TunnelWebChromeClient()
        setDownloadListener(TunnelDownloadListener())

        Log.d(TAG, "WebView initialized")
    }

    /**
     * Loads the given URL, enforcing HTTPS.
     */
    fun loadUrlSafe(url: String) {
        if (!url.startsWith("https://", ignoreCase = true)) {
            Log.w(TAG, "Blocked non-HTTPS URL: $url")
            return
        }
        super.loadUrl(url)
    }

    fun reloadSafe() {
        if (url?.startsWith("https://", ignoreCase = true) == true) {
            reload()
        } else {
            Log.w(TAG, "Reload blocked: current URL is not HTTPS")
        }
    }

    /**
     * Called when moving to foreground — ensures WebView is visible and active.
     */
    fun onForeground() {
        if (visibility != VISIBLE) {
            visibility = VISIBLE
        }
        // On some devices, WebView needs a resume callback
        onResume()
        resumeTimers()
    }

    /**
     * Called when moving to background — pause WebView rendering.
     */
    fun onBackground() {
        pauseTimers()
        onPause()
    }

    // --- Internal client classes ---

    private inner class TunnelWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false

            // External URLs like mailto:, tel:, intent:// — delegate
            if (url.startsWith("mailto:") || url.startsWith("tel:") ||
                url.startsWith("intent://") || url.startsWith("market://")) {
                val handled = webViewListener?.onExternalUrl(url) ?: false
                if (handled) return true
                // Safe intent launch
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to open external URL: $url", e)
                }
                return true
            }

            // Block all remaining non-HTTPS navigation.
            if (!url.startsWith("https://", ignoreCase = true)) {
                Log.w(TAG, "Blocked non-HTTPS navigation: $url")
                return true
            }

            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            Log.d(TAG, "Page started: $url")
            webViewListener?.onPageStarted()
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            Log.d(TAG, "Page finished: $url")
            webViewListener?.onPageFinished(url)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            Log.w(TAG, "WebView error: ${error?.description} for ${request?.url}")
            // Do NOT auto-reload — let VS Code's own reconnect logic handle it.
            // A well-behaved VS Code web client will retry via WebSocket reconnect.
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            Log.w(TAG, "HTTP error: ${errorResponse?.statusCode} for ${request?.url}")
            // Don't auto-reload on HTTP errors either
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            Log.e(TAG, "Render process gone: ${detail?.didCrash()}")
            webViewListener?.onRendererCrashed()
            // Destroy and recreate will be handled by MainActivity.
            return true
        }
    }

    private inner class TunnelWebChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            webViewListener?.onProgressChanged(newProgress)
        }

        // File chooser for uploads
        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            filePathCallback ?: return false
            val params = fileChooserParams ?: return false

            webViewListener?.onFileChooserRequest(
                filePathCallback,
                params.acceptTypes,
                params.mode == FileChooserParams.MODE_OPEN_MULTIPLE
            )
            return true
        }

        // Auth popups — open in system browser / Custom Tab
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: android.os.Message?
        ): Boolean {
            if (!isUserGesture) return false

            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false

            // This typically happens for OAuth popups (GitHub, Microsoft).
            // We handle it by delegating to onExternalUrl which opens the
            // system browser. The redirect URL points back to vscode.dev,
            // so the user needs to return to the app after auth.
            val newWebView = WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        if (webViewListener?.onExternalUrl(url) == true) return true
                        // Default: load in this popup WebView
                        return false
                    }
                }
            }

            transport.webView = newWebView
            resultMsg.sendToTarget()

            Log.d(TAG, "Popup window created for auth")
            return true
        }
    }

    private inner class TunnelDownloadListener : DownloadListener {
        override fun onDownloadStart(
            url: String,
            userAgent: String,
            contentDisposition: String,
            mimetype: String,
            contentLength: Long
        ) {
            Log.d(TAG, "Download requested: $url ($mimetype)")

            try {
                if (!url.startsWith("https://", ignoreCase = true)) {
                    Log.w(TAG, "Blocked non-HTTPS download: $url")
                    return
                }

                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    addRequestHeader("User-Agent", userAgent)
                    CookieManager.getInstance().getCookie(url)?.let {
                        addRequestHeader("Cookie", it)
                    }
                    setMimeType(mimetype)
                    setTitle(extractFileName(url, contentDisposition))
                    setDescription("Downloading file from VS Code workspace")
                    setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        extractFileName(url, contentDisposition)
                    )
                }

                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start download", e)
            }
        }

        private fun extractFileName(url: String, contentDisposition: String): String {
            // Try content-disposition first
            val cdPattern = Regex("""filename[^;=\n]*=((['"]).*?\2|[^;\n]*)""")
            val cdMatch = cdPattern.find(contentDisposition)
            cdMatch?.let {
                return it.groupValues[1].trim('"', '\'')
            }
            // Fall back to last path segment
            return Uri.parse(url).lastPathSegment ?: "download"
        }
    }
}
