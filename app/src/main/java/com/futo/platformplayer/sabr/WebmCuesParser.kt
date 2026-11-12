package com.futo.platformplayer.sabr

object WebmCuesParser {

    private const val ID_SEGMENT = 0x18538067L
    private const val ID_INFO = 0x1549A966L
    private const val ID_TIMECODE_SCALE = 0x2AD7B1L
    private const val ID_DURATION = 0x4489L
    private const val ID_CUES = 0x1C53BB6BL
    private const val ID_CUE_POINT = 0xBBL
    private const val ID_CUE_TIME = 0xB3L

    private const val NANOS_PER_SECOND = 1_000_000_000L
    private const val DEFAULT_TIMECODE_SCALE = 1_000_000L

    fun parse(data: ByteArray): SidxTiming? {
        val segment = findSegmentContent(data) ?: return null

        var timecodeScaleNs = DEFAULT_TIMECODE_SCALE
        var durationTicks = 0.0
        val cueTimes = ArrayList<Long>()

        var pos = segment.first
        val end = segment.second
        while (pos < end) {
            val el = readElement(data, pos, end) ?: break
            when (el.id) {
                ID_INFO -> {
                    var p = el.contentStart
                    while (p < el.contentEnd) {
                        val child = readElement(data, p, el.contentEnd) ?: break
                        when (child.id) {
                            ID_TIMECODE_SCALE -> readUInt(data, child)?.let { timecodeScaleNs = it }
                            ID_DURATION -> readFloat(data, child)?.let { durationTicks = it }
                        }
                        p = child.contentEnd
                    }
                }

                ID_CUES -> {
                    var p = el.contentStart
                    while (p < el.contentEnd) {
                        val cuePoint = readElement(data, p, el.contentEnd) ?: break
                        if (cuePoint.id == ID_CUE_POINT) {
                            var q = cuePoint.contentStart
                            while (q < cuePoint.contentEnd) {
                                val child = readElement(data, q, cuePoint.contentEnd) ?: break
                                if (child.id == ID_CUE_TIME) {
                                    readUInt(data, child)?.let { cueTimes.add(it) }
                                    break
                                }
                                q = child.contentEnd
                            }
                        }
                        p = cuePoint.contentEnd
                    }
                }
            }
            pos = el.contentEnd
        }

        if (timecodeScaleNs <= 0) return null

        if (NANOS_PER_SECOND % timecodeScaleNs != 0L) return null
        val timescale = (NANOS_PER_SECOND / timecodeScaleNs).toInt()
        if (timescale <= 0) return null

        val times = cueTimes.distinct().sorted()
        if (times.size < 2) return null

        val durations = LongArray(times.size)
        for (i in 0 until times.size - 1)
            durations[i] = (times[i + 1] - times[i]).coerceAtLeast(1)

        val lastStart = times.last()
        val tail = (durationTicks.toLong() - lastStart)
        durations[durations.size - 1] =
            if (durationTicks > 0 && tail > 0) tail else durations.getOrElse(durations.size - 2) { 1L }

        return SidxTiming(timescale, durations, times.first())
    }

    private class Element(val id: Long, val contentStart: Int, val contentEnd: Int)

    private fun findSegmentContent(data: ByteArray): Pair<Int, Int>? {
        var pos = 0
        while (pos < data.size) {
            val el = readElement(data, pos, data.size) ?: return null
            if (el.id == ID_SEGMENT) return Pair(el.contentStart, el.contentEnd)
            pos = el.contentEnd
        }
        return null
    }

    private fun readElement(data: ByteArray, pos: Int, limit: Int): Element? {
        if (pos < 0 || pos >= limit) return null

        val idLen = vintLength(data[pos])
        if (idLen == 0 || pos + idLen > limit) return null
        var id = 0L
        for (i in 0 until idLen) id = (id shl 8) or (data[pos + i].toLong() and 0xFF)

        val sizePos = pos + idLen
        if (sizePos >= limit) return null
        val sizeLen = vintLength(data[sizePos])
        if (sizeLen == 0 || sizePos + sizeLen > limit) return null

        var size = (data[sizePos].toInt() and (0xFF shr sizeLen)).toLong()
        var unknown = size == (0xFF shr sizeLen).toLong()
        for (i in 1 until sizeLen) {
            val b = data[sizePos + i].toInt() and 0xFF
            if (b != 0xFF) unknown = false
            size = (size shl 8) or b.toLong()
        }

        val contentStart = sizePos + sizeLen
        val contentEnd = if (unknown) limit
            else (contentStart + size).let { if (it < contentStart || it > limit) limit else it.toInt() }

        return Element(id, contentStart, contentEnd)
    }

    private fun vintLength(b: Byte): Int {
        val v = b.toInt() and 0xFF
        if (v == 0) return 0
        var mask = 0x80
        for (len in 1..8) {
            if (v and mask != 0) return len
            mask = mask shr 1
        }
        return 0
    }

    private fun readUInt(data: ByteArray, el: Element): Long? {
        val len = el.contentEnd - el.contentStart
        if (len <= 0 || len > 8) return null
        var v = 0L
        for (i in 0 until len) v = (v shl 8) or (data[el.contentStart + i].toLong() and 0xFF)
        return v
    }

    private fun readFloat(data: ByteArray, el: Element): Double? {
        return when (el.contentEnd - el.contentStart) {
            4 -> {
                var bits = 0
                for (i in 0 until 4) bits = (bits shl 8) or (data[el.contentStart + i].toInt() and 0xFF)
                Float.fromBits(bits).toDouble()
            }
            8 -> {
                var bits = 0L
                for (i in 0 until 8) bits = (bits shl 8) or (data[el.contentStart + i].toLong() and 0xFF)
                Double.fromBits(bits)
            }
            else -> null
        }
    }
}
