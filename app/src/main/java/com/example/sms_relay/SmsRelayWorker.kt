package com.example.sms_relay

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

object SmsRelayWorker {
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun forwardSms(context: Context, to: String, message: String, subId: Int = -1) {
        try {
            val baseManager: SmsManager = context.getSystemService(SmsManager::class.java)
            val smsManager = if (subId != -1) {
                Log.d("SmsRelayWorker", "Using provided subId: $subId")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    baseManager.createForSubscriptionId(subId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(subId)
                }
            } else {
                val defaultSubId = SmsManager.getDefaultSmsSubscriptionId()
                Log.d("SmsRelayWorker", "No subId provided. Default system subId: $defaultSubId")
                if (defaultSubId != -1) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        baseManager.createForSubscriptionId(defaultSubId)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getSmsManagerForSubscriptionId(defaultSubId)
                    }
                } else {
                    Log.d("SmsRelayWorker", "Falling back to base manager")
                    baseManager
                }
            }
            
            Log.d("SmsRelayWorker", "Attempting relay - To: $to, SubId: ${smsManager.subscriptionId}")
            val parts = smsManager.divideMessage(message)

            val sentIntents = ArrayList<PendingIntent>()

            val sentIntent = PendingIntent.getBroadcast(
                context, 0, Intent("SMS_SENT"), PendingIntent.FLAG_IMMUTABLE
            )

            // Register receiver to see the result
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(arg0: Context?, arg1: Intent?) {
                    when (resultCode) {
                        android.app.Activity.RESULT_OK -> Log.d("SmsRelayWorker", "SMS Sent OK")
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> Log.e("SmsRelayWorker", "Generic failure")
                        SmsManager.RESULT_ERROR_NO_SERVICE -> Log.e("SmsRelayWorker", "No service")
                        SmsManager.RESULT_ERROR_NULL_PDU -> Log.e("SmsRelayWorker", "Null PDU")
                        SmsManager.RESULT_ERROR_RADIO_OFF -> Log.e("SmsRelayWorker", "Radio off")
                        else -> Log.e("SmsRelayWorker", "Failed with code: $resultCode")
                    }
                    context.applicationContext.unregisterReceiver(this)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(
                    receiver,
                    IntentFilter("SMS_SENT"),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.applicationContext.registerReceiver(receiver, IntentFilter("SMS_SENT"))
            }

            repeat(parts.size) {
                sentIntents.add(sentIntent)
            }
            
            smsManager.sendMultipartTextMessage(to, null, parts, sentIntents, null)
            Log.d("SmsRelayWorker", "Relay command sent to system")
        } catch (e: Exception) {
            Log.e("SmsRelayWorker", "Relay failed: ${e.message}", e)
        }
    }
}
