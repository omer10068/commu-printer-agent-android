package com.commu.printeragent.printing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Sends ESC/POS byte payloads to TCP-connected receipt printers.
 *
 * Each distinct address:port endpoint has its own [Mutex] so that concurrent
 * calls for the same printer are serialized rather than interleaved.
 * Different printers run independently — no global lock.
 *
 * A [PrintResult.Transmitted] result means the bytes were written and flushed
 * to the OS socket buffer. Thermal printers do not acknowledge receipt;
 * physical paper output cannot be confirmed via TCP alone.
 */
object PrinterChannel {

    private val mutexes = ConcurrentHashMap<String, Mutex>()

    private fun mutexFor(address: String, port: Int): Mutex =
        mutexes.getOrPut("$address:$port") { Mutex() }

    /**
     * Sends [bytes] to [address]:[port] under the per-printer mutex.
     *
     * @param connectTimeoutMs applies to the TCP connect call and the socket SO_TIMEOUT.
     */
    suspend fun send(
        address: String,
        port: Int,
        bytes: ByteArray,
        connectTimeoutMs: Int = 5_000,
    ): PrintResult = mutexFor(address, port).withLock {
        withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.soTimeout = connectTimeoutMs
                    socket.connect(InetSocketAddress(address, port), connectTimeoutMs)
                    val out = socket.getOutputStream()
                    out.write(bytes)
                    out.flush()
                    PrintResult.Transmitted(bytes.size)
                }
            } catch (e: Exception) {
                PrintResult.Failed(e.message ?: "TCP error")
            }
        }
    }
}
