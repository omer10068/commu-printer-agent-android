package com.commu.printeragent.agent

import com.commu.printeragent.data.PrinterRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PrintAgentLoop] using virtual-time control from kotlinx-coroutines-test.
 *
 * No Android runtime, no real network, no real printer required.
 * [PrintAgentLoop.delayFn] defaults to [kotlinx.coroutines.delay] which hooks into the
 * [runTest] virtual clock automatically — no custom injection needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrintAgentLoopTest {

    private fun makeConfig() = PrintAgentLoop.LoopConfig(
        baseUrl = "http://localhost",
        tenantId = "tenant",
        token = "tok",
        mappings = mapOf(1 to "uuid-a"),
        printers = listOf(PrinterRecord("uuid-a", "Test Printer", "10.0.0.1")),
    )

    // ── Basic lifecycle ───────────────────────────────────────────────────────

    @Test
    fun `start triggers first polling cycle`() = runTest {
        var cycleCount = 0
        val loop = PrintAgentLoop(
            loadConfig = { makeConfig() },
            runCycle = { _, _ -> cycleCount++; PrintAgentLoop.CycleResult.Empty },
            onStatus = {},
            normalIntervalMs = 10_000L,
        )
        loop.start(this)
        runCurrent()
        assertTrue(cycleCount >= 1)
        loop.stop()
    }

    @Test
    fun `start while running is idempotent`() = runTest {
        var cycleCount = 0
        val loop = PrintAgentLoop(
            loadConfig = { makeConfig() },
            runCycle = { _, _ -> cycleCount++; PrintAgentLoop.CycleResult.Empty },
            onStatus = {},
            normalIntervalMs = 10_000L,
        )
        loop.start(this)
        loop.start(this) // second call must be a no-op
        runCurrent()
        assertEquals(1, cycleCount)
        loop.stop()
    }

    @Test
    fun `stop emits Stopped status and deactivates loop`() = runTest {
        val statuses = mutableListOf<AgentStatus>()
        val loop = PrintAgentLoop(
            loadConfig = { makeConfig() },
            runCycle = { _, _ -> PrintAgentLoop.CycleResult.Empty },
            onStatus = { statuses.add(it) },
            normalIntervalMs = 5_000L,
        )
        loop.start(this)
        runCurrent()
        loop.stop()
        assertEquals(AgentStatus.Stopped, statuses.last())
        assertFalse(loop.isActive)
    }

    // ── Polling interval ──────────────────────────────────────────────────────

    @Test
    fun `second cycle runs only after normal interval elapses`() = runTest {
        var cycleCount = 0
        val loop = PrintAgentLoop(
            loadConfig = { makeConfig() },
            runCycle = { _, _ -> cycleCount++; PrintAgentLoop.CycleResult.Empty },
            onStatus = {},
            normalIntervalMs = 5_000L,
        )
        loop.start(this)
        runCurrent()
        assertEquals(1, cycleCount)

        advanceTimeBy(4_999L)
        assertEquals(1, cycleCount) // still in sleep window

        // delay fires strictly after N ms, so advance N+1 to guarantee the next cycle runs
        advanceTimeBy(2L)
        assertEquals(2, cycleCount)

        loop.stop()
    }

    // ── Config missing ────────────────────────────────────────────────────────

    @Test
    fun `null config emits NoConfig status`() = runTest {
        val statuses = mutableListOf<AgentStatus>()
        val loop = PrintAgentLoop(
            loadConfig = { null },
            runCycle = { _, _ -> PrintAgentLoop.CycleResult.Empty },
            onStatus = { statuses.add(it) },
            normalIntervalMs = 5_000L,
        )
        loop.start(this)
        runCurrent()
        assertTrue(AgentStatus.NoConfig in statuses)
        loop.stop()
    }

    // ── Status emissions ──────────────────────────────────────────────────────

    @Test
    fun `Idle status emitted after empty cycle`() = runTest {
        val statuses = mutableListOf<AgentStatus>()
        val loop = PrintAgentLoop(
            loadConfig = { makeConfig() },
            runCycle = { _, _ -> PrintAgentLoop.CycleResult.Empty },
            onStatus = { statuses.add(it) },
            normalIntervalMs = 10_000L,
        )
        loop.start(this)
        runCurrent()
        assertTrue(statuses.any { it is AgentStatus.Idle })
        loop.stop()
    }

    @Test
    fun `BackingOff status emitted after network error`() = runTest {
        val statuses = mutableListOf<AgentStatus>()
        val loop = PrintAgentLoop(
            loadConfig = { makeConfig() },
            runCycle = { _, _ -> PrintAgentLoop.CycleResult.NetworkError("timeout") },
            onStatus = { statuses.add(it) },
            normalIntervalMs = 5_000L,
        )
        loop.start(this)
        runCurrent()
        assertTrue(statuses.any { it is AgentStatus.BackingOff })
        loop.stop()
    }

    // ── Exponential backoff ───────────────────────────────────────────────────

    @Test
    fun `network error doubles the delay before next retry`() = runTest {
        var cycleCount = 0
        val loop = PrintAgentLoop(
            loadConfig = { makeConfig() },
            runCycle = { _, _ -> cycleCount++; PrintAgentLoop.CycleResult.NetworkError("fail") },
            onStatus = {},
            normalIntervalMs = 1_000L,
            maxBackoffMs = 60_000L,
        )
        loop.start(this)
        runCurrent() // cycle 1 → backoff = 2000ms

        assertEquals(1, cycleCount)
        advanceTimeBy(1_999L)
        assertEquals(1, cycleCount) // still sleeping

        advanceTimeBy(2L) // past 2000ms — cycle 2 fires
        assertEquals(2, cycleCount)

        loop.stop()
    }

    @Test
    fun `success after failure resets backoff to normal interval`() = runTest {
        var cycleCount = 0
        val loop = PrintAgentLoop(
            loadConfig = { makeConfig() },
            runCycle = { _, _ ->
                cycleCount++
                if (cycleCount == 1) PrintAgentLoop.CycleResult.NetworkError("fail")
                else PrintAgentLoop.CycleResult.Empty
            },
            onStatus = {},
            normalIntervalMs = 1_000L,
            maxBackoffMs = 60_000L,
        )
        loop.start(this)
        runCurrent() // cycle 1 → NetworkError → backoff = 2000ms

        advanceTimeBy(2_001L) // past 2000ms → cycle 2 runs → resets to 1000ms

        // Cycle 3 must come at 1000ms after cycle 2, not at 4000ms
        advanceTimeBy(999L)
        assertEquals(2, cycleCount) // hasn't reached 1000ms yet
        advanceTimeBy(2L) // past 1000ms
        assertEquals(3, cycleCount)

        loop.stop()
    }

    @Test
    fun `backoff is capped at maxBackoffMs`() = runTest {
        var cycleCount = 0
        val loop = PrintAgentLoop(
            loadConfig = { makeConfig() },
            runCycle = { _, _ -> cycleCount++; PrintAgentLoop.CycleResult.NetworkError("fail") },
            onStatus = {},
            normalIntervalMs = 1_000L,
            maxBackoffMs = 4_000L,
        )
        loop.start(this)
        runCurrent()              // cycle 1 → backoff = 2000ms
        advanceTimeBy(2_001L)     // cycle 2 → backoff = 4000ms (cap)
        advanceTimeBy(4_001L)     // cycle 3 → backoff = min(8000,4000) = 4000ms
        advanceTimeBy(4_001L)     // cycle 4 → backoff still 4000ms
        advanceTimeBy(4_001L)     // cycle 5

        assertEquals(5, cycleCount)
        loop.stop()
    }

    // ── Mutex / overlap prevention ────────────────────────────────────────────

    @Test
    fun `held mutex suspends the cycle until released`() = runTest {
        val mutex = Mutex()
        var cycleCount = 0
        val loop = PrintAgentLoop(
            loadConfig = { makeConfig() },
            runCycle = { _, _ -> cycleCount++; PrintAgentLoop.CycleResult.Empty },
            onStatus = {},
            normalIntervalMs = 5_000L,
            processingMutex = mutex,
        )

        mutex.lock() // simulate manual UI processing holding the mutex
        loop.start(this)
        runCurrent()
        assertEquals(0, cycleCount) // loop is suspended waiting for mutex

        mutex.unlock()
        runCurrent() // loop acquires mutex and runs cycle
        assertEquals(1, cycleCount)

        loop.stop()
    }
}
