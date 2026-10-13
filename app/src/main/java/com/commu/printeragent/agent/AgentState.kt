package com.commu.printeragent.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * Process-wide singleton that carries the live state of the background print agent.
 *
 * Both [PrintAgentService] (writer) and [JobQueueViewModel] (reader) reference this object.
 * Using a singleton is safe because the service and UI live in the same process.
 *
 * The [processingMutex] prevents concurrent processing between the background polling
 * loop and the manual "process queue" button in the UI.
 */
object AgentState {

    private val _status = MutableStateFlow<AgentStatus>(AgentStatus.Stopped)
    val status: StateFlow<AgentStatus> = _status.asStateFlow()

    private val _lastPollMs = MutableStateFlow(0L)
    val lastPollMs: StateFlow<Long> = _lastPollMs.asStateFlow()

    private val _lastJobMs = MutableStateFlow(0L)
    val lastJobMs: StateFlow<Long> = _lastJobMs.asStateFlow()

    private val _sessionPrinted = MutableStateFlow(0)
    val sessionPrinted: StateFlow<Int> = _sessionPrinted.asStateFlow()

    private val _sessionFailed = MutableStateFlow(0)
    val sessionFailed: StateFlow<Int> = _sessionFailed.asStateFlow()

    /** Acquired by both the background loop and the manual button before processing. */
    val processingMutex = Mutex()

    fun setStatus(status: AgentStatus) {
        _status.value = status
    }

    /** Called after each completed polling cycle. */
    fun recordCycle(pollMs: Long, printedCount: Int, failedCount: Int) {
        _lastPollMs.value = pollMs
        if (printedCount > 0 || failedCount > 0) {
            _lastJobMs.value = pollMs
            _sessionPrinted.value += printedCount
            _sessionFailed.value += failedCount
        }
    }

    /** Called when the service stops; preserves session counts for the UI until app is killed. */
    fun markStopped() {
        _status.value = AgentStatus.Stopped
    }
}
