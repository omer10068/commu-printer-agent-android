package com.commu.printeragent.printing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicInteger

/** The active IPv4 address and its subnet prefix length as reported by the platform. */
data class LocalNetworkInfo(val address: String, val prefixLength: Int) {
    /** The effective prefix used for scanning: capped at /24 (254 hosts). */
    val effectivePrefix: Int get() = maxOf(prefixLength, 24)

    /** Total number of hosts that will be probed. */
    val scanHostCount: Int
        get() {
            val ipLong = ipToLong(address) ?: return 0
            val netAddr = networkAddress(ipLong, effectivePrefix)
            return subnetHosts(netAddr, effectivePrefix).size
        }
}

/**
 * LAN discovery utilities.
 *
 * Scanning is user-triggered only and bounded to the /24 subnet containing the device.
 * An open TCP port is reported as a CANDIDATE — not confirmed printer identity.
 */
object LanScanner {

    /**
     * Returns the active non-loopback IPv4 address and prefix from the platform's
     * ConnectivityManager (API 23+). Returns null when:
     * - Wi-Fi / LAN is not connected
     * - No Inet4Address link address is found
     * - Required system service is unavailable
     *
     * Requires ACCESS_NETWORK_STATE permission.
     */
    fun localNetworkInfo(context: Context): LocalNetworkInfo? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) return null
        val lp = cm.getLinkProperties(network) ?: return null
        val linkAddr = lp.linkAddresses.firstOrNull { la ->
            la.address is Inet4Address && !la.address.isLoopbackAddress
        } ?: return null
        val hostAddress = linkAddr.address.hostAddress ?: return null
        return LocalNetworkInfo(address = hostAddress, prefixLength = linkAddr.prefixLength)
    }

    /**
     * Scans for open TCP [port] in the /24 subnet containing [networkInfo].
     *
     * - Caps scan to /24 regardless of actual subnet size.
     * - Uses at most [MAX_CONCURRENCY] simultaneous probes.
     * - [onProgress] is called after each probe with (completed, total).
     * - [onFound] is called for each address that accepted the connection.
     * - Respects coroutine cancellation; in-flight probes complete within [connectTimeoutMs].
     */
    suspend fun scan(
        networkInfo: LocalNetworkInfo,
        port: Int = 9100,
        connectTimeoutMs: Int = 300,
        onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> },
        onFound: suspend (address: String) -> Unit = {},
    ) {
        val ipLong = ipToLong(networkInfo.address) ?: return
        val netAddr = networkAddress(ipLong, networkInfo.effectivePrefix)
        val hosts = subnetHosts(netAddr, networkInfo.effectivePrefix)
        if (hosts.isEmpty()) return

        val total = hosts.size
        val completed = AtomicInteger(0)
        val semaphore = Semaphore(MAX_CONCURRENCY)

        coroutineScope {
            hosts.map { host ->
                async {
                    semaphore.withPermit {
                        val reachable = TcpProbe.check(host, port, connectTimeoutMs)
                        onProgress(completed.incrementAndGet(), total)
                        if (reachable) onFound(host)
                    }
                }
            }.awaitAll()
        }
    }

    private const val MAX_CONCURRENCY = 50
}
