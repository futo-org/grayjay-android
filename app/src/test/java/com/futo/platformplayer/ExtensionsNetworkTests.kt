package com.futo.platformplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class ExtensionsNetworkTests {
    @Test
    fun `should correctly parse valid IPv4 string`() {
        val ipString = "192.168.0.1"
        val expected = InetAddress.getByName(ipString)

        val actual = ipString.toInetAddress()

        assertEquals(expected, actual)
    }

    @Test
    fun `should return null for invalid IPv4 string`() {
        val ipString = "192.168.0.300"

        val actual = ipString.toInetAddress()

        assertNull(actual)
    }

    @Test
    fun `should correctly parse valid IPv6 string`() {
        val ipString = "2001:0db8:85a3:0000:0000:8a2e:0370:7334"
        val expected = InetAddress.getByName(ipString)

        val actual = ipString.toInetAddress()

        assertEquals(expected, actual)
    }

    @Test
    fun `should return null for invalid IPv6 string`() {
        val ipString = "2001:0db8:85a3:0000:0000:8a2e:0370:733g"  // 'g' is invalid in an IPv6 address

        val actual = ipString.toInetAddress()

        assertNull(actual)
    }

    @Test
    fun `should convert IPv6 dotted quad to hex correctly`() {
        val ipString = "2001:0db8:85a3:0000:0000:8a2e:192.0.2.1"
        val expected = "2001:0db8:85a3:0000:0000:8a2e:c000:201"

        val actual = convertDottedQuadToHex(ipString)

        assertEquals(expected, actual)
    }


    @Test
    fun `should correctly parse valid IPv4 string with zeros`() {
        val ipString = "192.168.0.0"
        val expected = InetAddress.getByName(ipString)

        val actual = ipString.toInetAddress()

        assertEquals(expected, actual)
    }

    @Test
    fun `should correctly parse valid IPv6 string with leading zeros`() {
        val ipString = "2001:0db8:0000:0000:0000:0000:0000:0001"
        val expected = InetAddress.getByName(ipString)

        val actual = ipString.toInetAddress()

        assertEquals(expected, actual)
    }

    @Test
    fun `should correctly parse valid IPv6 string with double colons`() {
        val ipString = "2001:0db8::1"
        val expected = InetAddress.getByName(ipString)

        val actual = ipString.toInetAddress()

        assertEquals(expected, actual)
    }

    @Test
    fun `should convert IPv6 dotted quad to hex correctly with leading zeros`() {
        val ipString = "2001:0db8:85a3:0000:0000:8a2e:192.0.0.0"
        val expected = "2001:0db8:85a3:0000:0000:8a2e:c000:0"

        val actual = convertDottedQuadToHex(ipString)

        assertEquals(expected, actual)
    }
}