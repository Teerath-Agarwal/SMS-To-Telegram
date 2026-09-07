package com.example.sms_relay

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class RelaySettings(private val context: Context) {

    companion object {
        val FORWARDING_NUMBER = stringPreferencesKey("forwarding_number")
        val SENDER_REGEX_LIST = stringSetPreferencesKey("sender_regex_list")
    }

    val forwardingNumber: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[FORWARDING_NUMBER]
    }

    val senderRegexList: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[SENDER_REGEX_LIST] ?: emptySet()
    }

    suspend fun updateForwardingNumber(number: String) {
        context.dataStore.edit { preferences ->
            preferences[FORWARDING_NUMBER] = number
        }
    }

    suspend fun addRegex(regex: String) {
        context.dataStore.edit { preferences ->
            val currentSet = preferences[SENDER_REGEX_LIST] ?: emptySet()
            preferences[SENDER_REGEX_LIST] = currentSet + regex
        }
    }

    suspend fun removeRegex(regex: String) {
        context.dataStore.edit { preferences ->
            val currentSet = preferences[SENDER_REGEX_LIST] ?: emptySet()
            preferences[SENDER_REGEX_LIST] = currentSet - regex
        }
    }
}
