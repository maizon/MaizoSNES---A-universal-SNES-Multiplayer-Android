package com.maizon.maizosnes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.net.wifi.WifiManager
import android.content.pm.ServiceInfo

/**
 * Keeps a user-visible active multiplayer session scheduled while Android
 * backgrounds the Activity. The existing NetplaySession remains the sole
 * owner of sockets, emulation frames, ROM rights and Gekko callbacks.
 *
 * A user/system force-stop or a process kill cannot keep sockets alive;
 * this service does not promise reconnection without a future protocol.
 */
class MultiplayerKeepAliveService : Service() {
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private val channelId = "maizosnes_multiplayer"

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(channelId, "Multiplayer MaizoSNES",
                    NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Manter a conexão de partida LAN ou online ativa."
                })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val launch = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, channelId) else Notification.Builder(this)
        val notification = builder
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("MaizoSNES — multiplayer ativo")
            .setContentText("Sessão LAN/online em andamento • toque para voltar")
            .setContentIntent(launch)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(55435, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(55435, notification)
        }
        if (wake?.isHeld != true) {
            wake = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "MaizoSNES:ActiveMultiplayer").apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }
        // A partial CPU wake lock alone cannot prevent Wi-Fi power saving
        // from suspending the socket on some Android devices while screen off.
        // Keep the Wi-Fi radio available for the duration of this session only.
        if (wifi?.isHeld != true) {
            runCatching {
                val manager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wifi = manager.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "MaizoSNES:MultiplayerWiFi").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        }
        // Restarting a service without its Activity-owned sockets cannot
        // reconstruct a deterministic session; don't create a fake room.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { if (wake?.isHeld == true) wake?.release() }
        wake = null
        runCatching { if (wifi?.isHeld == true) wifi?.release() }
        wifi = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
