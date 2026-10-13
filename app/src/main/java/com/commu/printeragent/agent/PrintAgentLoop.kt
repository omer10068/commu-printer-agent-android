package com.commu.printeragent.agent

import com.commu.printeragent.data.PrinterRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The background print-agent polling loop.
 *
 * All external dependencies are injected as suspend lambdas so this class can be
 * tested in a plain JVM environment without Android context or a real printer.
 *
 * The loop:
 *   1. Loads credentials and printer mappings ([loadConfig]).
 *   2. Acquires [processingMutex] — preventing overlap with the manual UI button.
 *   3. Calls [runCycle] which drains pending PATCH retries and then fetches + dispatches jobs.
 *   4. Sleeps for [normalIntervalMs] on success, or backs off on failure.
 *
 * A [CancellationException] is always re-thrown so the coroutine cancels cleanly.
 */
class PrintAgentLoop(
    private val loadConfig: suspend () -> LoopConfig?,
    private val runCycle: suspend (LoopConfig, onProgress: (String) -> Unit) -> CycleResult,
    private val onStatus: (AgentStatus) -> Unit,
    internal val normalIntervalMs: Long = NORMAL_INTERVAL_MS,
    private val maxBackoffMs: Long = MAX_BACKOFF_MS,
    /** Injected to allow coroutine-test virtual-time control; defaults to [delay]. */
    private val delayFn: suspend (Long) -> Unit = ::delay,
    /** Shared with [JobQueueViewModel] to prevent concurrent manual + background processing. */
    private val processingMutex: Mutex = Mutex(),
) {
    companion object {
        const val NORMAL_INTERVAL_MS = 5_000L
        const val MAX_BACKOFF_MS = 300_000L
    }

    /** All the runtime credentials + routing data needed for one polling cycle. */
    data class LoopConfig(
        val baseUrl: String,
        val tenantId: String,
        val token: String,
        val mappings: Map<Int, String>,
        val printers: List<PrinterRecord>,
    )

    sealed class CycleResult {
        /** At least one job was fetched and processed. */
        data class Processed(val printed: Int, val failed: Int) : CycleResult()
        /** Queue was empty; nothing to do. */
        object Empty : CycleResult()
        /** Credentials or mappings are incomplete — surface to user without backoff. */
        data class ConfigMissing(val message: String) : CycleResult()
        /** Network or backend error — apply backoff before retrying. */
        data class NetworkError(val message: String) : CycleResult()
    }

    @Volatile private var loopJob: Job? = null

    val isActive: Boolean get() = loopJob?.isActive == true

    /**
     * Starts the polling loop in [scope].
     * Idempotent: calling start while already running is a no-op.
     */
    fun start(scope: CoroutineScope) {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { loop() }
    }

    /** Cancels the polling loop and emits [AgentStatus.Stopped]. */
    fun stop() {
        loopJob?.cancel()
        loopJob = null
        onStatus(AgentStatus.Stopped)
    }

    private suspend fun loop() {
        var backoffMs = normalIntervalMs
        while (isActive) {
            val config = loadConfig()
            if (config == null) {
                onStatus(AgentStatus.NoConfig)
                delayFn(normalIntervalMs)
                continue
            }

            onStatus(AgentStatus.Polling)

            val result = try {
                processingMutex.withLock {
                    runCycle(config) { /* progress reported via onStatus / AgentState */ }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CycleResult.NetworkError(e.message ?: "שגיאה לא ידועה")
            }

            val nowMs = System.currentTimeMillis()
            when (result) {
                is CycleResult.Processed,
                is CycleResult.Empty -> {
                    backoffMs = normalIntervalMs
                    onStatus(AgentStatus.Idle(nowMs))
                }
                is CycleResult.NetworkError -> {
                    backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
                    onStatus(AgentStatus.BackingOff(result.message, nowMs + backoffMs))
                }
                is CycleResult.ConfigMissing -> {
                    backoffMs = normalIntervalMs
                    onStatus(AgentStatus.NoConfig)
                }
            }

            delayFn(backoffMs)
        }
    }
}
