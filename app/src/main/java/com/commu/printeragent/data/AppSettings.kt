package com.commu.printeragent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.appSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppSettings(private val context: Context) {

    private val keyBackendUrl = stringPreferencesKey("backend_url")
    private val keyTenantId = stringPreferencesKey("tenant_id")

    suspend fun getBackendUrl(): String? =
        context.applicationContext.appSettingsStore.data.first()[keyBackendUrl]

    suspend fun getTenantId(): String? =
        context.applicationContext.appSettingsStore.data.first()[keyTenantId]

    suspend fun save(backendUrl: String, tenantId: String) {
        context.applicationContext.appSettingsStore.edit { prefs ->
            prefs[keyBackendUrl] = backendUrl
            prefs[keyTenantId] = tenantId
        }
    }
}
