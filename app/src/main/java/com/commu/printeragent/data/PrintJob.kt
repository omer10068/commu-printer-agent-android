package com.commu.printeragent.data

/**
 * Represents a single print job returned by GET agent-queue or PATCH status.
 *
 * Only the fields the agent actually needs are captured; additional backend fields
 * (orderId, timestamps, image URLs, etc.) are intentionally ignored.
 */
data class PrintJob(
    val id: Int,
    val printerId: Int,       // backend printer ID — used for routing via PrinterMappingRepository
    val printerName: String,
    val status: String,       // "queued" | "claimed" | "printed" | "failed"
    val printableText: String,
    val errorMessage: String?,
)
