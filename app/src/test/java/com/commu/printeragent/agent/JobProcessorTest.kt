package com.commu.printeragent.agent

import com.commu.printeragent.data.PrintJob
import com.commu.printeragent.data.PrinterRecord
import com.commu.printeragent.printing.PrintResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [JobProcessor] using lightweight fake dependencies.
 * No Android runtime, no real network, no real printer required.
 */
class JobProcessorTest {

    // ── Fake dependency builders ──────────────────────────────────────────────

    /** Mutable record of every patchStatus call: jobId → list of (status, errorMessage) pairs */
    private data class PatchCall(val jobId: Int, val status: String, val errorMessage: String?)

    private fun makeProcessor(
        queueJobs: List<PrintJob> = emptyList(),
        fetchFailure: Exception? = null,
        patchFailure: Exception? = null,
        sendResult: (address: String, port: Int) -> PrintResult = { _, _ -> PrintResult.Transmitted(100) },
        renderResult: (text: String) -> ByteArray = { byteArrayOf(1, 2, 3) },
        patchCalls: MutableList<PatchCall> = mutableListOf(),
        sentBytes: MutableList<Triple<String, Int, ByteArray>> = mutableListOf(),
    ) = JobProcessor(
        fetchQueue = { _, _, _, _ ->
            if (fetchFailure != null) Result.failure(fetchFailure)
            else Result.success(queueJobs)
        },
        patchStatus = { _, _, _, jobId, status, err ->
            patchCalls.add(PatchCall(jobId, status, err))
            if (patchFailure != null) Result.failure(patchFailure) else Result.success(Unit)
        },
        send = { addr, port, bytes ->
            sentBytes.add(Triple(addr, port, bytes))
            sendResult(addr, port)
        },
        render = { text -> renderResult(text) },
    )

    private fun job(id: Int, printerId: Int, text: String = "test receipt") =
        PrintJob(id, printerId, "Printer$printerId", "queued", text, null)

    private fun printer(localId: String, address: String, port: Int = 9100) =
        PrinterRecord(
            id = localId,
            name = "Printer $localId",
            address = address,
            port = port,
        )

    // ── Happy-path tests ──────────────────────────────────────────────────────

    @Test
    fun `single job is claimed then reported printed on success`() = runTest {
        val patches = mutableListOf<PatchCall>()
        val processor = makeProcessor(
            queueJobs = listOf(job(1, 1)),
            patchCalls = patches,
        )
        val results = processor.processQueue(
            baseUrl = "http://localhost",
            tenantId = "t",
            token = "tok",
            mappings = mapOf(1 to "uuid-a"),
            printers = listOf(printer("uuid-a", "192.168.1.10")),
        )

        assertEquals(1, results.size)
        assertTrue(results[0].isSuccess)
        assertEquals(2, patches.size)
        assertEquals("claimed", patches[0].status)
        assertEquals("printed", patches[1].status)
    }

    @Test
    fun `two printers with one job each are both processed`() = runTest {
        val patches = mutableListOf<PatchCall>()
        val sent = mutableListOf<Triple<String, Int, ByteArray>>()
        val processor = makeProcessor(
            queueJobs = listOf(job(1, 1), job(2, 2)),
            patchCalls = patches,
            sentBytes = sent,
        )
        val results = processor.processQueue(
            baseUrl = "http://localhost",
            tenantId = "t",
            token = "tok",
            mappings = mapOf(1 to "uuid-kitchen", 2 to "uuid-bar"),
            printers = listOf(
                printer("uuid-kitchen", "192.168.1.10"),
                printer("uuid-bar", "192.168.1.11"),
            ),
        )

        assertEquals(2, results.size)
        assertTrue(results.all { it.isSuccess })
        // Each job: claim + printed = 2 patches
        assertEquals(4, patches.size)
        // Each sent to its own IP
        assertEquals("192.168.1.10", sent[0].first)
        assertEquals("192.168.1.11", sent[1].first)
    }

    @Test
    fun `job routes to correct printer port`() = runTest {
        val sent = mutableListOf<Triple<String, Int, ByteArray>>()
        val processor = makeProcessor(
            queueJobs = listOf(job(1, 1)),
            sentBytes = sent,
        )
        processor.processQueue(
            "http://localhost", "t", "tok",
            mappings = mapOf(1 to "uuid-a"),
            printers = listOf(printer("uuid-a", "10.0.0.5", port = 9200)),
        )
        assertEquals(9200, sent[0].second)
    }

    // ── Empty-queue cases ─────────────────────────────────────────────────────

    @Test
    fun `empty queue returns empty results without any API call`() = runTest {
        val patches = mutableListOf<PatchCall>()
        val processor = makeProcessor(queueJobs = emptyList(), patchCalls = patches)
        val results = processor.processQueue(
            "http://localhost", "t", "tok",
            mappings = mapOf(1 to "uuid-a"),
            printers = listOf(printer("uuid-a", "10.0.0.1")),
        )
        assertTrue(results.isEmpty())
        assertTrue(patches.isEmpty())
    }

    @Test
    fun `no mappings returns empty results without fetching queue`() = runTest {
        var fetchCalled = false
        val processor = JobProcessor(
            fetchQueue = { _, _, _, _ -> fetchCalled = true; Result.success(emptyList()) },
            patchStatus = { _, _, _, _, _, _ -> Result.success(Unit) },
            send = { _, _, _ -> PrintResult.Transmitted(0) },
            render = { byteArrayOf() },
        )
        val results = processor.processQueue(
            "http://localhost", "t", "tok",
            mappings = emptyMap(),
            printers = emptyList(),
        )
        assertTrue(results.isEmpty())
        assertFalse(fetchCalled)
    }

    // ── Failure cases ─────────────────────────────────────────────────────────

    @Test
    fun `fetch failure propagates as exception`() = runTest {
        val processor = makeProcessor(fetchFailure = Exception("network timeout"))
        var threw = false
        try {
            processor.processQueue("http://localhost", "t", "tok", mapOf(1 to "u"), listOf())
        } catch (e: Exception) {
            threw = true
            assertTrue(e.message?.contains("network timeout") == true)
        }
        assertTrue(threw)
    }

    @Test
    fun `unmapped printerId is marked failed without sending`() = runTest {
        val patches = mutableListOf<PatchCall>()
        val sent = mutableListOf<Triple<String, Int, ByteArray>>()
        val processor = makeProcessor(
            queueJobs = listOf(job(1, 99)),  // printerId=99 not in mappings
            patchCalls = patches,
            sentBytes = sent,
        )
        val results = processor.processQueue(
            "http://localhost", "t", "tok",
            mappings = mapOf(1 to "uuid-a"),            // only backendId=1 mapped
            printers = listOf(printer("uuid-a", "10.0.0.1")),
        )

        assertEquals(1, results.size)
        assertFalse(results[0].isSuccess)
        assertTrue(results[0].message.contains("backendId=99"))
        // Should have patched to failed (not claimed first)
        assertEquals(1, patches.size)
        assertEquals("failed", patches[0].status)
        assertTrue(sent.isEmpty())  // no TCP attempt
    }

    @Test
    fun `claim failure skips send and does not patch printed`() = runTest {
        val patches = mutableListOf<PatchCall>()
        val sent = mutableListOf<Triple<String, Int, ByteArray>>()
        val processor = makeProcessor(
            queueJobs = listOf(job(1, 1)),
            patchFailure = Exception("claim rejected"),  // all patches fail
            patchCalls = patches,
            sentBytes = sent,
        )
        val results = processor.processQueue(
            "http://localhost", "t", "tok",
            mappings = mapOf(1 to "uuid-a"),
            printers = listOf(printer("uuid-a", "10.0.0.1")),
        )

        assertEquals(1, results.size)
        assertFalse(results[0].isSuccess)
        assertTrue(sent.isEmpty())  // no TCP attempt after failed claim
    }

    @Test
    fun `TCP send failure patches job as failed with reason`() = runTest {
        val patches = mutableListOf<PatchCall>()
        val processor = makeProcessor(
            queueJobs = listOf(job(1, 1)),
            sendResult = { _, _ -> PrintResult.Failed("Connection refused") },
            patchCalls = patches,
        )
        val results = processor.processQueue(
            "http://localhost", "t", "tok",
            mappings = mapOf(1 to "uuid-a"),
            printers = listOf(printer("uuid-a", "10.0.0.1")),
        )

        assertEquals(1, results.size)
        assertFalse(results[0].isSuccess)
        assertTrue(results[0].message.contains("Connection refused"))
        // claimed then failed
        assertEquals(2, patches.size)
        assertEquals("claimed", patches[0].status)
        assertEquals("failed", patches[1].status)
        assertEquals("Connection refused", patches[1].errorMessage)
    }

    @Test
    fun `render failure patches job as failed and continues to next job`() = runTest {
        val patches = mutableListOf<PatchCall>()
        val sent = mutableListOf<Triple<String, Int, ByteArray>>()
        var renderCount = 0
        val processor = JobProcessor(
            fetchQueue = { _, _, _, _ -> Result.success(listOf(job(1, 1), job(2, 1))) },
            patchStatus = { _, _, _, id, status, err ->
                patches.add(PatchCall(id, status, err))
                Result.success(Unit)
            },
            send = { addr, port, bytes ->
                sent.add(Triple(addr, port, bytes))
                PrintResult.Transmitted(bytes.size)
            },
            render = { text ->
                renderCount++
                if (renderCount == 1) throw RuntimeException("render error")
                byteArrayOf(1, 2, 3)
            },
        )
        val results = processor.processQueue(
            "http://localhost", "t", "tok",
            mappings = mapOf(1 to "uuid-a"),
            printers = listOf(printer("uuid-a", "10.0.0.1")),
        )

        assertEquals(2, results.size)
        assertFalse(results[0].isSuccess)  // job 1 failed render
        assertTrue(results[1].isSuccess)   // job 2 succeeded
        assertTrue(sent.size == 1)         // only job 2 was sent
    }

    // ── Two-printer routing correctness ───────────────────────────────────────

    @Test
    fun `three jobs on two printers route to correct endpoints`() = runTest {
        val sent = mutableListOf<Triple<String, Int, ByteArray>>()
        val processor = makeProcessor(
            queueJobs = listOf(job(1, 1), job(2, 2), job(3, 1)),
            sentBytes = sent,
        )
        processor.processQueue(
            "http://localhost", "t", "tok",
            mappings = mapOf(1 to "uuid-kitchen", 2 to "uuid-bar"),
            printers = listOf(
                printer("uuid-kitchen", "192.168.1.10", 9100),
                printer("uuid-bar",     "192.168.1.11", 9200),
            ),
        )
        assertEquals(3, sent.size)
        assertEquals("192.168.1.10", sent[0].first); assertEquals(9100, sent[0].second)
        assertEquals("192.168.1.11", sent[1].first); assertEquals(9200, sent[1].second)
        assertEquals("192.168.1.10", sent[2].first); assertEquals(9100, sent[2].second)
    }

    // ── Hebrew content ────────────────────────────────────────────────────────

    @Test
    fun `Hebrew printableText is passed verbatim to render function`() = runTest {
        val hebrewReceipt = "מסעדת הדגמה\nהזמנה #123\nסה״כ: 85.00 ₪"
        var capturedText = ""
        val processor = JobProcessor(
            fetchQueue = { _, _, _, _ -> Result.success(listOf(job(1, 1, hebrewReceipt))) },
            patchStatus = { _, _, _, _, _, _ -> Result.success(Unit) },
            send = { _, _, bytes -> PrintResult.Transmitted(bytes.size) },
            render = { text -> capturedText = text; byteArrayOf(0x1B, 0x40) },
        )
        processor.processQueue(
            "http://localhost", "t", "tok",
            mappings = mapOf(1 to "uuid-a"),
            printers = listOf(printer("uuid-a", "10.0.0.1")),
        )
        assertEquals(hebrewReceipt, capturedText)
    }

    // ── PATCH best-effort after successful send ───────────────────────────────

    @Test
    fun `result is still counted as success even if PATCH printed fails`() = runTest {
        var patchCount = 0
        val processor = JobProcessor(
            fetchQueue = { _, _, _, _ -> Result.success(listOf(job(1, 1))) },
            patchStatus = { _, _, _, _, status, _ ->
                patchCount++
                if (status == "claimed") Result.success(Unit) // claim succeeds
                else Result.failure(Exception("server unreachable"))  // printed patch fails
            },
            send = { _, _, bytes -> PrintResult.Transmitted(bytes.size) },
            render = { byteArrayOf(1) },
        )
        val results = processor.processQueue(
            "http://localhost", "t", "tok",
            mappings = mapOf(1 to "uuid-a"),
            printers = listOf(printer("uuid-a", "10.0.0.1")),
        )
        // TCP Transmitted means we count it as success — PATCH failure is best-effort
        assertEquals(1, results.size)
        assertTrue(results[0].isSuccess)
        assertTrue(results[0].message.contains("bytes"))
    }

    // ── errorMessage truncation ───────────────────────────────────────────────

    @Test
    fun `TCP failure reason is included in PATCH errorMessage`() = runTest {
        val patches = mutableListOf<PatchCall>()
        val processor = makeProcessor(
            queueJobs = listOf(job(1, 1)),
            sendResult = { _, _ -> PrintResult.Failed("ECONNREFUSED port 9100") },
            patchCalls = patches,
        )
        processor.processQueue(
            "http://localhost", "t", "tok",
            mappings = mapOf(1 to "uuid-a"),
            printers = listOf(printer("uuid-a", "10.0.0.1")),
        )
        val failedPatch = patches.first { it.status == "failed" }
        assertTrue(failedPatch.errorMessage?.contains("ECONNREFUSED") == true)
    }

    // ── patchReported flag ────────────────────────────────────────────────────

    @Test
    fun `patchReported is false when printed PATCH fails after TCP success`() = runTest {
        val processor = JobProcessor(
            fetchQueue = { _, _, _, _ -> Result.success(listOf(job(1, 1))) },
            patchStatus = { _, _, _, _, status, _ ->
                if (status == "claimed") Result.success(Unit)
                else Result.failure(Exception("backend unavailable"))
            },
            send = { _, _, bytes -> PrintResult.Transmitted(bytes.size) },
            render = { byteArrayOf(1) },
        )
        val results = processor.processQueue(
            "http://localhost", "t", "tok",
            mappings = mapOf(1 to "uuid-a"),
            printers = listOf(printer("uuid-a", "10.0.0.1")),
        )
        assertEquals(1, results.size)
        assertTrue(results[0].isSuccess)          // TCP succeeded
        assertFalse(results[0].patchReported)      // PATCH failed
        assertTrue(results[0].message.contains("⚠")) // warning suffix present
    }

    // ── onProgress callback ───────────────────────────────────────────────────

    @Test
    fun `onProgress is called once per job`() = runTest {
        val progressMessages = mutableListOf<String>()
        val processor = makeProcessor(queueJobs = listOf(job(1, 1), job(2, 1)))
        processor.processQueue(
            "http://localhost", "t", "tok",
            mappings = mapOf(1 to "uuid-a"),
            printers = listOf(printer("uuid-a", "10.0.0.1")),
            onProgress = { progressMessages.add(it) },
        )
        assertEquals(2, progressMessages.size)
        assertTrue(progressMessages[0].contains("#1"))
        assertTrue(progressMessages[1].contains("#2"))
    }
}
