package com.futo.platformplayer.sabr

class SabrSegment(
    val formatKey: SabrFormatKey,
    val sequenceNumber: Int,
    val isInit: Boolean,
    val startUs: Long,
    durationUs: Long,
    val contentLength: Int,
    val startTicks: Long = 0,
    val timescale: Int = 0
) {
    @Volatile
    var durationUs: Long = durationUs
        private set

    @Volatile
    var durationExact: Boolean = false
        private set

    fun setDuration(us: Long, exact: Boolean) {
        if (us <= 0) return
        if (durationExact && !exact) return
        durationUs = us
        durationExact = exact
    }

    private var data: ByteArray = ByteArray(if (contentLength > 0) contentLength else INITIAL_CAPACITY)

    @Volatile
    var size: Int = 0
        private set

    @Volatile
    var isComplete: Boolean = false
        private set

    val endUs: Long get() = startUs + durationUs

    @Synchronized
    fun append(bytes: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        val required = size + length
        if (required > data.size) {
            var capacity = data.size.coerceAtLeast(INITIAL_CAPACITY)
            while (capacity < required) capacity *= 2
            data = data.copyOf(capacity)
        }
        System.arraycopy(bytes, offset, data, size, length)
        size += length
    }

    @Synchronized
    fun read(position: Int, dest: ByteArray, destOffset: Int, length: Int): Int {
        val available = size - position
        if (available <= 0) return 0
        val toCopy = minOf(available, length)
        System.arraycopy(data, position, dest, destOffset, toCopy)
        return toCopy
    }

    @Synchronized
    fun toByteArray(): ByteArray = data.copyOf(size)

    fun markComplete() {
        isComplete = true
    }

    companion object {
        private const val INITIAL_CAPACITY = 32 * 1024
    }
}
