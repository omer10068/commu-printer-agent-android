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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private val Context.pendingPatchStore: DataStore<Preferences> by preferencesDataStore(name = "pending_patches")

/**
 * A job whose TCP transmission succeeded but whose backend PATCH (to "printed" or "failed")
 * was not confirmed by the server in time.
 *
 * On the next polling cycle the agent retries only the PATCH — never the TCP send.
 * This ensures a given set of ESC/POS bytes is sent to the printer at most once.
 */
data class PendingPatch(
    val jobId: Int,
    val status: String,         // "printed" or "failed"
    val errorMessage: String?,  // non-null only for "failed"
)

/**
 * DataStore-backed store for [PendingPatch] entries.
 *
 * [encode] / [decode] are pure functions exposed for JVM unit testing.
 */
class PendingStatusRepository(context: Context) {

    private val ctx = context.applicationContext
    private val KEY = stringPreferencesKey("items")

    companion object {
        /** Serialises [items] to a JSON string. */
        fun encode(items: List<PendingPatch>): String {
            val arr = JSONArray()
            for (item in items) {
                val obj = JSONObject()
                obj.put("jobId", item.jobId)
                obj.put("status", item.status)
                if (item.errorMessage != null) obj.put("errorMessage", item.errorMessage)
                arr.put(obj)
            }
            return arr.toString()
        }

        /** Parses a JSON string produced by [encode]. Silently drops malformed entries. */
        fun decode(raw: String): List<PendingPatch> {
            if (raw.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                    val jobId = obj.optInt("jobId", -1).takeIf { it >= 0 } ?: return@mapNotNull null
                    val status = obj.optString("status").takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val errorMessage = if (obj.has("errorMessage") && !obj.isNull("errorMessage"))
                        obj.getString("errorMessage") else null
                    PendingPatch(jobId, status, errorMessage)
                }
            } catch (_: JSONException) {
                emptyList()
            }
        }
    }

    /** Adds or replaces the patch for [patch.jobId] (idempotent on duplicate jobId). */
    suspend fun add(patch: PendingPatch): Unit = withContext(Dispatchers.IO) {
        ctx.pendingPatchStore.edit { prefs ->
            val current = decode(prefs[KEY] ?: "")
            val updated = current.filterNot { it.jobId == patch.jobId } + patch
            prefs[KEY] = encode(updated)
        }
    }

    /** Removes the pending patch for [jobId] after a successful retry. */
    suspend fun remove(jobId: Int): Unit = withContext(Dispatchers.IO) {
        ctx.pendingPatchStore.edit { prefs ->
            val current = decode(prefs[KEY] ?: "")
            prefs[KEY] = encode(current.filterNot { it.jobId == jobId })
        }
    }

    suspend fun loadAll(): List<PendingPatch> = withContext(Dispatchers.IO) {
        decode(ctx.pendingPatchStore.data.first()[KEY] ?: "")
    }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        ctx.pendingPatchStore.edit { prefs -> prefs[KEY] = "" }
    }
}
