package com.futo.platformplayer

import com.futo.platformplayer.sabr.Mp4SidxParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class Mp4SidxParserTests {

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val size = 8 + payload.size
        out.write(ByteBuffer.allocate(4).putInt(size).array())
        out.write(type.toByteArray(Charsets.US_ASCII))
        out.write(payload)
        return out.toByteArray()
    }

    private fun sidxPayload(timescale: Int, durations: LongArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0)
        out.write(byteArrayOf(0, 0, 0))
        out.write(ByteBuffer.allocate(4).putInt(1).array())
        out.write(ByteBuffer.allocate(4).putInt(timescale).array())
        out.write(ByteBuffer.allocate(4).putInt(0).array())
        out.write(ByteBuffer.allocate(4).putInt(0).array())
        out.write(ByteBuffer.allocate(2).putShort(0).array())
        out.write(ByteBuffer.allocate(2).putShort(durations.size.toShort()).array())
        for (d in durations) {
            out.write(ByteBuffer.allocate(4).putInt(0).array())
            out.write(ByteBuffer.allocate(4).putInt(d.toInt()).array())
            out.write(ByteBuffer.allocate(4).putInt(0).array())
        }
        return out.toByteArray()
    }

    @Test
    fun parsesSidxAfterFtypAndMoov() {
        val durations = longArrayOf(5000, 5000, 4321, 5000)
        val data = ByteArrayOutputStream().apply {
            write(box("ftyp", ByteArray(16)))
            write(box("moov", ByteArray(200)))
            write(box("sidx", sidxPayload(1000, durations)))
        }.toByteArray()

        val timing = Mp4SidxParser.parse(data)!!
        assertEquals(1000, timing.timescale)
        assertEquals(4, timing.segmentCount)
        assertArrayEquals(durations, timing.durations)
    }

    @Test
    fun returnsNullWhenNoSidx() {
        val data = ByteArrayOutputStream().apply {
            write(box("ftyp", ByteArray(16)))
            write(box("moov", ByteArray(64)))
        }.toByteArray()
        assertNull(Mp4SidxParser.parse(data))
    }

    @Test
    fun returnsNullOnTruncatedSidx() {
        val payload = sidxPayload(1000, longArrayOf(5000, 5000)).copyOf(20)
        val data = box("sidx", payload)
        assertNull(Mp4SidxParser.parse(data))
    }
}
