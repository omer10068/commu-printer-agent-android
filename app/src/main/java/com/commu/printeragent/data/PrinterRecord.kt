package com.commu.printeragent.data

/**
 * A locally-saved network printer entry.
 *
 * [id] is a stable UUID assigned at creation; never reused or reordered.
 * [lastCheckMs] is epoch milliseconds; 0 means the printer has never been probed.
 * [lastCheckOk] is the result of the most recent TCP connectivity probe —
 * it does NOT indicate the printer is currently online.
 */
data class PrinterRecord(
    val id: String,
    val name: String,
    val address: String,
    val port: Int = 9100,
    val notes: String = "",
    val isPrimary: Boolean = false,
    val lastCheckMs: Long = 0L,
    val lastCheckOk: Boolean = false,
) {
    companion object {
        /**
         * Returns a copy of [original] with updated editable fields.
         * Resets connectivity check results when [address] or [port] change,
         * since a previous probe result is no longer valid for a different endpoint.
         */
        fun applyEdit(
            original: PrinterRecord,
            name: String,
            address: String,
            port: Int,
            notes: String,
        ): PrinterRecord {
            val endpointChanged = original.address != address || original.port != port
            return original.copy(
                name        = name,
                address     = address,
                port        = port,
                notes       = notes,
                lastCheckMs = if (endpointChanged) 0L else original.lastCheckMs,
                lastCheckOk = if (endpointChanged) false else original.lastCheckOk,
            )
        }
    }
}
