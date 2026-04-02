package com.croakvpn.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "croak_prefs")

class PrefsManager(private val context: Context) {

    private val configDir: File
        get() = context.filesDir.also { it.mkdirs() }

    val singboxConfigFile: File
        get() = File(configDir, "config.json")

    // Keys
    private val KEY_SUBSCRIPTION_URL = stringPreferencesKey("subscription_url")
    private val KEY_AUTO_CONNECT      = booleanPreferencesKey("auto_connect")

    // Flows
    val subscriptionUrlFlow: Flow<String?> = context.dataStore.data
        .map { it[KEY_SUBSCRIPTION_URL]?.takeIf { s -> s.isNotEmpty() } }

    val autoConnectFlow: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_AUTO_CONNECT] ?: false }

    val hasSubscription: Flow<Boolean> = subscriptionUrlFlow.map { it != null }

    suspend fun getSubscriptionUrl(): String? =
        context.dataStore.data.first()[KEY_SUBSCRIPTION_URL]?.takeIf { it.isNotEmpty() }

    suspend fun saveSubscription(url: String, singboxConfig: String) {
        context.dataStore.edit { it[KEY_SUBSCRIPTION_URL] = url }
        singboxConfigFile.writeText(singboxConfig)
    }

    suspend fun clearSubscription() {
        context.dataStore.edit { it.remove(KEY_SUBSCRIPTION_URL) }
        singboxConfigFile.delete()
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_CONNECT] = enabled }
    }

    fun hasSingboxConfig(): Boolean = singboxConfigFile.exists()

    fun getSingboxConfigPath(): String = singboxConfigFile.absolutePath
}
