package com.commu.printeragent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.printerStore: DataStore<Preferences> by preferencesDataStore(name = "printer_list")

/**
 * Persistent storage for [PrinterRecord] entries using DataStore Preferences.
 *
 * Storage layout — all values are strings:
 *   "printer_ids"          → comma-separated list of UUIDs (insertion order)
 *   "p_<id>_name"          → friendly name
 *   "p_<id>_addr"          → IPv4 address
 *   "p_<id>_port"          → TCP port (integer as string)
 *   "p_<id>_notes"         → optional notes
 *   "p_<id>_primary"       → "true" / "false"
 *   "p_<id>_check_ms"      → epoch ms of last TCP probe (long as string)
 *   "p_<id>_check_ok"      → "true" / "false"
 *   "p_<id>_ip_config"     → IpConfigStatus name
 *   "p_<id>_test_print"    → TestPrintStatus name
 *
 * All coroutine operations run on Dispatchers.IO.
 */
class PrinterRepository(context: Context) {

    private val ctx = context.applicationContext

    // ── Key helpers — public so tests can verify naming conventions ──────────

    companion object {
        val KEY_IDS = stringPreferencesKey("printer_ids")

        fun keyName(id: String)      = stringPreferencesKey("p_${id}_name")
        fun keyAddress(id: String)   = stringPreferencesKey("p_${id}_addr")
        fun keyPort(id: String)      = stringPreferencesKey("p_${id}_port")
        fun keyNotes(id: String)     = stringPreferencesKey("p_${id}_notes")
        fun keyPrimary(id: String)   = stringPreferencesKey("p_${id}_primary")
        fun keyCheckMs(id: String)   = stringPreferencesKey("p_${id}_check_ms")
        fun keyCheckOk(id: String)   = stringPreferencesKey("p_${id}_check_ok")
        fun keyIpConfig(id: String)  = stringPreferencesKey("p_${id}_ip_config")
        fun keyTestPrint(id: String) = stringPreferencesKey("p_${id}_test_print")

        /** Parses a comma-separated ID string into a list, ignoring blank entries. */
        fun parseIdList(raw: String): List<String> =
            if (raw.isBlank()) emptyList()
            else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }

        /** Encodes a list of IDs back to the stored comma-separated format. */
        fun formatIdList(ids: List<String>): String = ids.joinToString(",")
    }

    // ── Reactive flow ────────────────────────────────────────────────────────

    /** Emits the full printer list whenever storage changes. */
    val printerFlow: Flow<List<PrinterRecord>>
        get() = ctx.printerStore.data.map { prefs ->
            val ids = parseIdList(prefs[KEY_IDS] ?: "")
            ids.mapNotNull { id ->
                val name    = prefs[keyName(id)]    ?: return@mapNotNull null
                val address = prefs[keyAddress(id)] ?: return@mapNotNull null
                PrinterRecord(
                    id              = id,
                    name            = name,
                    address         = address,
                    port            = prefs[keyPort(id)]?.toIntOrNull() ?: 9100,
                    notes           = prefs[keyNotes(id)] ?: "",
                    isPrimary       = prefs[keyPrimary(id)] == "true",
                    lastCheckMs     = prefs[keyCheckMs(id)]?.toLongOrNull() ?: 0L,
                    lastCheckOk     = prefs[keyCheckOk(id)] == "true",
                    ipConfigStatus  = runCatching {
                        IpConfigStatus.valueOf(prefs[keyIpConfig(id)] ?: "Pending")
                    }.getOrDefault(IpConfigStatus.Pending),
                    testPrintStatus = runCatching {
                        TestPrintStatus.valueOf(prefs[keyTestPrint(id)] ?: "Pending")
                    }.getOrDefault(TestPrintStatus.Pending),
                )
            }
        }

    // ── Read ─────────────────────────────────────────────────────────────────

    /** Loads all saved printers in insertion order. Skips entries with missing required fields. */
    suspend fun loadAll(): List<PrinterRecord> = withContext(Dispatchers.IO) {
        val prefs = ctx.printerStore.data.first()
        val ids = parseIdList(prefs[KEY_IDS] ?: "")
        ids.mapNotNull { id ->
            val name    = prefs[keyName(id)]    ?: return@mapNotNull null
            val address = prefs[keyAddress(id)] ?: return@mapNotNull null
            PrinterRecord(
                id              = id,
                name            = name,
                address         = address,
                port            = prefs[keyPort(id)]?.toIntOrNull() ?: 9100,
                notes           = prefs[keyNotes(id)] ?: "",
                isPrimary       = prefs[keyPrimary(id)] == "true",
                lastCheckMs     = prefs[keyCheckMs(id)]?.toLongOrNull() ?: 0L,
                lastCheckOk     = prefs[keyCheckOk(id)] == "true",
                ipConfigStatus  = runCatching {
                    IpConfigStatus.valueOf(prefs[keyIpConfig(id)] ?: "Pending")
                }.getOrDefault(IpConfigStatus.Pending),
                testPrintStatus = runCatching {
                    TestPrintStatus.valueOf(prefs[keyTestPrint(id)] ?: "Pending")
                }.getOrDefault(TestPrintStatus.Pending),
            )
        }
    }

    // ── Write ────────────────────────────────────────────────────────────────

    /** Inserts or fully replaces a printer record. Adds the ID to the list if new. */
    suspend fun save(record: PrinterRecord): Unit = withContext(Dispatchers.IO) {
        ctx.printerStore.edit { prefs ->
            val ids = parseIdList(prefs[KEY_IDS] ?: "").toMutableList()
            if (record.id !in ids) ids.add(record.id)
            prefs[KEY_IDS]                  = formatIdList(ids)
            prefs[keyName(record.id)]       = record.name
            prefs[keyAddress(record.id)]    = record.address
            prefs[keyPort(record.id)]       = record.port.toString()
            prefs[keyNotes(record.id)]      = record.notes
            prefs[keyPrimary(record.id)]    = record.isPrimary.toString()
            prefs[keyCheckMs(record.id)]    = record.lastCheckMs.toString()
            prefs[keyCheckOk(record.id)]    = record.lastCheckOk.toString()
            prefs[keyIpConfig(record.id)]   = record.ipConfigStatus.name
            prefs[keyTestPrint(record.id)]  = record.testPrintStatus.name
        }
    }

    /** Removes a printer and all its stored keys. No-op if the ID is not present. */
    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        ctx.printerStore.edit { prefs ->
            val ids = parseIdList(prefs[KEY_IDS] ?: "").filter { it != id }
            prefs[KEY_IDS] = formatIdList(ids)
            prefs.remove(keyName(id))
            prefs.remove(keyAddress(id))
            prefs.remove(keyPort(id))
            prefs.remove(keyNotes(id))
            prefs.remove(keyPrimary(id))
            prefs.remove(keyCheckMs(id))
            prefs.remove(keyCheckOk(id))
            prefs.remove(keyIpConfig(id))
            prefs.remove(keyTestPrint(id))
        }
    }

    /**
     * Atomically sets [primaryId] as the primary printer and clears the flag on all others.
     * Uses a single DataStore edit transaction to avoid partial writes.
     * No-op for IDs not present in the stored list.
     */
    suspend fun setPrimary(primaryId: String): Unit = withContext(Dispatchers.IO) {
        ctx.printerStore.edit { prefs ->
            val ids = parseIdList(prefs[KEY_IDS] ?: "")
            ids.forEach { id -> prefs[keyPrimary(id)] = (id == primaryId).toString() }
        }
    }

    /** Updates only the connectivity check result fields for an existing printer. */
    suspend fun updateCheckResult(id: String, ok: Boolean, timestampMs: Long): Unit =
        withContext(Dispatchers.IO) {
            ctx.printerStore.edit { prefs ->
                prefs[keyCheckMs(id)] = timestampMs.toString()
                prefs[keyCheckOk(id)] = ok.toString()
            }
        }

    /** Updates the IP configuration status for an existing printer. */
    suspend fun updateIpConfigStatus(id: String, status: IpConfigStatus): Unit =
        withContext(Dispatchers.IO) {
            ctx.printerStore.edit { prefs ->
                prefs[keyIpConfig(id)] = status.name
            }
        }

    /** Updates the test print status for an existing printer. */
    suspend fun updateTestPrintStatus(id: String, status: TestPrintStatus): Unit =
        withContext(Dispatchers.IO) {
            ctx.printerStore.edit { prefs ->
                prefs[keyTestPrint(id)] = status.name
            }
        }
}
