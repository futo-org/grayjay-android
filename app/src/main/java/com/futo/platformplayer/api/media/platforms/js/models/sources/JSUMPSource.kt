package com.futo.platformplayer.api.media.platforms.js.models.sources

import android.util.Base64
import com.caoccao.javet.values.reference.V8ValueArray
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.getOrThrowList
import com.futo.platformplayer.invokeV8
import com.futo.platformplayer.requireSourcePlugin
import com.futo.platformplayer.sabr.SabrFormat
import com.futo.platformplayer.sabr.SabrStreamSpec

class JSUMPSource : JSSource, IVideoSource {
    private val ctx = "UMPSource"

    val url: String
    val ustreamerConfig: String
    val videoId: String
    val isLive: Boolean

    override val name: String
    override val width: Int
    override val height: Int
    override val container: String
    override val codec: String
    override val bitrate: Int?
    override val duration: Long
    override val priority: Boolean
    override val language: String?
    override val original: Boolean?

    val clientName: Int
    val clientVersion: String
    val osName: String
    val osVersion: String

    val videoFormats: List<SabrFormat>
    val audioFormats: List<SabrFormat>
    val format: SabrFormat?
    val poToken: String?

    constructor(plugin: JSClient, obj: V8ValueObject) : super(TYPE_UMP, plugin, obj) {
        val cfg = plugin.config
        url = obj.getOrThrow(cfg, "url", ctx)
        poToken = obj.getOrDefault<String>(cfg, "poToken", ctx, null)
        ustreamerConfig = obj.getOrThrow(cfg, "ustreamerConfig", ctx)
        videoId = obj.getOrDefault(cfg, "videoId", ctx, "") ?: ""
        isLive = obj.getOrDefault(cfg, "isLive", ctx, false) ?: false
        name = obj.getOrDefault(cfg, "name", ctx, "UMP") ?: "UMP"
        width = obj.getOrDefault<Int>(cfg, "width", ctx, 0) ?: 0
        height = obj.getOrDefault<Int>(cfg, "height", ctx, 0) ?: 0
        container = "application/vnd.yt-ump"
        codec = ""
        bitrate = null
        duration = obj.getOrDefault<Int>(cfg, "duration", ctx, 0)?.toLong() ?: 0L
        priority = obj.getOrDefault(cfg, "priority", ctx, false) ?: false
        language = obj.getOrDefault<String>(cfg, "language", ctx, null)
        original = obj.getOrDefault<Boolean>(cfg, "original", ctx, null)

        clientName = obj.getOrDefault<Int>(cfg, "clientName", ctx, 1) ?: 1
        clientVersion = obj.getOrDefault(cfg, "clientVersion", ctx, "2.20250923.08.00") ?: "2.20250923.08.00"
        osName = obj.getOrDefault(cfg, "osName", ctx, "Windows") ?: "Windows"
        osVersion = obj.getOrDefault(cfg, "osVersion", ctx, "10.0") ?: "10.0"

        videoFormats = readFormats(plugin, obj, "videoFormats")
        audioFormats = readFormats(plugin, obj, "audioFormats")
        format = null
    }

    private constructor(other: JSUMPSource, format: SabrFormat)
        : super(TYPE_UMP, other.getUnderlyingPlugin()!!, other.getUnderlyingObject()!!) {
        url = other.url
        poToken = other.poToken
        ustreamerConfig = other.ustreamerConfig
        videoId = other.videoId
        isLive = other.isLive
        name = format.videoLabel
        width = format.width
        height = format.height
        container = format.containerMimeType
        codec = format.codecs
        bitrate = format.bitrate
        duration = other.duration
        priority = other.priority
        language = other.language
        original = other.original
        clientName = other.clientName
        clientVersion = other.clientVersion
        osName = other.osName
        osVersion = other.osVersion
        videoFormats = other.videoFormats
        audioFormats = other.audioFormats
        this.format = format
    }

    fun downloadQualitySources(): List<JSUMPSource> =
        videoFormats.sortedByDescending { it.height.toLong() * 100000 + it.bitrate }
            .map { JSUMPSource(this, it) }

    fun downloadAudioQualitySources(): List<JSUMPAudioSource> =
        audioFormats.sortedWith(compareByDescending<SabrFormat> { it.isOriginalAudio }.thenByDescending { it.bitrate })
            .map { JSUMPAudioSource(this, it) }

    private fun readFormats(plugin: JSClient, obj: V8ValueObject, key: String): List<SabrFormat> {
        val cfg = plugin.config
        val entries = obj.getOrThrowList<V8ValueObject>(cfg, key, ctx)
        return entries.map { entry ->
            SabrFormat(
                itag = entry.getOrThrow<Int>(cfg, "itag", ctx),
                lastModified = (entry.getOrDefault<String>(cfg, "lastModified", ctx, null))?.toLongOrNull() ?: 0L,
                xtags = entry.getOrDefault(cfg, "xtags", ctx, "") ?: "",
                mimeType = entry.getOrThrow(cfg, "mimeType", ctx),
                codecs = entry.getOrDefault(cfg, "codecs", ctx, "") ?: "",
                bitrate = entry.getOrDefault<Int>(cfg, "bitrate", ctx, 0) ?: 0,
                width = entry.getOrDefault<Int>(cfg, "width", ctx, 0) ?: 0,
                height = entry.getOrDefault<Int>(cfg, "height", ctx, 0) ?: 0,
                fps = entry.getOrDefault<Int>(cfg, "fps", ctx, 0) ?: 0,
                audioChannels = entry.getOrDefault<Int>(cfg, "audioChannels", ctx, 0) ?: 0,
                audioSampleRate = entry.getOrDefault<Int>(cfg, "audioSampleRate", ctx, 0) ?: 0,
                language = entry.getOrDefault<String>(cfg, "language", ctx, null),
                isOriginalAudio = entry.getOrDefault(cfg, "original", ctx, false) ?: false,
                isDrc = entry.getOrDefault(cfg, "isDrc", ctx, false) ?: false
            )
        }
    }

    fun toStreamSpec(httpClientFactory: () -> ManagedHttpClient, ownsClient: Boolean = false): SabrStreamSpec {
        val ustreamerBytes = decodeBase64Url(ustreamerConfig)
        return SabrStreamSpec(
            httpClientFactory = httpClientFactory,
            ownsHttpClient = ownsClient,
            serverAbrStreamingUrl = url,
            ustreamerConfig = ustreamerBytes,
            videoId = videoId,
            isLive = isLive,
            durationUs = if (duration > 0) duration * 1_000_000L else -1L,
            videoFormats = videoFormats,
            audioFormats = audioFormats,
            poToken = poToken,
            clientName = clientName,
            clientVersion = clientVersion,
            osName = osName,
            osVersion = osVersion
        )
    }

    private fun decodeBase64Url(value: String): ByteArray =
        Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
