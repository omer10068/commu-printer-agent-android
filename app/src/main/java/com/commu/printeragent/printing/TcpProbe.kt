package com.commu.printeragent.printing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Lightweight TCP connectivity probe.
 * Attempts to open a TCP connection and immediately closes it.
 * An open port means the host accepted the connection — it does NOT identify the device
 * as a printer or verify any print protocol.
 */
object TcpProbe {

    /**
     * Returns true if [address]:[port] accepted a TCP connection within [timeoutMs].
     * Never throws — returns false on any error (refused, timeout, DNS failure, etc.).
     */
    suspend fun check(address: String, port: Int, timeoutMs: Int = 2000): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }
}
