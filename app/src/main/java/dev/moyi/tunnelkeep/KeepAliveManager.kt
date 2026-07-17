package dev.moyi.tunnelkeep

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Manages the keep-alive toggle state.
 * Does NOT directly start/stop the service — that is the caller's responsibility.
 */
class KeepAliveManager(context: Context) {

    companion object {
        private const val TAG = "KeepAliveManager"
        private const val PREFS_NAME = "tunnelkeep_keepalive"
        private const val KEY_ENABLED = "keep_alive_enabled"
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns the persisted keep-alive toggle state.
     */
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    /**
     * Persists the keep-alive toggle state.
     */
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        Log.d(TAG, "Keep-alive set to: $enabled")
    }
}
