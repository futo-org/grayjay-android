package com.futo.platformplayer.sabr

import com.futo.platformplayer.sabr.proto.FormatId

class SabrFormat(
    val itag: Int,
    val lastModified: Long,
    val xtags: String,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val width: Int,
    val height: Int,
    val fps: Int,
    val audioChannels: Int,
    val audioSampleRate: Int,
    val language: String?,
    val isOriginalAudio: Boolean,
    val isDrc: Boolean
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")

    val key: SabrFormatKey = SabrFormatKey(itag, lastModified, xtags)

    override fun equals(other: Any?): Boolean = other is SabrFormat && other.key == key
    override fun hashCode(): Int = key.hashCode()

    val containerMimeType: String get() = mimeType.substringBefore(';').trim()

    val sampleMimeType: String? get() = SabrCodecs.sampleMimeType(codecs, containerMimeType)

    val codecName: String get() = SabrCodecs.codecName(codecs)

    val qualityLabel: String get() = when {
        height <= 0 -> "itag $itag"
        fps > 30 -> "${height}p$fps"
        else -> "${height}p"
    }

    val videoLabel: String get() = qualityLabel

    val audioLabel: String get() = buildString {
        val lang = language?.takeIf { it.isNotBlank() && !it.equals("Unknown", true) }
        if (lang != null) append("$lang ")
        if (bitrate > 0) append("${bitrate / 1000}kbps") else append("itag $itag")
        if (audioChannels > 2) append(" ${audioChannels}ch")
        if (isDrc) append(" (normalized)")
        if (isOriginalAudio) append(" (original)")
    }.trim()

    val detailLabel: String get() = listOfNotNull("UMP", codecName.ifBlank { null }).joinToString(" · ")

    fun toFormatId(): FormatId {
        val builder = FormatId.newBuilder()
            .setItag(itag)
            .setLmt(lastModified)
        if (xtags.isNotEmpty()) builder.setXtags(xtags)
        return builder.build()
    }

    override fun toString(): String = "itag=$itag lmt=$lastModified ${if (isVideo) "${width}x$height" else "${bitrate}bps"}"
}

data class SabrFormatKey(val itag: Int, val lastModified: Long, val xtags: String) {
    companion object {
        fun of(itag: Int, lmt: Long, xtags: String?) = SabrFormatKey(itag, lmt, xtags ?: "")
    }
}

object SabrCodecs {
    fun codecName(codecs: String): String {
        val c = codecs.lowercase()
        return when {
            c.startsWith("avc1") || c.startsWith("avc3") -> "H.264"
            c.startsWith("hev1") || c.startsWith("hvc1") -> "H.265"
            c.startsWith("av01") -> "AV1"
            c.startsWith("vp9") || c.startsWith("vp09") -> "VP9"
            c.startsWith("vp8") || c.startsWith("vp08") -> "VP8"
            c.startsWith("mp4a") -> "AAC"
            c.startsWith("opus") -> "Opus"
            c.startsWith("vorbis") -> "Vorbis"
            c.startsWith("ec-3") -> "EAC3"
            c.startsWith("ac-3") -> "AC3"
            else -> codecs.substringBefore('.').trim()
        }
    }

    fun sampleMimeType(codecs: String, containerMimeType: String): String? {
        val c = codecs.lowercase()
        return when {
            c.startsWith("avc1") || c.startsWith("avc3") -> "video/avc"
            c.startsWith("hev1") || c.startsWith("hvc1") -> "video/hevc"
            c.startsWith("av01") -> "video/av01"
            c.startsWith("vp9") || c.startsWith("vp09") -> "video/x-vnd.on2.vp9"
            c.startsWith("vp8") || c.startsWith("vp08") -> "video/x-vnd.on2.vp8"
            c.startsWith("mp4a") -> "audio/mp4a-latm"
            c.startsWith("opus") -> "audio/opus"
            c.startsWith("vorbis") -> "audio/vorbis"
            c.startsWith("ec-3") -> "audio/eac3"
            c.startsWith("ac-3") -> "audio/ac3"
            else -> null
        }
    }
}
