package com.commu.printeragent.data

enum class IpConfigStatus { Pending, Configured, Failed }
enum class TestPrintStatus { Pending, Verified, Failed }

/**
 * A locally-saved network printer entry.
 *
 * [id] is a stable UUID assigned at creation; never reused or reordered.
 * [lastCheckMs] is epoch milliseconds; 0 means the printer has never been probed.
 * [lastCheckOk] is the result of the most recent TCP connectivity probe —
 * it does NOT indicate the printer is currently online.
 * [ipConfigStatus] tracks whether the user has set a static IP for this printer.
 * [testPrintStatus] tracks whether a test print was physically confirmed.
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
    val ipConfigStatus: IpConfigStatus = IpConfigStatus.Pending,
    val testPrintStatus: TestPrintStatus = TestPrintStatus.Pending,
) {
    companion object {
        /**
         * Returns a copy of [original] with updated editable fields.
         * Resets connectivity check results when [address] or [port] change,
         * since a previous probe result is no longer valid for a different endpoint.
         * Also resets [ipConfigStatus] and [testPrintStatus] when the endpoint changes.
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
                name            = name,
                address         = address,
                port            = port,
                notes           = notes,
                lastCheckMs     = if (endpointChanged) 0L else original.lastCheckMs,
                lastCheckOk     = if (endpointChanged) false else original.lastCheckOk,
                ipConfigStatus  = if (endpointChanged) IpConfigStatus.Pending else original.ipConfigStatus,
                testPrintStatus = if (endpointChanged) TestPrintStatus.Pending else original.testPrintStatus,
            )
        }
    }
}
