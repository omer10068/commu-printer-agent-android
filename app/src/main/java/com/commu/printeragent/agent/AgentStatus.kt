package com.commu.printeragent.agent

/** Observable status of the background print-agent polling loop. */
sealed class AgentStatus {
    /** Service not started. */
    object Stopped : AgentStatus()

    /** Service started, loop not yet executing first cycle. */
    object Starting : AgentStatus()

    /** Running normally, waiting between poll cycles. */
    data class Idle(val lastPollMs: Long) : AgentStatus()

    /** Actively fetching the queue and dispatching jobs right now. */
    object Polling : AgentStatus()

    /**
     * A network or backend error occurred; the loop will retry after [retryAtMs].
     * [reason] is a user-visible summary — must NOT contain tokens or credentials.
     */
    data class BackingOff(val reason: String, val retryAtMs: Long) : AgentStatus()

    /**
     * Credentials or printer mappings are missing; the loop cannot run until the user
     * completes setup in the Connection or Printers tab.
     */
    object NoConfig : AgentStatus()
}
