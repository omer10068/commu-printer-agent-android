package com.commu.printeragent.printing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkUtilsTest {

    // ── isValidIpv4 ───────────────────────────────────────────────────────────

    @Test
    fun `valid IPv4 addresses are accepted`() {
        assertTrue(isValidIpv4("0.0.0.0"))
        assertTrue(isValidIpv4("10.0.0.1"))
        assertTrue(isValidIpv4("192.168.1.21"))
        assertTrue(isValidIpv4("255.255.255.255"))
    }

    @Test
    fun `invalid IPv4 addresses are rejected`() {
        assertFalse(isValidIpv4(""))
        assertFalse(isValidIpv4("abc"))
        assertFalse(isValidIpv4("256.0.0.1"))
        assertFalse(isValidIpv4("10.0.0"))
        assertFalse(isValidIpv4("10.0.0.0.0"))
        assertFalse(isValidIpv4("10.0.0.-1"))
        assertFalse(isValidIpv4("not-an-ip"))
    }

    // ── isValidPort ───────────────────────────────────────────────────────────

    @Test
    fun `valid ports are accepted`() {
        assertTrue(isValidPort(1))
        assertTrue(isValidPort(9100))
        assertTrue(isValidPort(65535))
    }

    @Test
    fun `invalid ports are rejected`() {
        assertFalse(isValidPort(0))
        assertFalse(isValidPort(-1))
        assertFalse(isValidPort(65536))
    }

    // ── ipToLong ──────────────────────────────────────────────────────────────

    @Test
    fun `ipToLong converts known addresses`() {
        assertEquals(0L, ipToLong("0.0.0.0"))
        assertEquals(167772161L, ipToLong("10.0.0.1"))      // 0x0A000001
        assertEquals(3232235801L, ipToLong("192.168.1.25")) // 0xC0A80119
        assertEquals(4294967295L, ipToLong("255.255.255.255"))
    }

    @Test
    fun `ipToLong returns null for invalid input`() {
        assertNull(ipToLong(""))
        assertNull(ipToLong("256.0.0.1"))
        assertNull(ipToLong("10.0.0"))
    }

    // ── networkAddress ────────────────────────────────────────────────────────

    @Test
    fun `networkAddress masks correctly for slash24`() {
        val ip = ipToLong("10.0.0.5")!!
        val net = networkAddress(ip, 24)
        assertEquals(ipToLong("10.0.0.0")!!, net)
    }

    @Test
    fun `networkAddress masks correctly for slash16`() {
        val ip = ipToLong("192.168.5.100")!!
        val net = networkAddress(ip, 16)
        assertEquals(ipToLong("192.168.0.0")!!, net)
    }

    @Test
    fun `networkAddress prefix 32 returns the address itself`() {
        val ip = ipToLong("10.0.0.5")!!
        assertEquals(ip, networkAddress(ip, 32))
    }

    @Test
    fun `networkAddress prefix 0 returns zero`() {
        val ip = ipToLong("10.0.0.5")!!
        assertEquals(0L, networkAddress(ip, 0))
    }

    // ── subnetHosts ───────────────────────────────────────────────────────────

    @Test
    fun `subnetHosts slash24 returns 254 hosts`() {
        val net = ipToLong("10.0.0.0")!!
        val hosts = subnetHosts(net, 24)
        assertEquals(254, hosts.size)
        assertEquals("10.0.0.1", hosts.first())
        assertEquals("10.0.0.254", hosts.last())
    }

    @Test
    fun `subnetHosts slash25 returns 126 hosts`() {
        val net = ipToLong("10.0.0.0")!!
        val hosts = subnetHosts(net, 25)
        assertEquals(126, hosts.size)
        assertEquals("10.0.0.1", hosts.first())
        assertEquals("10.0.0.126", hosts.last())
    }

    @Test
    fun `subnetHosts slash32 returns empty list`() {
        val net = ipToLong("10.0.0.5")!!
        assertTrue(subnetHosts(net, 32).isEmpty())
    }

    @Test
    fun `subnetHosts all addresses are valid IPv4`() {
        val net = ipToLong("192.168.1.0")!!
        subnetHosts(net, 24).forEach { host ->
            assertTrue("$host should be valid IPv4", isValidIpv4(host))
        }
    }

    @Test
    fun `subnetHosts does not include network or broadcast address`() {
        val net = ipToLong("10.0.0.0")!!
        val hosts = subnetHosts(net, 24)
        assertFalse(hosts.contains("10.0.0.0"))
        assertFalse(hosts.contains("10.0.0.255"))
    }

    // ── LanScanner.effectivePrefix cap ────────────────────────────────────────

    @Test
    fun `effectivePrefix caps slash16 to slash24`() {
        val info = com.commu.printeragent.printing.LocalNetworkInfo("10.0.5.3", 16)
        assertEquals(24, info.effectivePrefix)
    }

    @Test
    fun `effectivePrefix preserves slash25`() {
        val info = com.commu.printeragent.printing.LocalNetworkInfo("10.0.0.5", 25)
        assertEquals(25, info.effectivePrefix)
    }

    @Test
    fun `scanHostCount for slash24 is 254`() {
        val info = com.commu.printeragent.printing.LocalNetworkInfo("10.0.0.5", 24)
        assertEquals(254, info.scanHostCount)
    }
}
