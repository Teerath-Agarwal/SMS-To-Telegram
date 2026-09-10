package com.example.smsToTelegram

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class SmsForwardingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val CHANNEL_ID = "relay_channel"
    private val NOTIFICATION_ID = 99

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val to = intent?.getStringExtra("EXTRA_TO")
        val body = intent?.getStringExtra("EXTRA_BODY")
        val subId = intent?.getIntExtra("EXTRA_SUB_ID", -1) ?: -1
        val forceSms = intent?.getBooleanExtra("EXTRA_FORCE_SMS", false) ?: false

        if (body != null) {
            startForegroundServiceNotification(to ?: "Telegram")

            serviceScope.launch {
                try {
                    var relayed = false

                    // 1. Primary: Telegram (if not forced SMS)
                    if (!forceSms && NetworkUtils.isInternetAvailable(this@SmsForwardingService)) {
                        Log.d("RelayService", "Attempting Telegram relay")
                        relayed = TelegramNotificationService.sendMessage("[$body")
                    }

                    // 2. Fallback: SMS (if Telegram failed or no internet or forced)
                    if (!relayed && !to.isNullOrBlank()) {
                        Log.d("RelayService", "Attempting SMS relay fallback to $to")
                        SmsRelayWorker.forwardSms(this@SmsForwardingService, to, "[SMS Relay - $body", subId)
                        // Note: forwardSms is fire-and-forget in terms of carrier ack here,
                        // but it ensures the command is sent to the modem.
                    } else if (!relayed) {
                        Log.e("RelayService", "All relay methods failed and no fallback number set")
                    }

                } finally {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startForegroundServiceNotification(target: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Relay Active")
            .setContentText("Relaying message to $target...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "SMS Relay", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
