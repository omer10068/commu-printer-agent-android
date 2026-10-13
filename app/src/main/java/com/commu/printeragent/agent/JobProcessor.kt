package com.commu.printeragent.agent

import com.commu.printeragent.data.PrintJob
import com.commu.printeragent.data.PrinterRecord
import com.commu.printeragent.printing.PrintResult

data class JobResult(
    val jobId: Int,
    val printerName: String,
    val isSuccess: Boolean,
    val message: String,
    /**
     * True when the backend PATCH confirming the outcome was acknowledged.
     * False when TCP transmission succeeded but the subsequent PATCH to "printed"
     * failed (e.g. transient network loss between send and report).
     *
     * When false, the caller should persist a [PendingPatch] so the PATCH can be
     * retried on the next cycle — without re-sending to the printer.
     */
    val patchReported: Boolean = true,
)

/**
 * Fetches queued print jobs from the backend, claims each one, sends it to the
 * correct local printer, and reports the outcome.
 *
 * All external dependencies are injected as suspend function parameters so the
 * class can be tested in a plain JVM environment without Android or a real printer.
 *
 * Processing is sequential within a single call — no job is re-queued if the
 * preceding one fails. The backend's claim transition is NOT atomic; if the
 * agent crashes after claiming but before reporting, the job stays claimed.
 * Callers must not treat a Transmitted result as proof of physical paper output.
 */
class JobProcessor(
    private val fetchQueue: suspend (
        baseUrl: String,
        tenantId: String,
        token: String,
        printerIds: List<Int>,
    ) -> Result<List<PrintJob>>,

    private val patchStatus: suspend (
        baseUrl: String,
        tenantId: String,
        token: String,
        jobId: Int,
        status: String,
        errorMessage: String?,
    ) -> Result<Unit>,

    private val send: suspend (
        address: String,
        port: Int,
        bytes: ByteArray,
    ) -> PrintResult,

    private val render: suspend (text: String) -> ByteArray,
) {

    /**
     * Processes all queued jobs reachable via [mappings].
     *
     * @param mappings  backendId → localPrinterUUID from [PrinterMappingRepository]
     * @param printers  all locally saved printers (used for address/port lookup)
     * @param onProgress  optional progress callback (called on the coroutine's dispatcher)
     * @throws Exception  if the queue cannot be fetched (caller shows the error to the user)
     */
    suspend fun processQueue(
        baseUrl: String,
        tenantId: String,
        token: String,
        mappings: Map<Int, String>,
        printers: List<PrinterRecord>,
        onProgress: (String) -> Unit = {},
    ): List<JobResult> {
        val backendIds = mappings.keys.toList()
        if (backendIds.isEmpty()) return emptyList()

        val jobsResult = fetchQueue(baseUrl, tenantId, token, backendIds)
        if (jobsResult.isFailure) {
            throw jobsResult.exceptionOrNull() ?: Exception("שגיאה בטעינת תור ההדפסה")
        }
        val jobs = jobsResult.getOrThrow()
        if (jobs.isEmpty()) return emptyList()

        val printerByUuid = printers.associateBy { it.id }
        val results = mutableListOf<JobResult>()

        for (job in jobs) {
            onProgress("עיבוד עבודה #${job.id} — ${job.printerName}")

            // Resolve backendId → local printer
            val localUuid = mappings[job.printerId]
            val printer = localUuid?.let { printerByUuid[it] }

            if (printer == null) {
                // No local printer for this job — claim and immediately fail so the job
                // does not stay stuck in "queued" for another agent that also cannot print it.
                val reason = "מדפסת לא ממופה (backendId=${job.printerId})"
                patchStatus(baseUrl, tenantId, token, job.id, "failed", reason)
                results.add(JobResult(job.id, job.printerName, false, reason))
                continue
            }

            // Claim before attempting to send — prevents another agent from double-processing.
            val claimResult = patchStatus(baseUrl, tenantId, token, job.id, "claimed", null)
            if (claimResult.isFailure) {
                // Claim failed: either a network glitch or another agent beat us. Skip silently.
                val reason = "לא ניתן לתבוע (${claimResult.exceptionOrNull()?.message})"
                results.add(JobResult(job.id, job.printerName, false, reason))
                continue
            }

            // Render receipt text to ESC/POS bytes
            val bytes = try {
                render(job.printableText)
            } catch (e: Exception) {
                val reason = "שגיאת עיבוד קבלה: ${e.message}".take(500)
                patchStatus(baseUrl, tenantId, token, job.id, "failed", reason)
                results.add(JobResult(job.id, job.printerName, false, reason))
                continue
            }

            // Send to the physical printer
            when (val sendResult = send(printer.address, printer.port, bytes)) {
                is PrintResult.Transmitted -> {
                    val patchResult = patchStatus(baseUrl, tenantId, token, job.id, "printed", null)
                    val reported = patchResult.isSuccess
                    val suffix = if (reported) "" else " ⚠ דיווח ממתין"
                    results.add(JobResult(
                        jobId = job.id,
                        printerName = job.printerName,
                        isSuccess = true,
                        patchReported = reported,
                        message = "✓ ${sendResult.byteCount} bytes → ${printer.address}:${printer.port}$suffix",
                    ))
                }
                is PrintResult.Failed -> {
                    val reason = sendResult.reason.take(500)
                    patchStatus(baseUrl, tenantId, token, job.id, "failed", reason)
                    results.add(JobResult(
                        jobId = job.id,
                        printerName = job.printerName,
                        isSuccess = false,
                        message = "✗ TCP: ${sendResult.reason}",
                    ))
                }
            }
        }

        return results
    }
}
