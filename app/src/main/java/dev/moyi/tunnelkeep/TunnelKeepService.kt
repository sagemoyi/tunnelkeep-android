package dev.moyi.tunnelkeep

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * Foreground service that maintains a notification, partial wake lock,
 * and connectivity monitoring for the TunnelKeep app.
 *
 * The service does NOT directly manage the WebView — the WebView lives
 * in the Activity. The service provides the Android system hints needed
 * to reduce the chance of process death during screen-off.
 */
class TunnelKeepService : Service(), LifecycleOwner {

    companion object {
        private const val TAG = "TunnelKeepService"
        const val ACTION_STOP = "dev.moyi.tunnelkeep.STOP_SERVICE"
        const val ACTION_STATE_CHANGED = "dev.moyi.tunnelkeep.KEEP_ALIVE_STATE_CHANGED"
        private const val WAKE_LOCK_TIMEOUT_MS = 15 * 60 * 1000L
        private const val WAKE_LOCK_REFRESH_MS = 10 * 60 * 1000L
    }

    private val binder = LocalBinder()
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var connectivityMonitor: ConnectivityMonitor
    private lateinit var keepAliveManager: KeepAliveManager

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var isServiceActive = false

    private val handler: Handler = Handler(Looper.getMainLooper())

    private val healthCheckRunnable = object : Runnable {
        override fun run() {
            ensureKeepAliveState()
            handler.postDelayed(this, WAKE_LOCK_REFRESH_MS)
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    inner class LocalBinder : Binder() {
        fun getService(): TunnelKeepService = this@TunnelKeepService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        connectivityMonitor = ConnectivityMonitor(this)
        keepAliveManager = KeepAliveManager(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand: ${intent?.action}")

        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Stop action received")
                stopService()
                return START_NOT_STICKY
            }
        }

        if (!keepAliveManager.isEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isServiceActive) {
            startServiceInternal()
        }

        return START_STICKY
    }

    private fun startServiceInternal() {
        Log.d(TAG, "Starting keep-alive service")

        // Build notification with stop action
        val stopIntent = Intent(this, TunnelKeepService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tap notification -> open MainActivity
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, TunnelKeepApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .build()

        // Acquire partial wake lock
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "TunnelKeep::KeepAlive"
            ).apply {
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
            Log.d(TAG, "Wake lock acquired")
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to acquire wake lock: ${e.message}")
        }

        // Start as foreground service
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    TunnelKeepApplication.NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(TunnelKeepApplication.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
            releaseResources()
            stopSelf()
            return
        }

        // Start connectivity monitoring
        connectivityMonitor.start(object : ConnectivityMonitor.Listener {
            override fun onConnected() {
                Log.d(TAG, "Network connected")
            }

            override fun onDisconnected() {
                Log.w(TAG, "Network disconnected")
            }
        })

        // Start periodic keep-alive health check
        handler.postDelayed(healthCheckRunnable, WAKE_LOCK_REFRESH_MS)

        isServiceActive = true
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    private fun stopService() {
        Log.d(TAG, "Stopping keep-alive service")
        releaseResources()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        isServiceActive = false
        keepAliveManager.setEnabled(false)
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))

        Log.d(TAG, "Service stopped")
    }

    private fun ensureKeepAliveState() {
        if (!keepAliveManager.isEnabled()) {
            Log.d(TAG, "Keep-alive disabled by user preference, stopping service")
            stopService()
            return
        }

        // Re-acquire wake lock if it was released
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                it.acquire(WAKE_LOCK_TIMEOUT_MS)
            } else {
                it.acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        } ?: run {
            try {
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "TunnelKeep::KeepAlive"
                ).apply {
                    acquire(WAKE_LOCK_TIMEOUT_MS)
                }
                Log.d(TAG, "Wake lock re-acquired in health check")
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to re-acquire wake lock: ${e.message}")
            }
        }

        Log.d(TAG, "Keep-alive health check passed")
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        releaseResources()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    private fun releaseResources() {
        handler.removeCallbacks(healthCheckRunnable)
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        connectivityMonitor.stop()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved - user swiped app away, service continues")
        super.onTaskRemoved(rootIntent)
    }
}
