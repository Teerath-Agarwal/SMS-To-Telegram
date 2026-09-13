package com.example.smsToTelegram

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val settings = RelaySettings(context)
        val subId = intent.getIntExtra("subscription", -1)
        
        val pendingResult = goAsync()
        scope.launch {
            try {
                val fwdNumber = settings.forwardingNumber.first()
                val regexList = settings.senderRegexList.first()

                for (msg in messages) {
                    val sender = msg.originatingAddress ?: ""
                    val body = msg.messageBody ?: ""

                    if (body.startsWith("[SMS Relay")) continue

                    // Simple logic: If no filters are defined, relay everything.
                    // If filters exist, the sender must match at least one.
                    val isMatch = if (regexList.isEmpty()) {
                        true
                    } else {
                        regexList.any { regex ->
                            try {
                                Regex(regex).containsMatchIn(sender)
                            } catch (e: Exception) {
                                Log.e("SmsReceiver", "Bad Regex: $regex")
                                false
                            }
                        }
                    }

                    if (isMatch) {
                        val relayedMessage = "$sender]\n$body"
                        val serviceIntent = Intent(context, SmsForwardingService::class.java).apply {
                            putExtra("EXTRA_TO", fwdNumber)
                            putExtra("EXTRA_BODY", relayedMessage)
                            putExtra("EXTRA_SUB_ID", subId)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error in scope", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
