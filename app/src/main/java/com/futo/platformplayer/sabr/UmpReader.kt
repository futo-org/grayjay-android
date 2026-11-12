package com.futo.platformplayer.sabr

import java.io.EOFException
import java.io.InputStream

object UmpPartType {
    const val ONESIE_HEADER = 10
    const val ONESIE_DATA = 11
    const val MEDIA_HEADER = 20
    const val MEDIA = 21
    const val MEDIA_END = 22
    const val LIVE_METADATA = 31
    const val HOSTNAME_CHANGE_HINT = 32
    const val NEXT_REQUEST_POLICY = 35
    const val FORMAT_INITIALIZATION_METADATA = 42
    const val SABR_REDIRECT = 43
    const val SABR_ERROR = 44
    const val SABR_SEEK = 45
    const val RELOAD_PLAYER_RESPONSE = 46
    const val PLAYBACK_START_POLICY = 47
    const val ALLOWED_CACHED_FORMATS = 48
    const val REQUEST_IDENTIFIER = 52
    const val REQUEST_CANCELLATION_POLICY = 53
    const val TIMELINE_CONTEXT = 55
    const val SABR_CONTEXT_UPDATE = 57
    const val STREAM_PROTECTION_STATUS = 58
    const val SABR_CONTEXT_SENDING_POLICY = 59
    const val SNACKBAR_MESSAGE = 67
    const val NETWORK_TIMING = 68
    const val CUEPOINT_LIST = 69

    private val NAMES = mapOf(
        10 to "ONESIE_HEADER", 11 to "ONESIE_DATA", 12 to "ONESIE_ENCRYPTED_MEDIA",
        20 to "MEDIA_HEADER", 21 to "MEDIA", 22 to "MEDIA_END",
        31 to "LIVE_METADATA", 32 to "HOSTNAME_CHANGE_HINT", 33 to "LIVE_METADATA_PROMISE",
        34 to "LIVE_METADATA_PROMISE_CANCELLATION", 35 to "NEXT_REQUEST_POLICY",
        36 to "USTREAMER_VIDEO_AND_FORMAT_DATA", 37 to "FORMAT_SELECTION_CONFIG",
        38 to "USTREAMER_SELECTED_MEDIA_STREAM", 42 to "FORMAT_INIT", 43 to "SABR_REDIRECT",
        44 to "SABR_ERROR", 45 to "SABR_SEEK", 46 to "RELOAD_PLAYER", 47 to "PLAYBACK_START_POLICY",
        48 to "ALLOWED_CACHED_FORMATS", 49 to "START_BW_SAMPLING_HINT", 50 to "PAUSE_BW_SAMPLING_HINT",
        51 to "SELECTABLE_FORMATS", 52 to "REQUEST_IDENTIFIER", 53 to "REQUEST_CANCELLATION_POLICY",
        54 to "ONESIE_PREFETCH_REJECTION", 55 to "TIMELINE_CONTEXT", 56 to "REQUEST_PIPELINING",
        57 to "SABR_CONTEXT_UPDATE", 58 to "STREAM_PROTECTION_STATUS", 59 to "SABR_CONTEXT_SENDING_POLICY",
        60 to "LAWNMOWER_POLICY", 61 to "SABR_ACK", 62 to "END_OF_TRACK", 63 to "CACHE_LOAD_POLICY",
        64 to "LAWNMOWER_MESSAGING_POLICY", 65 to "PREWARM_CONNECTION", 66 to "PLAYBACK_DEBUG_INFO",
        67 to "SNACKBAR_MESSAGE", 68 to "NETWORK_TIMING", 69 to "CUEPOINT_LIST",
        70 to "STITCHED_REGIONS_OF_INTEREST", 71 to "STITCHED_SEGMENTS_METADATA_LIST",
        72 to "PROBE_SUCCESS"
    )

    fun name(type: Int): String = NAMES[type] ?: "UNKNOWN($type)"

}

class UmpPart(val type: Int, val data: ByteArray)

class UmpReader(private val input: InputStream) {

    fun next(): UmpPart? {
        val type = readVarInt() ?: return null
        val length = readVarInt() ?: throw EOFException("UMP part $type truncated before length")
        if (length < 0 || length > MAX_PART_SIZE)
            throw IllegalStateException("UMP part $type has implausible length $length")

        val data = ByteArray(length.toInt())
        var read = 0
        while (read < data.size) {
            val r = input.read(data, read, data.size - read)
            if (r < 0) throw EOFException("UMP part $type truncated: got $read of ${data.size}")
            read += r
        }
        return UmpPart(type.toInt(), data)
    }

    private fun readByte(): Int {
        val b = input.read()
        if (b < 0) throw EOFException("Unexpected end of UMP stream")
        return b
    }

    private fun readVarInt(): Long? {
        val first = input.read()
        if (first < 0) return null

        val size = sizeOf(first)
        if (size == 1)
            return first.toLong()

        var trailing = 0L
        for (i in 0 until size - 1)
            trailing = trailing or (readByte().toLong() shl (8 * i))

        if (size == 5)
            return trailing

        val valueBits = 8 - size
        val head = (first and ((1 shl valueBits) - 1)).toLong()
        return head or (trailing shl valueBits)
    }

    companion object {
        private const val MAX_PART_SIZE = 64L * 1024 * 1024

        fun sizeOf(firstByte: Int): Int = when {
            firstByte < 128 -> 1
            firstByte < 192 -> 2
            firstByte < 224 -> 3
            firstByte < 240 -> 4
            else -> 5
        }

        fun decodeVarInt(bytes: ByteArray, offset: Int): Pair<Long, Int> {
            if (offset >= bytes.size) return Pair(-1, offset)
            val first = bytes[offset].toInt() and 0xFF
            val size = sizeOf(first)
            if (size == 1) return Pair(first.toLong(), offset + 1)
            if (offset + size > bytes.size) return Pair(-1, bytes.size)

            var trailing = 0L
            for (i in 1 until size)
                trailing = trailing or ((bytes[offset + i].toInt() and 0xFF).toLong() shl (8 * (i - 1)))

            if (size == 5) return Pair(trailing, offset + 5)
            val valueBits = 8 - size
            val head = (first and ((1 shl valueBits) - 1)).toLong()
            return Pair(head or (trailing shl valueBits), offset + size)
        }
    }
}
