package com.commu.printeragent.printing

/**
 * Outcome of a [PrinterChannel.send] operation.
 *
 * [Transmitted]: Bytes were written to the socket buffer and flushed.
 * This is the strongest confirmation available from a raw TCP print stream.
 * Thermal printers do not send application-level acknowledgements;
 * physical paper output cannot be confirmed by a socket write alone.
 *
 * [Failed]: The operation did not complete. [reason] describes the failure.
 */
sealed class PrintResult {
    data class Transmitted(val byteCount: Int) : PrintResult()
    data class Failed(val reason: String) : PrintResult()
}
