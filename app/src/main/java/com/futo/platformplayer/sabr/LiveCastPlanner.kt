package com.futo.platformplayer.sabr

class LiveCastPlanner(
    private val config: Config = Config(),
    private val clock: () -> Long = System::currentTimeMillis
) {

    class Config(
        val windowSegments: Int = 8,
        val maxLagSegments: Int = 40,
        val minStartSegments: Int = 3,
        val playbackLagSegments: Int = 4
    ) {
        init {
            require(minStartSegments in 2..windowSegments) { "minStartSegments must be between 2 and windowSegments" }
            require(playbackLagSegments in 0 until windowSegments) { "playbackLagSegments must be under windowSegments" }
            require(maxLagSegments > windowSegments) { "maxLagSegments must exceed the window" }
        }
    }

    class Track(val low: Int, val head: Int)

    private val lock = Any()

    private var edge = -1
    private var publishedStart = -1

    private val commits = ArrayDeque<Pair<Long, Int>>()
    private var emitted: IntRange? = null
    private var floor = -1

    private val position = HashMap<Int, Int>()

    var segmentMs: Long = 2000
        set(value) { field = value.coerceIn(500, 15_000) }

    val window: IntRange? get() = synchronized(lock) { emitted }
    val liveEdge: Int get() = synchronized(lock) { edge }

    fun onLiveEdge(headSequence: Int) = synchronized(lock) {
        if (headSequence > edge) edge = headSequence
    }

    fun noteRequest(role: Int, sequence: Int) = synchronized(lock) {
        position[role] = sequence
    }

    fun reset() = synchronized(lock) {
        publishedStart = -1
        commits.clear()
        emitted = null
        floor = -1
        position.clear()
    }

    private fun trailingPosition(): Int {
        val trail = position.values.minOrNull() ?: return -1
        var clamped = trail
        if (floor >= 0) clamped = maxOf(clamped, floor)
        if (edge >= 0) clamped = maxOf(clamped, edge - config.maxLagSegments)
        return clamped
    }

    fun retainFrom(): Int = synchronized(lock) {
        val promised = promisedStartLocked()
        if (promised >= 0) {
            val trail = trailingPosition()
            floor = maxOf(floor, if (trail >= 0) minOf(promised, trail) else promised)
        }
        return floor
    }

    private fun promisedStartLocked(): Int {
        val cutoff = clock() - (2 * segmentMs + GRACE_SLACK_MS)
        var promised = -1
        for ((at, start) in commits) {
            if (at > cutoff) break
            promised = start
        }
        return if (promised >= 0) promised else commits.firstOrNull()?.second ?: publishedStart
    }

    fun planWindow(tracks: Collection<Track>): IntRange? = synchronized(lock) {
        if (tracks.isEmpty()) return null

        val low = tracks.maxOf { it.low }
        val head = tracks.minOf { it.head }
        if (low < 0 || head < low) return null

        val previous = emitted

        if (previous == null && head - low + 1 < config.minStartSegments) return null

        var start = maxOf(low, head - config.windowSegments + 1)

        trailingPosition().takeIf { it >= 0 }?.let { start = minOf(start, it - config.playbackLagSegments) }

        if (previous != null && low <= previous.last) {
            start = minOf(start, previous.last)
            start = maxOf(start, previous.first)
        }

        start = maxOf(start, low)
        if (start > head) return null
        return start..head
    }

    private companion object {
        const val GRACE_SLACK_MS = 5_000L
    }

    fun commit(range: IntRange): Boolean = synchronized(lock) {
        val previous = emitted
        val expired = previous != null && range.first > previous.last

        val now = clock()
        emitted = range
        publishedStart = maxOf(publishedStart, range.first)

        commits.addLast(now to range.first)
        while (commits.size > 1 && commits.first().first < now - (2 * segmentMs + GRACE_SLACK_MS) * 2)
            commits.removeFirst()

        retainFrom()
        return expired
    }
}
