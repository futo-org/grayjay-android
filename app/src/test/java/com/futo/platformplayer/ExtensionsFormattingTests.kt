package com.futo.platformplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExtensionsFormattingTests {

    @Test
    fun testToHumanNumber() {
        assertEquals("1K", 1000L.toHumanNumber())
        assertEquals("1M", 1000000L.toHumanNumber())
        assertEquals("1.5M", 1500000L.toHumanNumber())
        assertEquals("3B", 3000000000L.toHumanNumber())
        assertEquals("500", 500L.toHumanNumber())
        assertEquals("0", 0L.toHumanNumber())
        assertEquals("-1K", (-1000L).toHumanNumber())
    }

    @Test
    fun testToHumanBitrate() {
        assertEquals("1kbps", 1000.toHumanBitrate())
        assertEquals("2mbps", 2000000.toHumanBitrate())
        assertEquals("3gbps", 3000000000.toHumanBitrate())
        assertEquals("500bps", 500.toHumanBitrate())
        assertEquals("0bps", 0.toHumanBitrate())
        assertEquals("-1kbps", (-1000).toHumanBitrate())
    }

    @Test
    fun testToHumanBytesSpeed() {
        assertEquals("1KB/s", 1000L.toHumanBytesSpeed())
        assertEquals("2MB/s", 2000000L.toHumanBytesSpeed())
        assertEquals("3GB/s", 3000000000L.toHumanBytesSpeed())
        assertEquals("500B/s", 500L.toHumanBytesSpeed())
        assertEquals("0B/s", 0L.toHumanBytesSpeed())
        assertEquals("-1KB/s", (-1000L).toHumanBytesSpeed())
    }

    @Test
    fun testToHumanBytesSize() {
        assertEquals("1KB", 1000L.toHumanBytesSize())
        assertEquals("2MB", 2000000L.toHumanBytesSize())
        assertEquals("3GB", 3000000000L.toHumanBytesSize())
        assertEquals("500B", 500L.toHumanBytesSize())
        assertEquals("0B", 0L.toHumanBytesSize())
        assertEquals("-1KB", (-1000L).toHumanBytesSize())
    }

    @Test
    fun testToHumanTime() {
        assertEquals("1:00", 60L.toHumanTime(false))
        assertEquals("1:01", 61L.toHumanTime(false))
        assertEquals("1:30:00", 5400L.toHumanTime(false))
        assertEquals("-1:00", (-60L).toHumanTime(false))
        assertEquals("0:00", 0L.toHumanTime(false))
    }

    @Test
    fun testToSafeFileName() {
        assertEquals("Hello_World", "Hello World".toSafeFileName())
        assertEquals("Hello_World_", "Hello World!".toSafeFileName())
    }

    @Test
    fun testMatchesDomain() {
        assertTrue("google.com".matchesDomain("google.com"))
        assertFalse("yahoo.com".matchesDomain("google.com"))
        assertTrue("mail.google.com".matchesDomain(".google.com"))
    }

    @Test
    fun testTimeDiff() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val oneHourAgo = now.minusHours(1)
        assertEquals(1, oneHourAgo.getNowDiffHours())
    }

    @Test
    fun testToHumanNowDiffString() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val oneHourAgo = now.minusHours(1)
        assertEquals("1 hour", oneHourAgo.toHumanNowDiffString())
    }

    @Test
    fun testSpecialCharacterToSafeFileName() {
        assertEquals("_Hello_World_", "?Hello World!".toSafeFileName())
    }

    @Test
    fun testZeroTimeDiff() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        assertEquals(0, now.getNowDiffHours())
    }

    @Test
    fun testZeroToHumanNowDiffString() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        assertEquals("0 seconds", now.toHumanNowDiffString())
    }
}
