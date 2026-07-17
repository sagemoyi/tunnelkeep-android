package dev.moyi.tunnelkeep

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

/**
 * Monitors network connectivity and notifies listeners of state changes.
 * Uses ConnectivityManager.NetworkCallback for efficient monitoring.
 */
class ConnectivityMonitor(context: Context) {

    companion object {
        private const val TAG = "ConnectivityMonitor"
    }

    interface Listener {
        fun onConnected()
        fun onDisconnected()
    }

    private val connectivityManager: ConnectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var listener: Listener? = null
    private var isRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available")
            listener?.onConnected()
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost")
            listener?.onDisconnected()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            Log.d(TAG, "Capabilities changed: internet=$hasInternet, validated=$isValidated")
            if (hasInternet && isValidated) {
                listener?.onConnected()
            }
        }
    }

    /**
     * Returns true if the device currently has a validated internet connection.
     */
    fun isConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Starts monitoring connectivity. Call from foreground.
     */
    fun start(listener: Listener) {
        if (isRegistered) return
        this.listener = listener
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        isRegistered = true
        Log.d(TAG, "Connectivity monitoring started")
    }

    /**
     * Stops monitoring connectivity.
     */
    fun stop() {
        if (!isRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "NetworkCallback already unregistered", e)
        }
        listener = null
        isRegistered = false
        Log.d(TAG, "Connectivity monitoring stopped")
    }
}
