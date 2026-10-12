package com.commu.printeragent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.mappingStore: DataStore<Preferences> by preferencesDataStore(name = "printer_mappings")

/**
 * Persists the backend-printer-ID → local-printer-UUID routing table.
 *
 * Storage: single DataStore key "mappings" using a compact text format:
 *   "backendId1:localUUID1,backendId2:localUUID2"
 *
 * [encode] and [decode] are pure functions — unit-testable without Android.
 */
class PrinterMappingRepository(context: Context) {

    private val ctx = context.applicationContext

    companion object {
        val KEY_MAPPINGS = stringPreferencesKey("mappings")

        fun encode(mappings: Map<Int, String>): String =
            mappings.entries.joinToString(",") { "${it.key}:${it.value}" }

        fun decode(raw: String): Map<Int, String> {
            if (raw.isBlank()) return emptyMap()
            return raw.split(",").mapNotNull { entry ->
                val colon = entry.indexOf(':')
                if (colon < 1) return@mapNotNull null
                val backendId = entry.substring(0, colon).toIntOrNull() ?: return@mapNotNull null
                val localId = entry.substring(colon + 1).trim()
                if (localId.isBlank()) null else backendId to localId
            }.toMap()
        }
    }

    suspend fun saveAll(mappings: Map<Int, String>): Unit = withContext(Dispatchers.IO) {
        ctx.mappingStore.edit { prefs -> prefs[KEY_MAPPINGS] = encode(mappings) }
    }

    suspend fun loadAll(): Map<Int, String> = withContext(Dispatchers.IO) {
        decode(ctx.mappingStore.data.first()[KEY_MAPPINGS] ?: "")
    }

    suspend fun removeByLocalId(localId: String): Unit = withContext(Dispatchers.IO) {
        ctx.mappingStore.edit { prefs ->
            val current = decode(prefs[KEY_MAPPINGS] ?: "")
            val updated = current.filterValues { it != localId }
            prefs[KEY_MAPPINGS] = encode(updated)
        }
    }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        ctx.mappingStore.edit { prefs -> prefs[KEY_MAPPINGS] = "" }
    }
}
