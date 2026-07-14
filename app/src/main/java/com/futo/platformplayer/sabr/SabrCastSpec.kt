package com.futo.platformplayer.sabr

import android.util.Base64
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSUMPSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private class SabrUrlFormat(
    @SerialName("itag") val itag: Int,
    @SerialName("last_modified") val lastModified: Long,
    @SerialName("xtags") val xtags: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("codecs") val codecs: String,
    @SerialName("bitrate") val bitrate: Int,
    @SerialName("width") val width: Int,
    @SerialName("height") val height: Int,
    @SerialName("fps") val fps: Int,
    @SerialName("audio_channels") val audioChannels: Int,
    @SerialName("audio_sample_rate") val audioSampleRate: Int,
    @SerialName("language") val language: String?,
    @SerialName("is_original_audio") val isOriginalAudio: Boolean,
    @SerialName("is_drc") val isDrc: Boolean,
)

@Serializable
private class SabrUrlSpec(
    @SerialName("server_abr_streaming_url") val serverAbrStreamingUrl: String,
    @SerialName("ustreamer_config") val ustreamerConfig: String,
    @SerialName("video_id") val videoId: String,
    @SerialName("is_live") val isLive: Boolean,
    @SerialName("duration_us") val durationUs: Long,
    @SerialName("video_formats") val videoFormats: List<SabrUrlFormat>,
    @SerialName("audio_formats") val audioFormats: List<SabrUrlFormat>,
    @SerialName("po_token") val poToken: String?,
    @SerialName("client_name") val clientName: Int,
    @SerialName("client_version") val clientVersion: String,
    @SerialName("os_name") val osName: String,
    @SerialName("os_version") val osVersion: String,
)

private val sabrJson = Json { encodeDefaults = true }

private fun SabrFormat.toUrlFormat() = SabrUrlFormat(
    itag = itag,
    lastModified = lastModified,
    xtags = xtags,
    mimeType = mimeType,
    codecs = codecs,
    bitrate = bitrate,
    width = width,
    height = height,
    fps = fps,
    audioChannels = audioChannels,
    audioSampleRate = audioSampleRate,
    language = language,
    isOriginalAudio = isOriginalAudio,
    isDrc = isDrc,
)

fun buildSabrUmpUrl(
    source: JSUMPSource,
    videoFormat: SabrFormat?,
    audioFormat: SabrFormat?,
): String {
    val spec = SabrUrlSpec(
        serverAbrStreamingUrl = source.url,
        ustreamerConfig = source.ustreamerConfig,
        videoId = source.videoId,
        isLive = source.isLive,
        durationUs = if (source.duration > 0) source.duration * 1_000_000L else -1L,
        videoFormats = listOfNotNull(videoFormat).map { it.toUrlFormat() },
        audioFormats = listOfNotNull(audioFormat).map { it.toUrlFormat() },
        poToken = source.poToken,
        clientName = source.clientName,
        clientVersion = source.clientVersion,
        osName = source.osName,
        osVersion = source.osVersion,
    )
    val json = sabrJson.encodeToString(spec)
    val b64 = Base64.encodeToString(
        json.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )
    val authority = source.videoId.ifBlank { "video" }
    return "sabrump://$authority?spec=$b64"
}
