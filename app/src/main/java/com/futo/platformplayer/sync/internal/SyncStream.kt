package com.futo.platformplayer.sync.internal

class SyncStream(expectedSize: Int, val opcode: UByte, val subOpcode: UByte) {
    companion object {
        const val MAXIMUM_SIZE = 10_000_000
    }

    private val _buffer: ByteArray = ByteArray(expectedSize)
    private val _expectedSize: Int = expectedSize
    var bytesReceived: Int = 0
        private set
    var isComplete: Boolean = false
        private set

    init {
        if (expectedSize > MAXIMUM_SIZE) {
            throw Exception("$expectedSize exceeded maximum size $MAXIMUM_SIZE")
        }
    }

    fun add(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) {
        require(offset >= 0 && length >= 0 && offset + length <= data.size) { "Invalid offset or length" }

        val remainingBytes = _expectedSize - bytesReceived
        if (length > remainingBytes) {
            throw Exception("More bytes received $length than expected remaining $remainingBytes")
        }
        data.copyInto(
            destination = _buffer,
            destinationOffset = bytesReceived,
            startIndex = offset,
            endIndex = offset + length
        )
        bytesReceived += length
        isComplete = bytesReceived == _expectedSize
    }

    fun getBytes(): ByteArray {
        return _buffer
    }
}