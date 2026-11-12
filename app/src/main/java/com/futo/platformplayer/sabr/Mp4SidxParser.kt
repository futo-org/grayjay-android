package com.futo.platformplayer.sabr

import java.nio.ByteBuffer
import java.nio.ByteOrder

class SidxTiming(val timescale: Int, val durations: LongArray, val baseTicks: Long = 0) {
    val segmentCount: Int get() = durations.size

    val baseUs: Long get() = ticksToUs(baseTicks)

    fun indexOfStartUs(startUs: Long): Int? {
        var ticks = baseTicks
        var best = -1
        var bestDelta = Long.MAX_VALUE
        for (i in durations.indices) {
            val us = ticksToUs(ticks)
            val delta = Math.abs(us - startUs)
            if (delta < bestDelta) {
                bestDelta = delta
                best = i
            }
            if (us > startUs && delta > bestDelta) break
            ticks += durations[i]
        }
        if (best < 0) return null

        val toleranceUs = minOf(MAX_ANCHOR_DRIFT_US, ticksToUs(durations[best]) / 2)
        return if (bestDelta <= toleranceUs) best else null
    }

    fun startUsOf(index: Int): Long? {
        if (index < 0 || index >= durations.size) return null
        var ticks = baseTicks
        for (i in 0 until index) ticks += durations[i]
        return ticksToUs(ticks)
    }

    private fun ticksToUs(ticks: Long): Long =
        ticks / timescale * 1_000_000L + (ticks % timescale) * 1_000_000L / timescale

    companion object {
        private const val MAX_ANCHOR_DRIFT_US = 250_000L
    }
}

object Mp4SidxParser {

    fun parse(data: ByteArray): SidxTiming? {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        var offset = 0

        while (offset + 8 <= data.size) {
            val size32 = readU32(buffer, offset)
            val type = readType(data, offset + 4)
            var boxSize = size32
            var headerSize = 8
            if (size32 == 1L) {
                if (offset + 16 > data.size) break
                boxSize = buffer.getLong(offset + 8)
                headerSize = 16
            } else if (size32 == 0L) {
                boxSize = (data.size - offset).toLong()
            }
            if (boxSize < headerSize || boxSize > Int.MAX_VALUE) break

            if (type == "sidx")
                return parseSidx(buffer, offset + headerSize, minOf(offset + boxSize.toInt(), data.size))

            val next = offset + boxSize.toInt()
            if (next <= offset) break
            offset = next
        }
        return null
    }

    private fun parseSidx(buffer: ByteBuffer, start: Int, end: Int): SidxTiming? {
        var p = start
        if (p + 4 > end) return null
        val version = buffer.get(p).toInt() and 0xFF
        p += 4

        p += 4
        if (p + 4 > end) return null
        val timescale = readU32(buffer, p).toInt()
        p += 4

        if (p + (if (version == 0) 8 else 16) > end) return null
        val earliestPresentationTime =
            if (version == 0) readU32(buffer, p) else buffer.getLong(p)
        p += if (version == 0) 8 else 16

        p += 2
        if (p + 2 > end) return null
        val referenceCount = readU16(buffer, p)
        p += 2

        if (referenceCount <= 0 || timescale <= 0) return null
        val durations = LongArray(referenceCount)
        for (i in 0 until referenceCount) {
            if (p + 12 > end) return null
            durations[i] = readU32(buffer, p + 4)
            p += 12
        }
        return SidxTiming(timescale, durations, earliestPresentationTime.coerceAtLeast(0))
    }

    private fun readU32(buffer: ByteBuffer, offset: Int): Long = buffer.getInt(offset).toLong() and 0xFFFFFFFFL
    private fun readU16(buffer: ByteBuffer, offset: Int): Int = buffer.getShort(offset).toInt() and 0xFFFF
    private fun readType(data: ByteArray, offset: Int): String =
        String(data, offset, 4, Charsets.US_ASCII)
}
