package com.futo.platformplayer.sabr

import java.util.TreeMap

class CastTimeline(val timescale: Int, val presentationOffsetTicks: Long = 0) {

    private class Entry(val startTicks: Long, val durationTicks: Long)

    private val lock = Any()
    private val entries = TreeMap<Int, Entry>()

    val firstNumber: Int get() = synchronized(lock) { if (entries.isEmpty()) -1 else entries.firstKey() }
    val lastNumber: Int get() = synchronized(lock) { if (entries.isEmpty()) -1 else entries.lastKey() }
    val isEmpty: Boolean get() = synchronized(lock) { entries.isEmpty() }

    fun put(sequence: Int, startTicks: Long, durationTicks: Long) = synchronized(lock) {
        entries[sequence] = Entry(startTicks, durationTicks.coerceAtLeast(1))
    }

    fun contains(sequence: Int): Boolean = synchronized(lock) { entries.containsKey(sequence) }

    fun dropBefore(sequence: Int) = synchronized(lock) {
        entries.headMap(sequence, false).clear()
    }

    fun clear() = synchronized(lock) { entries.clear() }

    fun truncateTo(lastSequence: Int): CastTimeline = apply {
        if (lastSequence < 0 || lastSequence == Int.MAX_VALUE) return@apply
        synchronized(lock) { entries.tailMap(lastSequence, false).clear() }
    }

    fun startUs(sequence: Int): Long? = synchronized(lock) {
        entries[sequence]?.let { ticksToUs(it.startTicks) }
    }

    fun durationUs(sequence: Int): Long? = synchronized(lock) {
        entries[sequence]?.let { ticksToUs(it.durationTicks) }
    }

    fun midUs(sequence: Int): Long? = synchronized(lock) {
        val entry = entries[sequence] ?: return null
        return ticksToUs(entry.startTicks + entry.durationTicks / 2)
    }

    fun totalUs(): Long = synchronized(lock) {
        val last = entries.lastEntry()?.value ?: return 0
        return ticksToUs(last.startTicks + last.durationTicks - presentationOffsetTicks)
    }

    val presentationOffsetUs: Long get() = ticksToUs(presentationOffsetTicks)

    fun segmentTimelineXml(from: Int, to: Int): String? = synchronized(lock) {
        if (from > to) return null
        val window = entries.subMap(from, true, to, true)
        if (window.isEmpty()) return null
        if (window.size != to - from + 1) return null

        val sb = StringBuilder("<SegmentTimeline>\n")
        var pendingStart = -1L
        var pendingDuration = -1L
        var pendingRepeat = 0
        var pendingEmitT = true
        var previousEnd = -1L

        for ((_, entry) in window) {
            val contiguous = previousEnd == entry.startTicks
            if (pendingDuration == entry.durationTicks && contiguous) {
                pendingRepeat++
            } else {
                emit(sb, pendingStart, pendingDuration, pendingRepeat, pendingEmitT)
                pendingStart = entry.startTicks
                pendingDuration = entry.durationTicks
                pendingRepeat = 0
                pendingEmitT = !contiguous
            }
            previousEnd = entry.startTicks + entry.durationTicks
        }
        emit(sb, pendingStart, pendingDuration, pendingRepeat, pendingEmitT)

        sb.append("</SegmentTimeline>\n")
        return sb.toString()
    }

    private fun emit(sb: StringBuilder, start: Long, duration: Long, repeat: Int, emitT: Boolean) {
        if (duration < 0) return
        val t = if (emitT) " t=\"$start\"" else ""
        if (repeat > 0) sb.append("<S$t d=\"$duration\" r=\"$repeat\"/>\n")
        else sb.append("<S$t d=\"$duration\"/>\n")
    }

    private fun ticksToUs(ticks: Long): Long =
        ticks / timescale * MICROS_PER_SECOND + (ticks % timescale) * MICROS_PER_SECOND / timescale

    companion object {
        private const val MICROS_PER_SECOND = 1_000_000L

        private const val LIVE_DELAY_SEGMENTS = 3

        fun livePresentationDelayMs(segmentMs: Long, windowDepthMs: Long): Long {
            val target = LIVE_DELAY_SEGMENTS * segmentMs
            val room = windowDepthMs - segmentMs
            if (room < segmentMs) return segmentMs
            return target.coerceAtMost(room)
        }

        fun fromSidx(startNumber: Int, timing: SidxTiming): CastTimeline {
            val timeline = CastTimeline(timing.timescale, timing.baseTicks)
            var start = timing.baseTicks
            for (i in timing.durations.indices) {
                timeline.put(startNumber + i, start, timing.durations[i])
                start += timing.durations[i]
            }
            return timeline
        }

        private const val MAX_SEGMENTS = 100_000

        fun uniform(startNumber: Int, endNumber: Int, segmentMs: Long, totalMs: Long): CastTimeline? {
            if (endNumber < startNumber || segmentMs <= 0) return null
            val count = endNumber - startNumber + 1
            if (count > MAX_SEGMENTS) return null
            val timeline = CastTimeline(1000)
            var start = 0L
            for (i in 0 until count) {
                val duration = if (i == count - 1 && totalMs > start) (totalMs - start) else segmentMs
                timeline.put(startNumber + i, start, duration.coerceAtLeast(1))
                start += duration
            }
            return timeline
        }
    }
}
