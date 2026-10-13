package com.commu.printeragent.printing

import com.commu.printeragent.agent.JobProcessor
import com.commu.printeragent.data.IpConfigStatus
import com.commu.printeragent.data.PrintJob
import com.commu.printeragent.data.PrinterRecord
import com.commu.printeragent.data.TestPrintStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

/**
 * JVM TCP integration tests for two-printer routing.
 *
 * Each test binds real ServerSocket(0) endpoints on localhost (OS assigns ports),
 * runs the actual PrinterChannel.send() implementation, and verifies that bytes
 * reach the correct socket and that backend PATCH calls follow the correct sequence.
 *
 * A Transmitted result proves bytes reached the OS socket buffer — not physical paper.
 */
class TwoEndpointRoutingTest {

    data class PatchCall(val jobId: Int, val status: String, val errorMessage: String?)

    private fun job(id: Int, printerId: Int) = PrintJob(
        id            = id,
        printerId     = printerId,
        printerName   = "Printer-$printerId",
        status        = "queued",
        printableText = "JOB-$id-PRINTER-$printerId",
        errorMessage  = null,
    )

    private fun printer(localId: String, port: Int) = PrinterRecord(
        id              = localId,
        name            = "Printer $localId",
        address         = "localhost",
        port            = port,
        ipConfigStatus  = IpConfigStatus.Configured,
        testPrintStatus = TestPrintStatus.Verified,
    )

    private fun makeProcessor(
        jobs: List<PrintJob>,
        patches: MutableList<PatchCall>,
    ) = JobProcessor(
        fetchQueue  = { _, _, _, _ -> Result.success(jobs) },
        patchStatus = { _, _, _, jobId, status, errorMessage ->
            patches.add(PatchCall(jobId, status, errorMessage))
            Result.success(Unit)
        },
        send   = PrinterChannel::send,
        render = { text -> text.toByteArray(Charsets.UTF_8) },
    )

    /** Accepts exactly one connection from [server] and drains all bytes. */
    private fun captureOne(server: ServerSocket): ByteArray {
        server.soTimeout = 10_000
        return server.accept().use { it.getInputStream().readBytes() }
    }

    /** Accepts exactly [count] sequential connections from [server], returns payloads in order. */
    private fun captureMany(server: ServerSocket, count: Int): List<ByteArray> {
        server.soTimeout = 10_000
        return (1..count).map { server.accept().use { it.getInputStream().readBytes() } }
    }

    // ── Test 1 ───────────────────────────────────────────────────────────────

    @Test
    fun `each job routes to its assigned TCP endpoint`() = runTest {
        val serverA = ServerSocket(0); val portA = serverA.localPort
        val serverB = ServerSocket(0); val portB = serverB.localPort
        try {
            val deferredA = async(Dispatchers.IO) { captureOne(serverA) }
            val deferredB = async(Dispatchers.IO) { captureOne(serverB) }

            val patches = mutableListOf<PatchCall>()
            makeProcessor(listOf(job(1, 7), job(2, 8)), patches).processQueue(
                baseUrl   = "http://x", tenantId = "t", token = "tok",
                mappings  = mapOf(7 to "uuid-a", 8 to "uuid-b"),
                printers  = listOf(printer("uuid-a", portA), printer("uuid-b", portB)),
            )

            val textA = deferredA.await().toString(Charsets.UTF_8)
            val textB = deferredB.await().toString(Charsets.UTF_8)

            assertTrue("port A must receive job-1 payload",  textA.contains("JOB-1-PRINTER-7"))
            assertTrue("port B must receive job-2 payload",  textB.contains("JOB-2-PRINTER-8"))
            assertFalse("port A must not receive job-2 payload", textA.contains("JOB-2-PRINTER-8"))
            assertFalse("port B must not receive job-1 payload", textB.contains("JOB-1-PRINTER-7"))
        } finally {
            serverA.close(); serverB.close()
        }
    }

    // ── Test 2 ───────────────────────────────────────────────────────────────

    @Test
    fun `correct PATCH sequence per job - claimed then printed`() = runTest {
        val serverA = ServerSocket(0); val portA = serverA.localPort
        val serverB = ServerSocket(0); val portB = serverB.localPort
        try {
            val deferredA = async(Dispatchers.IO) { captureOne(serverA) }
            val deferredB = async(Dispatchers.IO) { captureOne(serverB) }

            val patches = mutableListOf<PatchCall>()
            makeProcessor(listOf(job(10, 7), job(11, 8)), patches).processQueue(
                baseUrl   = "http://x", tenantId = "t", token = "tok",
                mappings  = mapOf(7 to "uuid-a", 8 to "uuid-b"),
                printers  = listOf(printer("uuid-a", portA), printer("uuid-b", portB)),
            )

            deferredA.await()
            deferredB.await()

            assertEquals(4, patches.size)
            assertEquals(PatchCall(10, "claimed", null), patches[0])
            assertEquals(PatchCall(10, "printed", null), patches[1])
            assertEquals(PatchCall(11, "claimed", null), patches[2])
            assertEquals(PatchCall(11, "printed", null), patches[3])
        } finally {
            serverA.close(); serverB.close()
        }
    }

    // ── Test 3 ───────────────────────────────────────────────────────────────

    @Test
    fun `multiple jobs for same printer arrive in submission order`() = runTest {
        val serverA = ServerSocket(0); val portA = serverA.localPort
        val serverB = ServerSocket(0); val portB = serverB.localPort
        try {
            // printer A receives jobs 1 and 3; printer B receives job 2
            val deferredA = async(Dispatchers.IO) { captureMany(serverA, 2) }
            val deferredB = async(Dispatchers.IO) { captureOne(serverB) }

            val patches = mutableListOf<PatchCall>()
            makeProcessor(listOf(job(1, 7), job(2, 8), job(3, 7)), patches).processQueue(
                baseUrl   = "http://x", tenantId = "t", token = "tok",
                mappings  = mapOf(7 to "uuid-a", 8 to "uuid-b"),
                printers  = listOf(printer("uuid-a", portA), printer("uuid-b", portB)),
            )

            val payloadsA = deferredA.await().map { it.toString(Charsets.UTF_8) }
            val payloadB  = deferredB.await().toString(Charsets.UTF_8)

            assertEquals(2, payloadsA.size)
            assertTrue("first connection to A must be job 1",  payloadsA[0].contains("JOB-1-PRINTER-7"))
            assertTrue("second connection to A must be job 3", payloadsA[1].contains("JOB-3-PRINTER-7"))
            assertTrue("connection to B must be job 2",        payloadB.contains("JOB-2-PRINTER-8"))
            assertEquals("3 jobs × 2 PATCHes each = 6 total", 6, patches.size)
        } finally {
            serverA.close(); serverB.close()
        }
    }

    // ── Test 4 ───────────────────────────────────────────────────────────────

    @Test
    fun `unreachable printer B fails while printer A succeeds independently`() = runTest {
        val serverA = ServerSocket(0); val portA = serverA.localPort
        // Bind then immediately close — no listener, connection refused
        val portB = ServerSocket(0).use { it.localPort }
        try {
            val deferredA = async(Dispatchers.IO) { captureOne(serverA) }

            val patches = mutableListOf<PatchCall>()
            val results = makeProcessor(listOf(job(1, 7), job(2, 8)), patches).processQueue(
                baseUrl   = "http://x", tenantId = "t", token = "tok",
                mappings  = mapOf(7 to "uuid-a", 8 to "uuid-b"),
                printers  = listOf(printer("uuid-a", portA), printer("uuid-b", portB)),
            )

            deferredA.await()

            val resultA = results.first { it.jobId == 1 }
            val resultB = results.first { it.jobId == 2 }

            assertTrue("job 1 (reachable printer) must succeed",    resultA.isSuccess)
            assertFalse("job 2 (unreachable printer) must fail",     resultB.isSuccess)

            val patchesA = patches.filter { it.jobId == 1 }
            val patchesB = patches.filter { it.jobId == 2 }

            assertEquals(listOf(PatchCall(1, "claimed", null), PatchCall(1, "printed", null)), patchesA)
            assertEquals(2, patchesB.size)
            assertEquals("claimed", patchesB[0].status)
            assertEquals("failed",  patchesB[1].status)
            assertTrue(
                "failure PATCH must carry a non-blank errorMessage",
                patchesB[1].errorMessage?.isNotBlank() == true,
            )
        } finally {
            serverA.close()
        }
    }

    // ── Test 5 ───────────────────────────────────────────────────────────────

    @Test
    fun `transmitted result message cites byte count not physical paper output`() = runTest {
        val serverA = ServerSocket(0); val portA = serverA.localPort
        try {
            val deferredA = async(Dispatchers.IO) { captureOne(serverA) }

            val patches = mutableListOf<PatchCall>()
            val results = makeProcessor(listOf(job(1, 7)), patches).processQueue(
                baseUrl   = "http://x", tenantId = "t", token = "tok",
                mappings  = mapOf(7 to "uuid-a"),
                printers  = listOf(printer("uuid-a", portA)),
            )

            deferredA.await()

            val result = results.single()
            assertTrue("result must be a TCP-transmission success", result.isSuccess)
            assertTrue("message must reference byte count",         result.message.contains("bytes"))
            assertFalse(
                "message must not claim physical paper output",
                result.message.contains("paper", ignoreCase = true),
            )
            assertFalse(
                "message must not claim physical printing",
                result.message.contains("physical", ignoreCase = true),
            )
        } finally {
            serverA.close()
        }
    }
}
