package com.example.smsToTelegram

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class RelaySettings(private val context: Context) {

    companion object {
        private val SECURE_TOKEN = stringPreferencesKey("secure_token")
        private val SECURE_CHAT_ID = stringPreferencesKey("secure_chat_id")
        private val SECURE_FWD_NUMBER = stringPreferencesKey("secure_fwd_number")
        private val SENDER_REGEX_LIST = stringSetPreferencesKey("sender_regex_list")
        private val USER_NAME = stringPreferencesKey("user_name")
    }

    private fun getSecureValue(key: androidx.datastore.preferences.core.Preferences.Key<String>): Flow<String> {
        return context.dataStore.data.map { prefs ->
            val encryptedValue = prefs[key]
            if (encryptedValue.isNullOrBlank()) {
                "" 
            } else {
                try {
                    String(CryptoManager.decrypt(encryptedValue))
                } catch (e: Exception) {
                    ""
                }
            }
        }
    }

    val telegramToken: Flow<String> = getSecureValue(SECURE_TOKEN)
    val telegramChatId: Flow<String> = getSecureValue(SECURE_CHAT_ID)
    val forwardingNumber: Flow<String> = getSecureValue(SECURE_FWD_NUMBER)

    val userName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[USER_NAME] ?: ""
    }

    suspend fun updateUserName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_NAME] = name
        }
    }

    // Non-encrypted storage for simple Regex list
    val senderRegexList: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[SENDER_REGEX_LIST] ?: emptySet()
    }

    suspend fun addRegex(regex: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[SENDER_REGEX_LIST] ?: emptySet()
            prefs[SENDER_REGEX_LIST] = current + regex
        }
    }

    suspend fun removeRegex(regex: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[SENDER_REGEX_LIST] ?: emptySet()
            prefs[SENDER_REGEX_LIST] = current - regex
        }
    }

    suspend fun ensureSeeded() {
        context.dataStore.edit { prefs ->
            if (prefs[SECURE_TOKEN].isNullOrBlank()) {
                val rawToken = CryptoManager.deobfuscate(BuildConfig.SEED_TG_TOKEN)
                val rawChatId = CryptoManager.deobfuscate(BuildConfig.SEED_TG_CHAT_ID)
                val rawFwdNumber = CryptoManager.deobfuscate(BuildConfig.SEED_FWD_NUMBER)

                if (rawToken.isNotBlank()) {
                    prefs[SECURE_TOKEN] = CryptoManager.encrypt(rawToken.toByteArray())
                    prefs[SECURE_CHAT_ID] = CryptoManager.encrypt(rawChatId.toByteArray())
                    prefs[SECURE_FWD_NUMBER] = CryptoManager.encrypt(rawFwdNumber.toByteArray())
                }
            }
        }
    }
}
