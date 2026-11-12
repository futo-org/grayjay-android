package com.futo.platformplayer

import com.futo.platformplayer.sabr.UmpPartType
import com.futo.platformplayer.sabr.UmpReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class UmpReaderTests {

    private fun writeVarInt(out: ByteArrayOutputStream, value: Long) {
        when {
            value < 128 -> out.write(value.toInt())
            value < 1 shl 14 -> {
                out.write((0x80 or (value and 0x3F).toInt()))
                out.write(((value shr 6) and 0xFF).toInt())
            }
            value < 1 shl 21 -> {
                out.write((0xC0 or (value and 0x1F).toInt()))
                out.write(((value shr 5) and 0xFF).toInt())
                out.write(((value shr 13) and 0xFF).toInt())
            }
            value < 1 shl 28 -> {
                out.write((0xE0 or (value and 0x0F).toInt()))
                out.write(((value shr 4) and 0xFF).toInt())
                out.write(((value shr 12) and 0xFF).toInt())
                out.write(((value shr 20) and 0xFF).toInt())
            }
            else -> {
                out.write(0xF0)
                out.write((value and 0xFF).toInt())
                out.write(((value shr 8) and 0xFF).toInt())
                out.write(((value shr 16) and 0xFF).toInt())
                out.write(((value shr 24) and 0xFF).toInt())
            }
        }
    }

    private fun stream(vararg parts: Pair<Int, ByteArray>): UmpReader {
        val out = ByteArrayOutputStream()
        for ((type, data) in parts) {
            writeVarInt(out, type.toLong())
            writeVarInt(out, data.size.toLong())
            out.write(data)
        }
        return UmpReader(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun readsSequenceOfParts() {
        val header = ByteArray(3) { it.toByte() }
        val media = ByteArray(500) { (it % 251).toByte() }
        val reader = stream(
            UmpPartType.MEDIA_HEADER to header,
            UmpPartType.MEDIA to media,
            UmpPartType.MEDIA_END to byteArrayOf(0)
        )

        val first = reader.next()!!
        assertEquals(UmpPartType.MEDIA_HEADER, first.type)
        assertArrayEquals(header, first.data)

        val second = reader.next()!!
        assertEquals(UmpPartType.MEDIA, second.type)
        assertArrayEquals(media, second.data)

        val third = reader.next()!!
        assertEquals(UmpPartType.MEDIA_END, third.type)

        assertNull(reader.next())
    }

    @Test
    fun readsPartsAcrossEveryVarIntWidth() {
        for (size in listOf(0, 1, 127, 128, 16383, 16384, 100_000)) {
            val data = ByteArray(size) { (it % 256).toByte() }
            val reader = stream(UmpPartType.MEDIA to data)
            val part = reader.next()!!
            assertEquals("size=$size", UmpPartType.MEDIA, part.type)
            assertEquals("size=$size", size, part.data.size)
            assertArrayEquals("size=$size", data, part.data)
            assertNull(reader.next())
        }
    }

    @Test
    fun readsLargePartTypeAndFiveByteLength() {
        val out = ByteArrayOutputStream()
        writeVarInt(out, UmpPartType.SNACKBAR_MESSAGE.toLong())
        writeVarInt(out, 0)
        val reader = UmpReader(ByteArrayInputStream(out.toByteArray()))
        assertEquals(UmpPartType.SNACKBAR_MESSAGE, reader.next()!!.type)
    }

    @Test
    fun emptyStreamYieldsNull() {
        assertNull(UmpReader(ByteArrayInputStream(ByteArray(0))).next())
    }
}
