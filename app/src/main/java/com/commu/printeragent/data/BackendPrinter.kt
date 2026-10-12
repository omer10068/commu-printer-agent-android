package com.commu.printeragent.data

/**
 * A printer registered on the backend for this tenant.
 * [id] is the backend-assigned integer, stable across sessions.
 * [name] must match the local [PrinterRecord.name] for sync routing to work.
 */
data class BackendPrinter(val id: Int, val name: String)
