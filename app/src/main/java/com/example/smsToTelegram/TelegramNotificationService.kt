package com.example.smsToTelegram

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object TelegramNotificationService {

    private const val TAG = "TelegramService"
    private const val TELEGRAM_API = "https://api.telegram.org/bot"

    /**
     * Sends a text message to the configured Telegram chat.
     */
    suspend fun sendMessage(message: String): Boolean = withContext(Dispatchers.IO) {
        if (RelayConfig.TELEGRAM_BOT_TOKEN == "DUMMY_BOT_TOKEN" ||
            RelayConfig.TELEGRAM_CHAT_ID == "DUMMY_CHAT_ID") {
            Log.e(TAG, "Telegram config not set in RelayConfig.kt")
            return@withContext false
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL("$TELEGRAM_API${RelayConfig.TELEGRAM_BOT_TOKEN}/sendMessage")

            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val requestBody = JSONObject().apply {
                put("chat_id", RelayConfig.TELEGRAM_CHAT_ID)
                put("text", message)
            }.toString()

            connection.outputStream.use { output ->
                output.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode

            if (responseCode in 200..299) {
                Log.d(TAG, "Telegram message sent successfully")
                true
            } else {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                } catch (_: Exception) {
                    null
                }
                Log.e(TAG, "Telegram API error: HTTP $responseCode, response=$errorBody")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Telegram message", e)
            false
        } finally {
            connection?.disconnect()
        }
    }
}
