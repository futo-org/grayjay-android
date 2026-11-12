package com.futo.platformplayer.sabr.media3

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import com.futo.platformplayer.sabr.SabrFormat

@UnstableApi
object SabrFormats {
    fun toMedia3Format(format: SabrFormat): Format {
        val builder = Format.Builder()
            .setId(format.itag.toString())
            .setContainerMimeType(format.containerMimeType)
            .setSampleMimeType(format.sampleMimeType)
            .setCodecs(format.codecs)
            .setAverageBitrate(format.bitrate)
            .setPeakBitrate(format.bitrate)

        if (format.isVideo) {
            builder.setWidth(if (format.width > 0) format.width else Format.NO_VALUE)
            builder.setHeight(if (format.height > 0) format.height else Format.NO_VALUE)
            if (format.fps > 0) builder.setFrameRate(format.fps.toFloat())
            builder.setLabel(format.videoLabel)
        } else {
            builder.setChannelCount(if (format.audioChannels > 0) format.audioChannels else Format.NO_VALUE)
            builder.setSampleRate(if (format.audioSampleRate > 0) format.audioSampleRate else Format.NO_VALUE)
            format.language?.let { builder.setLanguage(it) }
            var roleFlags = 0
            if (format.isOriginalAudio) roleFlags = roleFlags or androidx.media3.common.C.ROLE_FLAG_MAIN
            else roleFlags = roleFlags or androidx.media3.common.C.ROLE_FLAG_DUB
            if (format.isDrc) roleFlags = roleFlags or androidx.media3.common.C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND
            builder.setRoleFlags(roleFlags)
            if (format.isOriginalAudio && !format.isDrc)
                builder.setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
            builder.setLabel(format.audioLabel)
        }

        return builder.build()
    }
}
