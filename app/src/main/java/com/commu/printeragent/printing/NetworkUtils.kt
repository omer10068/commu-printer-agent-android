package com.commu.printeragent.printing

/**
 * Pure Kotlin IPv4 and subnet utility functions.
 * No Android dependencies — fully testable in JVM unit tests.
 */

/** Converts a dotted-decimal IPv4 string to a 32-bit value stored in a Long. Returns null if invalid. */
fun ipToLong(address: String): Long? {
    val parts = address.split(".")
    if (parts.size != 4) return null
    var result = 0L
    for (part in parts) {
        val octet = part.toIntOrNull() ?: return null
        if (octet !in 0..255) return null
        result = (result shl 8) or octet.toLong()
    }
    return result
}

/** Returns true if [address] is a valid dotted-decimal IPv4 address. */
fun isValidIpv4(address: String): Boolean = ipToLong(address) != null

/** Returns true if [port] is in the valid TCP port range 1–65535. */
fun isValidPort(port: Int): Boolean = port in 1..65535

/**
 * Computes the network (base) address for the given IP and prefix length.
 * Example: networkAddress(ipToLong("10.0.0.5")!!, 24) → ipToLong("10.0.0.0")!!
 */
fun networkAddress(ipLong: Long, prefixLength: Int): Long {
    if (prefixLength == 0) return 0L
    if (prefixLength >= 32) return ipLong
    val mask = (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
    return ipLong and mask
}

/**
 * Returns all usable host addresses in the subnet defined by [networkAddress] (32-bit Long)
 * and [prefixLength]. Excludes the network address and broadcast address.
 *
 * Maximum safe input for subnet scanning: prefixLength ≥ 24 (254 hosts).
 * Callers are responsible for capping to /24 before calling this.
 */
fun subnetHosts(networkAddress: Long, prefixLength: Int): List<String> {
    require(prefixLength in 0..32) { "Invalid prefix length: $prefixLength" }
    val hostBits = 32 - prefixLength
    if (hostBits == 0) return emptyList()
    val hostCount = (1L shl hostBits) - 2L
    if (hostCount <= 0L) return emptyList()
    val mask = if (prefixLength == 0) 0L else (0xFFFFFFFFL shl hostBits) and 0xFFFFFFFFL
    val base = networkAddress and mask
    return (1L..hostCount).map { i ->
        val addr = base + i
        "${(addr shr 24) and 0xFFL}.${(addr shr 16) and 0xFFL}.${(addr shr 8) and 0xFFL}.${addr and 0xFFL}"
    }
}

/** Formats an epoch-milliseconds timestamp as HH:mm, or "—" if zero. */
fun formatCheckTime(epochMs: Long): String {
    if (epochMs == 0L) return "—"
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMs))
}
