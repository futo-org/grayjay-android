package com.futo.platformplayer

import com.futo.platformplayer.sabr.WebmCuesParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class WebmCuesParserTests {

    private fun vint(value: Long): ByteArray {
        return when {
            value < 0x7F -> byteArrayOf((value or 0x80).toByte())
            value < 0x3FFF -> byteArrayOf((((value shr 8) or 0x40).toByte()), (value and 0xFF).toByte())
            else -> byteArrayOf(
                (((value shr 16) or 0x20).toByte()),
                ((value shr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte()
            )
        }
    }

    private fun id(vararg bytes: Int): ByteArray = bytes.map { it.toByte() }.toByteArray()

    private fun element(idBytes: ByteArray, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(idBytes)
        out.write(vint(content.size.toLong()))
        out.write(content)
        return out.toByteArray()
    }

    private fun uint(value: Long): ByteArray {
        if (value <= 0xFF) return byteArrayOf(value.toByte())
        if (value <= 0xFFFF) return byteArrayOf((value shr 8).toByte(), value.toByte())
        return byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())
    }

    private fun f64(value: Double): ByteArray {
        val bits = value.toRawBits()
        return ByteArray(8) { i -> (bits shr (56 - i * 8)).toByte() }
    }

    private fun cuePoint(timeMs: Long): ByteArray =
        element(id(0xBB), element(id(0xB3), uint(timeMs)))

    private fun webm(cueTimesMs: List<Long>, durationMs: Double?, timecodeScaleNs: Long = 1_000_000): ByteArray {
        val info = ByteArrayOutputStream()
        info.write(element(id(0x2A, 0xD7, 0xB1), uint(timecodeScaleNs)))
        if (durationMs != null) info.write(element(id(0x44, 0x89), f64(durationMs)))

        val cues = ByteArrayOutputStream()
        cueTimesMs.forEach { cues.write(cuePoint(it)) }

        val segment = ByteArrayOutputStream()
        segment.write(element(id(0x15, 0x49, 0xA9, 0x66), info.toByteArray()))
        segment.write(element(id(0x1C, 0x53, 0xBB, 0x6B), cues.toByteArray()))

        val out = ByteArrayOutputStream()
        out.write(element(id(0x1A, 0x45, 0xDF, 0xA3), byteArrayOf(1, 2, 3)))
        out.write(element(id(0x18, 0x53, 0x80, 0x67), segment.toByteArray()))
        return out.toByteArray()
    }

    @Test
    fun cueTimesBecomeExactSegmentDurations() {
        val timing = WebmCuesParser.parse(webm(listOf(0L, 5000L, 8000L, 15000L), durationMs = 20000.0))!!

        assertEquals(1000, timing.timescale)
        assertEquals(4, timing.segmentCount)
        assertEquals(listOf(5000L, 3000L, 7000L, 5000L), timing.durations.toList())
    }

    @Test
    fun startTimesAreRecoverableForAnchoring() {
        val timing = WebmCuesParser.parse(webm(listOf(0L, 5000L, 8000L, 15000L), durationMs = 20000.0))!!

        assertEquals(0L, timing.startUsOf(0))
        assertEquals(5_000_000L, timing.startUsOf(1))
        assertEquals(8_000_000L, timing.startUsOf(2))
        assertEquals(15_000_000L, timing.startUsOf(3))

        assertEquals(2, timing.indexOfStartUs(8_000_000L))
        assertNull(timing.indexOfStartUs(9_999_999L))
    }

    @Test
    fun aNonDefaultTimecodeScaleIsHonoured() {
        val timing = WebmCuesParser.parse(
            webm(listOf(0L, 50_000L), durationMs = 100_000.0, timecodeScaleNs = 100_000)
        )!!

        assertEquals(10_000, timing.timescale)
        assertEquals(5_000_000L, timing.startUsOf(1))
    }

    @Test
    fun withoutDurationTheLastClusterReusesThePreviousLength() {
        val timing = WebmCuesParser.parse(webm(listOf(0L, 5000L, 10_000L), durationMs = null))!!
        assertEquals(listOf(5000L, 5000L, 5000L), timing.durations.toList())
    }

    @Test
    fun tooFewCuesIsNotATimeline() {
        assertNull(WebmCuesParser.parse(webm(listOf(0L), durationMs = 5000.0)))
        assertNull(WebmCuesParser.parse(webm(emptyList(), durationMs = 5000.0)))
    }

    @Test
    fun garbageAndMp4DoNotParseAsWebm() {
        assertNull(WebmCuesParser.parse(ByteArray(64)))
        assertNull(WebmCuesParser.parse("ftypiso5".toByteArray()))
    }
}
