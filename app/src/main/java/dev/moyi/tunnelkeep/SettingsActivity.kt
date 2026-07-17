package dev.moyi.tunnelkeep

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
    }

    private lateinit var webViewConfig: WebViewConfig
    private lateinit var keepAliveManager: KeepAliveManager
    private lateinit var currentUrlView: TextView
    private lateinit var switchKeepAlive: SwitchMaterial
    private lateinit var keepAliveStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        webViewConfig = WebViewConfig(this)
        keepAliveManager = KeepAliveManager(this)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // Find views
        currentUrlView = findViewById(R.id.current_url)
        switchKeepAlive = findViewById(R.id.switch_keep_alive)
        keepAliveStatus = findViewById(R.id.keep_alive_status)

        // Display current URL
        updateUrlDisplay()
        updateKeepAliveDisplay()

        // Edit URL
        findViewById<android.view.View>(R.id.btn_edit_url).setOnClickListener {
            showEditUrlDialog()
        }

        // Reset URL
        findViewById<android.view.View>(R.id.btn_reset_url).setOnClickListener {
            showResetUrlDialog()
        }

        // Reconnect is performed after returning to MainActivity.
        findViewById<android.view.View>(R.id.btn_reconnect).setOnClickListener {
            returnToWorkspace(reconnect = true)
        }

        // Keep-alive toggle
        switchKeepAlive.isChecked = keepAliveManager.isEnabled()
        switchKeepAlive.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // MainActivity owns notification permission and service startup.
                keepAliveManager.setEnabled(false)
                returnToWorkspace(toggleKeepAlive = true)
            } else {
                keepAliveManager.setEnabled(false)
                startService(Intent(this, TunnelKeepService::class.java).apply {
                    action = TunnelKeepService.ACTION_STOP
                })
                updateKeepAliveDisplay()
            }
        }

        // App info
        findViewById<android.view.View>(R.id.btn_app_info).setOnClickListener {
            openAppInfo()
        }

        // Battery optimization
        findViewById<android.view.View>(R.id.btn_battery_opt).setOnClickListener {
            openBatteryOptimization()
        }

        // Help
        findViewById<android.view.View>(R.id.btn_help).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
    }

    private fun updateUrlDisplay() {
        currentUrlView.text = webViewConfig.getUrl()
    }

    private fun updateKeepAliveDisplay() {
        val enabled = keepAliveManager.isEnabled()
        switchKeepAlive.isChecked = enabled
        keepAliveStatus.text = if (enabled) {
            getString(R.string.keep_alive_active)
        } else {
            getString(R.string.keep_alive_inactive)
        }
    }

    private fun showEditUrlDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(webViewConfig.getUrl())
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.url_dialog_title)
            .setMessage(R.string.url_dialog_message)
            .setView(input)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                val newUrl = input.text.toString().trim()
                if (webViewConfig.setUrl(newUrl)) {
                    updateUrlDisplay()
                    returnToWorkspace(reconnect = true)
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.url_error_invalid),
                        Toast.LENGTH_LONG
                    ).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun showResetUrlDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.url_reset_confirm)
            .setMessage(R.string.url_reset_message)
            .setPositiveButton(R.string.reset) { dialog, _ ->
                webViewConfig.resetToDefault()
                updateUrlDisplay()
                returnToWorkspace(reconnect = true)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun returnToWorkspace(reconnect: Boolean = false, toggleKeepAlive: Boolean = false) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_RECONNECT, reconnect)
            putExtra(MainActivity.EXTRA_TOGGLE_KEEP_ALIVE, toggleKeepAlive)
        })
        finish()
    }

    private fun openAppInfo() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open app info settings", e)
            Toast.makeText(this, "Cannot open app info", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openBatteryOptimization() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open battery optimization", e)
            // Fallback to generic battery settings
            try {
                startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, "Cannot open battery settings", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
