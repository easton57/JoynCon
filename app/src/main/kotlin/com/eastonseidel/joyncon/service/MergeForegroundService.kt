package com.eastonseidel.joyncon.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.eastonseidel.joyncon.MainActivity
import com.eastonseidel.joyncon.R
import com.eastonseidel.joyncon.data.MappingJson
import com.eastonseidel.joyncon.data.RemapRepository
import com.eastonseidel.joyncon.shizuku.BridgeClient
import com.eastonseidel.joyncon.shizuku.ShizukuHelper
import com.eastonseidel.joyncon.shizuku.ShizukuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the merge session's lifecycle: follows [ConnectionState]'s coarse
 * (zero-permission) connect/disconnect signal, and drives the
 * Shizuku bridge to (re)start the uinput merge whenever both Joy-Cons are present, or fall back to
 * plain passthrough when only one is.
 */
class MergeForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var remapRepository: RemapRepository

    override fun onCreate() {
        super.onCreate()
        remapRepository = RemapRepository(applicationContext)
        startForeground(NOTIFICATION_ID, buildNotification("Watching for Joy-Cons…"))

        scope.launch {
            ShizukuHelper.state.collect { state ->
                if (state == ShizukuState.READY) {
                    BridgeClient.bind(applicationContext)
                }
            }
        }
        scope.launch {
            BridgeClient.status.map { it.bound }.distinctUntilChanged().collect { bound ->
                if (bound) applyConnection(ConnectionState.state.value)
            }
        }
        scope.launch {
            ConnectionState.state.collect { applyConnection(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        BridgeClient.stopMerge()
        BridgeClient.unbind()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun applyConnection(connection: NativeConnectionState) {
        val left = connection.leftSeen
        val right = connection.rightSeen
        updateNotification(left, right)

        if (left && right) {
            scope.launch { startMergeIfNeeded() }
        } else {
            // Only one (or none) present: don't grab anything, let it pass through natively (req #6).
            BridgeClient.stopMerge()
        }
    }

    private suspend fun startMergeIfNeeded() {
        val config = remapRepository.configFlow.first()
        BridgeClient.startMerge(MappingJson.encode(config))
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "joyncon_status"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title_merging))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(left: Boolean, right: Boolean) {
        val text = when {
            left && right -> "Both Joy-Cons connected — merging"
            left -> "Only Joy-Con (L) connected — passthrough"
            right -> "Only Joy-Con (R) connected — passthrough"
            else -> "No Joy-Cons connected"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}
