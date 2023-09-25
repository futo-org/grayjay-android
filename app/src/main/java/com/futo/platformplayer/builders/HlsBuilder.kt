package com.futo.platformplayer.builders

import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import java.io.PrintWriter
import java.io.StringWriter

class HlsBuilder {
    companion object{
        fun generateOnDemandHLS(vidSource: IVideoSource, vidUrl: String, audioSource: IAudioSource?, audioUrl: String?, subtitleSource: ISubtitleSource?, subtitleUrl: String?): String {
            val hlsBuilder = StringWriter()
            PrintWriter(hlsBuilder).use { writer ->
                writer.println("#EXTM3U")

                // Audio
                if (audioSource != null && audioUrl != null) {
                    val audioFormat = audioSource.container.substringAfter("/")
                    writer.println("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",LANGUAGE=\"en\",NAME=\"English\",AUTOSELECT=YES,DEFAULT=YES,URI=\"${audioUrl.replace("&", "&amp;")}\",FORMAT=\"$audioFormat\"")
                }

                // Subtitles
                if (subtitleSource != null && subtitleUrl != null) {
                    val subtitleFormat = subtitleSource.format ?: "text/vtt"
                    writer.println("#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",LANGUAGE=\"en\",NAME=\"English\",AUTOSELECT=YES,DEFAULT=YES,URI=\"${subtitleUrl.replace("&", "&amp;")}\",FORMAT=\"$subtitleFormat\"")
                }

                // Video
                val videoFormat = vidSource.container.substringAfter("/")
                writer.println("#EXT-X-STREAM-INF:BANDWIDTH=100000,CODECS=\"${vidSource.codec}\",RESOLUTION=${vidSource.width}x${vidSource.height}${if (audioSource != null) ",AUDIO=\"audio\"" else ""}${if (subtitleSource != null) ",SUBTITLES=\"subs\"" else ""},FORMAT=\"$videoFormat\"")
                writer.println(vidUrl.replace("&", "&amp;"))
            }

            return hlsBuilder.toString()
        }
    }
}