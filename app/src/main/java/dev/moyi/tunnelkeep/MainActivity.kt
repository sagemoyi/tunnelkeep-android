package dev.moyi.tunnelkeep

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.ValueCallback
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_NOTIFICATION_PERMISSION = 100
        const val EXTRA_RECONNECT = "dev.moyi.tunnelkeep.extra.RECONNECT"
        const val EXTRA_TOGGLE_KEEP_ALIVE = "dev.moyi.tunnelkeep.extra.TOGGLE_KEEP_ALIVE"
    }

    private lateinit var webView: TunnelWebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorOverlay: View
    private lateinit var statusIndicator: TextView
    private lateinit var webViewConfig: WebViewConfig
    private lateinit var keepAliveManager: KeepAliveManager

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var serviceBound = false
    private var keepAliveService: TunnelKeepService? = null

    // File chooser launcher
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.takeIf { it.isNotEmpty() }?.toTypedArray())
        filePathCallback = null
    }

    // Service connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? TunnelKeepService.LocalBinder
            keepAliveService = binder?.getService()
            serviceBound = true
            updateKeepAliveStatus()
            Log.d(TAG, "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            keepAliveService = null
            serviceBound = false
            updateKeepAliveStatus()
            Log.d(TAG, "Service disconnected")
        }
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Keep-alive state changed")
            updateKeepAliveStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webViewConfig = WebViewConfig(this)
        keepAliveManager = KeepAliveManager(this)

        // Find views
        webView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.progress_bar)
        errorOverlay = findViewById(R.id.error_overlay)
        statusIndicator = findViewById(R.id.status_indicator)

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))

        // Setup WebView
        setupWebView()

        // Setup error overlay
        findViewById<View>(R.id.btn_reload).setOnClickListener {
            errorOverlay.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.reloadSafe()
        }

        registerReceiver(
            stateReceiver,
            IntentFilter(TunnelKeepService.ACTION_STATE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED
        )

        // Restore keep-alive state (service may still be running)
        updateKeepAliveStatus()

        if (savedInstanceState == null) {
            webView.loadUrlSafe(webViewConfig.getUrl())
        } else if (webView.restoreState(savedInstanceState) == null) {
            webView.loadUrlSafe(webViewConfig.getUrl())
        }

        when {
            intent.getBooleanExtra(EXTRA_TOGGLE_KEEP_ALIVE, false) -> toggleKeepAlive()
            intent.getBooleanExtra(EXTRA_RECONNECT, false) -> reconnect()
        }
    }

    private fun setupWebView() {
        webView.initialize()

        webView.webViewListener = object : TunnelWebView.Listener {
            override fun onPageStarted() {
                progressBar.visibility = View.VISIBLE
                progressBar.isIndeterminate = true
            }

            override fun onPageFinished(url: String?) {
                progressBar.visibility = View.GONE
            }

            override fun onProgressChanged(progress: Int) {
                if (progress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.isIndeterminate = false
                    progressBar.progress = progress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onRendererCrashed() {
                Log.e(TAG, "WebView renderer crashed")
                webView.visibility = View.GONE
                errorOverlay.visibility = View.VISIBLE
                progressBar.visibility = View.GONE

                // Recreate the WebView
                val container = findViewById<android.widget.FrameLayout>(R.id.webview_container)
                container.removeView(webView)
                webView = TunnelWebView(this@MainActivity).apply {
                    id = R.id.webview
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                container.addView(webView, 0)
                setupWebView()

                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.renderer_crash_message),
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onFileChooserRequest(
                callback: ValueCallback<Array<Uri>>,
                acceptTypes: Array<String>,
                multiple: Boolean
            ) {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val mimeTypes = acceptTypes.filter { it.isNotBlank() }.ifEmpty { listOf("*/*") }
                try {
                    fileChooserLauncher.launch(mimeTypes.toTypedArray())
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        this@MainActivity,
                        "No file picker available",
                        Toast.LENGTH_SHORT
                    ).show()
                    callback.onReceiveValue(null)
                    filePathCallback = null
                }
            }

            override fun onExternalUrl(url: String): Boolean {
                // For OAuth URLs (accounts.google.com, login.microsoftonline.com,
                // github.com/login), open in Custom Tabs or system browser
                if (url.contains("login") || url.contains("oauth") ||
                    url.contains("accounts.google.com") ||
                    url.contains("login.microsoftonline.com") ||
                    url.contains("github.com/login")
                ) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to open auth URL", e)
                    }
                }
                return false
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        updateKeepAliveStatus()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reload -> {
                webView.reloadSafe()
                Toast.makeText(this, "Reloading…", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_reconnect -> {
                reconnect()
                true
            }
            R.id.action_keep_alive -> {
                toggleKeepAlive()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_help -> {
                startActivity(Intent(this, HelpActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun reconnect() {
        val url = webViewConfig.getUrl()
        Log.d(TAG, "Reconnecting to: $url")
        webView.loadUrlSafe(url)
        Toast.makeText(this, "Reconnecting…", Toast.LENGTH_SHORT).show()
    }

    private fun toggleKeepAlive() {
        val enabled = !keepAliveManager.isEnabled()
        keepAliveManager.setEnabled(enabled)

        if (enabled) {
            // Check notification permission first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Toast.makeText(
                        this,
                        "Notification permission required for keep-alive",
                        Toast.LENGTH_LONG
                    ).show()
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        REQUEST_NOTIFICATION_PERMISSION
                    )
                    keepAliveManager.setEnabled(false)
                    updateKeepAliveStatus()
                    return
                }
            }
            startKeepAliveService()
            Toast.makeText(this, getString(R.string.keep_alive_active), Toast.LENGTH_SHORT).show()
        } else {
            stopKeepAliveService()
            Toast.makeText(this, getString(R.string.keep_alive_inactive), Toast.LENGTH_SHORT).show()
        }
        updateKeepAliveStatus()
    }

    private fun startKeepAliveService() {
        val intent = Intent(this, TunnelKeepService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        startForegroundService(intent)
    }

    private fun stopKeepAliveService() {
        val intent = Intent(this, TunnelKeepService::class.java).apply {
            action = TunnelKeepService.ACTION_STOP
        }
        startService(intent)
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
            keepAliveService = null
        }
    }

    private fun updateKeepAliveStatus() {
        val isRunning = keepAliveManager.isEnabled()
        statusIndicator.text = if (isRunning) {
            getString(R.string.keep_alive_active)
        } else {
            null
        }
        statusIndicator.visibility = if (isRunning) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        webView.onForeground()

        // Re-bind to service if keep-alive was enabled
        if (keepAliveManager.isEnabled() && !serviceBound) {
            val intent = Intent(this, TunnelKeepService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        updateKeepAliveStatus()
    }

    override fun onPause() {
        super.onPause()
        // Do not call WebView.onPause()/pauseTimers() here: the purpose of
        // keep-alive mode is to give Chromium a chance to keep its connection.
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(stateReceiver)
        } catch (e: IllegalArgumentException) {
            // Already unregistered
        }
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    override fun onBackPressed() {
        // WebView handles back navigation for its history
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        webView.onForeground()
        when {
            intent.getBooleanExtra(EXTRA_TOGGLE_KEEP_ALIVE, false) -> toggleKeepAlive()
            intent.getBooleanExtra(EXTRA_RECONNECT, false) -> reconnect()
        }
        Log.d(TAG, "onNewIntent: returning to workspace")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // WebView state (cookies, DOM storage, URL) is preserved automatically
        // by the WebView's built-in save/restore state mechanism
        webView.saveState(outState)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Notification permission granted")
            } else {
                Log.w(TAG, "Notification permission denied")
                // Keep-alive won't work well without notification permission on 13+
            }
        }
    }
}
