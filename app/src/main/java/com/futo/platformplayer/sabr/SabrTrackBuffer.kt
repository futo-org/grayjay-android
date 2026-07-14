package com.futo.platformplayer.sabr

import java.util.TreeMap

class SabrTrackBuffer(val formatKey: SabrFormatKey) {
    private val lock = Object()
    private val segments = TreeMap<Int, SabrSegment>()

    @Volatile
    var initSegment: SabrSegment? = null
        private set

    val segmentCount: Int get() = synchronized(lock) { segments.size }

    val highestSequence: Int get() = synchronized(lock) { if (segments.isEmpty()) -1 else segments.lastKey() }

    val lowestSequence: Int get() = synchronized(lock) { if (segments.isEmpty()) -1 else segments.firstKey() }

    fun announce(segment: SabrSegment) {
        synchronized(lock) {
            if (segment.isInit) initSegment = segment
            else segments[segment.sequenceNumber] = segment
            lock.notifyAll()
        }
    }

    fun notifyChanged() {
        synchronized(lock) { lock.notifyAll() }
    }

    fun get(sequenceNumber: Int): SabrSegment? = synchronized(lock) { segments[sequenceNumber] }

    fun snapshot(): List<SabrSegment> = synchronized(lock) { segments.values.toList() }

    fun firstAtOrAfter(minSequence: Int): SabrSegment? = synchronized(lock) {
        if (minSequence < 0) segments.firstEntry()?.value
        else segments.ceilingEntry(minSequence)?.value
    }

    fun firstCovering(positionUs: Long): SabrSegment? = synchronized(lock) {
        for (segment in segments.values)
            if (segment.endUs > positionUs) return segment
        return null
    }

    fun awaitAnnounced(minSequence: Int, timeoutMs: Long): SabrSegment? {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (true) {
                val found = if (minSequence < 0) segments.firstEntry()?.value else segments.ceilingEntry(minSequence)?.value
                if (found != null) return found
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return null
                lock.wait(remaining)
            }
        }
    }

    fun awaitCovering(positionUs: Long, timeoutMs: Long): SabrSegment? {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (true) {
                for (segment in segments.values)
                    if (segment.endUs > positionUs) return segment
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return null
                lock.wait(remaining)
            }
        }
    }

    fun awaitSequence(sequence: Int, timeoutMs: Long): SabrSegment? {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (true) {
                segments[sequence]?.let { return it }
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return null
                lock.wait(remaining)
            }
        }
    }

    fun awaitBytes(segment: SabrSegment, position: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (segment.size <= position && !segment.isComplete) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return false
                lock.wait(remaining)
            }
        }
        return segment.size > position || segment.isComplete
    }

    fun bufferedEndFromFrontUs(): Long = bufferedEndUs(Long.MIN_VALUE)

    fun bufferedExactEndUs(): Long = synchronized(lock) {
        var end = Long.MIN_VALUE
        var expected = -1
        for ((sequence, segment) in segments) {
            if (expected != -1 && sequence != expected) break
            if (!segment.isComplete || !segment.durationExact) break
            end = segment.endUs
            expected = sequence + 1
        }
        return end
    }

    fun publishableRun(): IntRange? = synchronized(lock) {
        val first = if (segments.isEmpty()) return null else segments.firstKey()
        var end = -1
        var sequence = first
        while (true) {
            val segment = segments[sequence] ?: break
            if (!segment.isComplete || !segment.durationExact) break
            end = sequence
            sequence++
        }
        return if (end < 0) null else first..end
    }

    fun exactEndFromSequence(sequence: Int): Long = synchronized(lock) {
        var end = Long.MIN_VALUE
        var expected = sequence
        for ((seq, segment) in segments.tailMap(sequence, true)) {
            if (seq != expected) break
            if (!segment.isComplete || !segment.durationExact) break
            end = segment.endUs
            expected = seq + 1
        }
        return end
    }

    fun recentStartDeltasUs(max: Int): List<Long> = synchronized(lock) {
        val deltas = ArrayList<Long>(max)
        var newer: SabrSegment? = null
        for (segment in segments.descendingMap().values) {
            val next = newer
            newer = segment
            if (next == null || next.sequenceNumber != segment.sequenceNumber + 1) continue
            val delta = next.startUs - segment.startUs
            if (delta > 0) deltas.add(delta)
            if (deltas.size >= max) break
        }
        return deltas
    }

    fun bufferedEndUs(fromUs: Long): Long = synchronized(lock) {
        var end = Long.MIN_VALUE
        var expected = -1
        for ((sequence, segment) in segments) {
            if (expected == -1) {
                if (fromUs != Long.MIN_VALUE && segment.endUs < fromUs) continue
                if (fromUs != Long.MIN_VALUE && segment.startUs > fromUs) return Long.MIN_VALUE
            }
            if (expected != -1 && sequence != expected) break
            if (!segment.isComplete) break
            end = segment.endUs
            expected = sequence + 1
        }
        return end
    }

    fun lastCompletedSequence(fromUs: Long): Int = synchronized(lock) {
        var last = -1
        var expected = -1
        for ((sequence, segment) in segments) {
            if (expected == -1) {
                if (fromUs != Long.MIN_VALUE && segment.endUs < fromUs) continue
                if (fromUs != Long.MIN_VALUE && segment.startUs > fromUs) return -1
            }
            if (expected != -1 && sequence != expected) break
            if (!segment.isComplete) break
            last = sequence
            expected = sequence + 1
        }
        return last
    }

    fun lastCompletedFromFront(): Int = lastCompletedSequence(Long.MIN_VALUE)

    fun discard(segment: SabrSegment) {
        synchronized(lock) {
            if (segment.isComplete) return
            if (segment.isInit) {
                if (initSegment === segment) initSegment = null
            } else if (segments[segment.sequenceNumber] === segment) {
                segments.remove(segment.sequenceNumber)
            }
            lock.notifyAll()
        }
    }

    fun evictBeforeSequence(sequence: Int) {
        synchronized(lock) {
            var evicted = false
            val iterator = segments.headMap(sequence, false).entries.iterator()
            while (iterator.hasNext()) {
                if (!iterator.next().value.isComplete) break
                iterator.remove()
                evicted = true
            }
            if (evicted) lock.notifyAll()
        }
    }

    fun evictBefore(positionUs: Long) {
        synchronized(lock) {
            var evicted = false
            var expected = -1
            val iterator = segments.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val sequence = entry.key
                val segment = entry.value
                if (expected != -1 && sequence != expected) break
                if (!segment.isComplete) break
                if (segment.endUs >= positionUs) break
                iterator.remove()
                expected = sequence + 1
                evicted = true
            }
            if (evicted) lock.notifyAll()
        }
    }

    fun clear() {
        synchronized(lock) {
            segments.clear()
            lock.notifyAll()
        }
    }
}
