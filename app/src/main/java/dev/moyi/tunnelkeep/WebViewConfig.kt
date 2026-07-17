package dev.moyi.tunnelkeep

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.net.URI

/**
 * Manages the configurable workspace URL.
 * Stores URL in SharedPreferences, enforces HTTPS-only,
 * and provides a default fallback.
 */
class WebViewConfig(context: Context) {

    companion object {
        private const val TAG = "WebViewConfig"
        private const val PREFS_NAME = "tunnelkeep_url"
        private const val KEY_URL = "workspace_url"
        private const val KEY_URL_MANUAL = "url_set_manually"
        const val DEFAULT_URL = "https://vscode.dev/tunnel/instance-20260610-18"
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns the current workspace URL.
     * Always enforces HTTPS — if the stored URL is not HTTPS,
     * falls back to the default.
     */
    fun getUrl(): String {
        val stored = prefs.getString(KEY_URL, null)
        return if (stored != null && isValidUrl(stored)) {
            stored
        } else {
            DEFAULT_URL
        }
    }

    /**
     * Sets a new workspace URL. Only accepts HTTPS URLs.
     * Returns true if the URL was accepted and stored.
     */
    fun setUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!isValidUrl(trimmed)) {
            Log.w(TAG, "Rejected invalid URL: $trimmed")
            return false
        }
        prefs.edit().putString(KEY_URL, trimmed).putBoolean(KEY_URL_MANUAL, true).apply()
        Log.d(TAG, "URL updated: $trimmed")
        return true
    }

    /**
     * Resets the URL to the default.
     */
    fun resetToDefault() {
        prefs.edit()
            .remove(KEY_URL)
            .remove(KEY_URL_MANUAL)
            .apply()
        Log.d(TAG, "URL reset to default")
    }

    /**
     * Returns true if the URL has been manually changed from default.
     */
    fun isCustomUrl(): Boolean {
        return prefs.getBoolean(KEY_URL_MANUAL, false) &&
                prefs.getString(KEY_URL, null) != null
    }

    /**
     * Validates that a URL starts with https:// and has sensible structure.
     */
    fun isValidUrl(url: String): Boolean {
        if (url.any { it.isWhitespace() || it.isISOControl() }) return false
        return try {
            val uri = URI(url)
            uri.scheme.equals("https", ignoreCase = true) &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }
}
