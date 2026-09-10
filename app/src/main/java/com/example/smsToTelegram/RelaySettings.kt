package com.example.smsToTelegram

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class RelaySettings(private val context: Context) {

    companion object {
        val FORWARDING_NUMBER = stringPreferencesKey("forwarding_number")
    }

    val forwardingNumber: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[FORWARDING_NUMBER]
    }

    suspend fun updateForwardingNumber(number: String) {
        context.dataStore.edit { preferences ->
            preferences[FORWARDING_NUMBER] = number
        }
    }
}
