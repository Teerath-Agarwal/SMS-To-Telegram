package com.example.sms_relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SmsReceiver", "Intent received: ${intent.action}")
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val settings = RelaySettings(context)
        val subId = intent.getIntExtra("subscription", -1)
        
        Log.d("SmsReceiver", "Processing ${messages.size} messages on Subscription ID: $subId")

        val pendingResult = goAsync()
        scope.launch {
            try {
                val forwardingNumber = settings.forwardingNumber.first()
                val regexList = settings.senderRegexList.first()

                Log.d("SmsReceiver", "Settings - Target: $forwardingNumber, Regex Count: ${regexList.size}")

                if (forwardingNumber.isNullOrBlank()) {
                    Log.w("SmsReceiver", "Aborting: Forwarding number is empty")
                    return@launch
                }

                for (msg in messages) {
                    val sender = msg.originatingAddress ?: ""
                    val body = msg.messageBody ?: ""
                    Log.d("SmsReceiver", "Message from $sender: ${body.take(10)}...")

                    if (body.startsWith("[SMS Relay")) {
                        Log.d("SmsReceiver", "Loop prevention: skipped")
                        continue
                    }

                    val isMatch = regexList.any { regex ->
                        try {
                            val matches = Regex(regex).containsMatchIn(sender)
                            Log.d("SmsReceiver", "Regex '$regex' match: $matches")
                            matches
                        } catch (e: Exception) {
                            Log.e("SmsReceiver", "Bad Regex: $regex")
                            false
                        }
                    }

                    if (isMatch) {
                        val relayedMessage = "[SMS Relay - $sender] $body"
                        Log.d("SmsReceiver", "Forwarding message from $sender to $forwardingNumber")
                        SmsRelayWorker.forwardSms(context, forwardingNumber, relayedMessage, subId)
                    } else {
                        Log.d("SmsReceiver", "Sender $sender did not match any regex")
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Critical error in scope", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
