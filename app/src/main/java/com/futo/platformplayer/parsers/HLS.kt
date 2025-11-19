package com.futo.platformplayer.parsers

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistParserFactory
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import com.futo.platformplayer.api.media.models.streams.sources.HLSVariantAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.HLSVariantSubtitleUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.HLSVariantVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.toYesNo
import com.futo.platformplayer.yesNoToBoolean
import java.io.ByteArrayInputStream
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.text.ifEmpty

class HLS {
    companion object {
        @OptIn(UnstableApi::class)
        fun parseMasterPlaylist(masterPlaylistContent: String, sourceUrl: String): MasterPlaylist {
            val baseUrl = URI(sourceUrl).resolve("./").toString()

            val variantPlaylists = mutableListOf<VariantPlaylistReference>()
            val mediaRenditions = mutableListOf<MediaRendition>()
            val sessionDataList = mutableListOf<SessionData>()
            var independentSegments = false
            var version: Int? = null
            var mediaSequence: Long? = null
            val unhandled = mutableListOf<String>()

            val lines = masterPlaylistContent.lines()
            lines.forEachIndexed { index, line ->
                when {
                    line.startsWith("#EXT-X-VERSION:") -> {
                        version = line.substringAfter(":").toIntOrNull()
                    }

                    line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                        mediaSequence = line.substringAfter(":").toLongOrNull()
                    }

                    line.startsWith("#EXT-X-STREAM-INF") -> {
                        val nextLine = lines.getOrNull(index + 1)
                            ?: throw Exception("Expected URI following #EXT-X-STREAM-INF, found none")
                        val url = resolveUrl(baseUrl, nextLine)
                        variantPlaylists.add(VariantPlaylistReference(url, parseStreamInfo(line)))
                    }

                    line.startsWith("#EXT-X-MEDIA") -> {
                        mediaRenditions.add(parseMediaRendition(line, baseUrl))
                    }

                    line == "#EXT-X-INDEPENDENT-SEGMENTS" -> {
                        independentSegments = true
                    }

                    line.startsWith("#EXT-X-SESSION-DATA") -> {
                        val sessionData = parseSessionData(line)
                        sessionDataList.add(sessionData)
                    }

                    else -> {
                        unhandled.add(line)
                    }
                }
            }

            return MasterPlaylist(variantPlaylists, mediaRenditions, sessionDataList, independentSegments, version = version, mediaSequence = mediaSequence, unhandled = unhandled)
        }

        fun mediaRenditionToVariant(rendition: MediaRendition): HLSVariantAudioUrlSource? {
            if (rendition.uri == null) {
                return null
            }

            val suffix = listOf(rendition.language, rendition.groupID).mapNotNull { x -> x?.ifEmpty { null } }.joinToString(", ")
            return when (rendition.type) {
                "AUDIO" -> HLSVariantAudioUrlSource(rendition.name?.ifEmpty { "Audio (${suffix})" } ?: "Audio (${suffix})", 0, "application/vnd.apple.mpegurl", "", rendition.language ?: "", null, false, false, rendition.uri)
                else -> null
            }
        }

        fun variantReferenceToVariant(reference: VariantPlaylistReference): HLSVariantVideoUrlSource {
            var width: Int? = null
            var height: Int? = null
            val resolutionTokens = reference.streamInfo.resolution?.split('x')
            if (resolutionTokens?.isNotEmpty() == true) {
                width = resolutionTokens[0].toIntOrNull()
                height = resolutionTokens[1].toIntOrNull()
            }

            val suffix = listOf(reference.streamInfo.video, reference.streamInfo.codecs).mapNotNull { x -> x?.ifEmpty { null } }.joinToString(", ")
            return HLSVariantVideoUrlSource(suffix, width ?: 0, height ?: 0, "application/vnd.apple.mpegurl", reference.streamInfo.codecs ?: "", reference.streamInfo.bandwidth, 0, false, reference.url)
        }

        private fun parseByteRange(value: String): Pair<Long, Long> {
            val trimmed = value.trim()
            require(trimmed.isNotEmpty()) { "Empty BYTERANGE value" }

            val parts = trimmed.split('@')
            val length = parts[0].toLong()
            require(length >= 0) { "Invalid BYTERANGE length '$value'" }

            val start = if (parts.size > 1) {
                val s = parts[1].toLong()
                require(s >= 0) { "Invalid BYTERANGE offset '$value'" }
                s
            } else {
                -1L
            }

            return length to start
        }


        private fun parseAttributes(content: String): Map<String, String> {
            val index = content.indexOf(':')
            if (index < 0 || index == content.length - 1) return emptyMap()

            val attributes = mutableMapOf<String, String>()
            val maybeAttributePairs = content.substring(index + 1).splitToSequence(',')

            var currentPair = StringBuilder()
            for (pair in maybeAttributePairs) {
                currentPair.append(pair)
                if (currentPair.count { it == '\"' } % 2 == 0) {
                    val full = currentPair.toString()
                    val key = full.substringBefore("=")
                    val value = full.substringAfter("=")
                    attributes[key.trim()] = value.trim().removeSurrounding("\"")
                    currentPair = StringBuilder()
                } else {
                    currentPair.append(',')
                }
            }

            return attributes
        }

        fun parseVariantPlaylist(content: String, sourceUrl: String): VariantPlaylist {
            val baseUrl = URI(sourceUrl).resolve("./").toString()
            val lines = content.lines()

            var version: Int? = null
            var targetDuration: Int? = null
            var mediaSequence: Long? = null
            var discontinuitySequence: Int? = null
            var programDateTime: ZonedDateTime? = null
            var playlistType: String? = null
            var streamInfo: StreamInfo? = null
            var decryptionInfo: DecryptionInfo? = null
            var mapUrl: String? = null
            var mapBytesStart: Long = -1
            var mapBytesLength: Long = -1
            val segments = mutableListOf<Segment>()
            val unhandled = mutableListOf<String>()

            var currentSegment: MediaSegment? = null

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                when {
                    line.startsWith("#EXT-X-VERSION:") -> {
                        version = line.substringAfter(":").toIntOrNull()
                    }

                    line.startsWith("#EXT-X-TARGETDURATION:") -> {
                        targetDuration = line.substringAfter(":").toIntOrNull()
                    }

                    line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                        mediaSequence = line.substringAfter(":").toLongOrNull()
                    }

                    line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE:") -> {
                        discontinuitySequence = line.substringAfter(":").toIntOrNull()
                    }

                    line.startsWith("#EXT-X-PROGRAM-DATE-TIME:") -> {
                        programDateTime = ZonedDateTime.parse(
                            line.substringAfter(":"),
                            DateTimeFormatter.ISO_DATE_TIME
                        )
                    }

                    line.startsWith("#EXT-X-PLAYLIST-TYPE:") -> {
                        playlistType = line.substringAfter(":")
                    }

                    line.startsWith("#EXT-X-STREAM-INF:") -> {
                        streamInfo = parseStreamInfo(line)
                    }

                    line.startsWith("#EXT-X-KEY:") -> {
                        val attrs = parseAttributes(line)
                        val method = attrs["METHOD"]?.ifEmpty { "AES-128" } ?: "AES-128"
                        val keyUri = attrs["URI"]?.removeSurrounding("\"")
                        val keyUrl = keyUri?.let { resolveUrl(baseUrl, it) }
                        val ivRaw = attrs["IV"]
                        val iv = ivRaw
                            ?.removePrefix("0x")
                            ?.removePrefix("0X")
                        val keyFormat = attrs["KEYFORMAT"]
                        val keyFormatVersions = attrs["KEYFORMATVERSIONS"]
                        decryptionInfo = DecryptionInfo(method, keyUrl, iv, keyFormat, keyFormatVersions)
                    }

                    line.startsWith("#EXT-X-MAP:") -> {
                        val attrs = parseAttributes(line)
                        attrs["URI"]?.let { uri ->
                            mapUrl = resolveUrl(baseUrl, uri)
                        }
                        attrs["BYTERANGE"]?.let { br ->
                            val (len, start) = parseByteRange(br)
                            mapBytesLength = len
                            mapBytesStart = start
                        }
                    }

                    line.startsWith("#EXTINF:") -> {
                        val durationText = line.substringAfter(":").substringBefore(",")
                        val duration = durationText.toDoubleOrNull()
                            ?: throw IllegalArgumentException("Invalid segment duration: '$line'")
                        currentSegment = MediaSegment(duration = duration)
                    }

                    line == "#EXT-X-DISCONTINUITY" -> {
                        segments.add(DiscontinuitySegment())
                    }

                    line == "#EXT-X-ENDLIST" -> {
                        segments.add(EndListSegment())
                    }

                    currentSegment != null && line.startsWith("#EXT-X-BYTERANGE:") -> {
                        val br = line.substringAfter(":").trim()
                        val (len, start) = parseByteRange(br)
                        currentSegment!!.bytesLength = len
                        currentSegment!!.bytesStart = start
                    }

                    currentSegment != null && line.startsWith("#") -> {
                        currentSegment!!.unhandled.add(line)
                    }

                    !line.startsWith("#") -> {
                        currentSegment?.let {
                            it.uri = resolveUrl(baseUrl, line)
                            segments.add(it)
                            currentSegment = null
                        } ?: run {
                            unhandled.add(line)
                        }
                    }

                    else -> {
                        unhandled.add(line)
                    }
                }
            }

            return VariantPlaylist(
                version = version,
                targetDuration = targetDuration,
                mediaSequence = mediaSequence,
                discontinuitySequence = discontinuitySequence,
                programDateTime = programDateTime,
                playlistType = playlistType,
                streamInfo = streamInfo,
                segments = segments,
                decryptionInfo = decryptionInfo,
                mapUrl = mapUrl,
                mapBytesStart = mapBytesStart,
                mapBytesLength = mapBytesLength,
                unhandled = unhandled
            )
        }

        fun parseAndGetVideoSources(source: Any, content: String, url: String): List<HLSVariantVideoUrlSource> {
            val masterPlaylist: MasterPlaylist
            try {
                masterPlaylist = parseMasterPlaylist(content, url)
                return masterPlaylist.getVideoSources()
            } catch (e: Throwable) {
                if (content.lines().any { it.startsWith("#EXTINF:") }) {
                    return if (source is IHLSManifestSource) {
                        listOf(HLSVariantVideoUrlSource("variant", 0, 0, "application/vnd.apple.mpegurl", "", null, 0, false, url))
                    } else if (source is IHLSManifestAudioSource) {
                        listOf()
                    } else {
                        throw NotImplementedError()
                    }
                } else {
                    throw e
                }
            }
        }

        fun parseAndGetAudioSources(source: Any, content: String, url: String): List<HLSVariantAudioUrlSource> {
            val masterPlaylist: MasterPlaylist
            try {
                masterPlaylist = parseMasterPlaylist(content, url)
                return masterPlaylist.getAudioSources()
            } catch (e: Throwable) {
                if (content.lines().any { it.startsWith("#EXTINF:") }) {
                    return if (source is IHLSManifestSource) {
                        listOf()
                    } else if (source is IHLSManifestAudioSource) {
                        listOf(HLSVariantAudioUrlSource("variant", 0, "application/vnd.apple.mpegurl", "", "", null, false, false, url))
                    } else {
                        throw NotImplementedError()
                    }
                } else {
                    throw e
                }
            }
        }

        //TODO: getSubtitleSources

        private fun resolveUrl(baseUrl: String, url: String): String {
            val baseUri = URI(baseUrl)
            val urlUri = URI(url)

            return if (urlUri.isAbsolute) {
                url
            } else {
                val resolvedUri = baseUri.resolve(urlUri)
                resolvedUri.toString()
            }
        }

        private fun parseStreamInfo(content: String): StreamInfo {
            val attributes = parseAttributes(content)
            return StreamInfo(
                bandwidth = attributes["BANDWIDTH"]?.toIntOrNull(),
                resolution = attributes["RESOLUTION"],
                codecs = attributes["CODECS"],
                frameRate = attributes["FRAME-RATE"],
                videoRange = attributes["VIDEO-RANGE"],
                audio = attributes["AUDIO"],
                video = attributes["VIDEO"],
                subtitles = attributes["SUBTITLES"],
                closedCaptions = attributes["CLOSED-CAPTIONS"]
            )
        }

        private fun parseMediaRendition(line: String, baseUrl: String): MediaRendition {
            val attributes = parseAttributes(line)
            val uri = attributes["URI"]?.let { resolveUrl(baseUrl, it) }
            return MediaRendition(
                type = attributes["TYPE"],
                uri = uri,
                groupID = attributes["GROUP-ID"],
                language = attributes["LANGUAGE"],
                name = attributes["NAME"],
                isDefault = attributes["DEFAULT"]?.yesNoToBoolean(),
                isAutoSelect = attributes["AUTOSELECT"]?.yesNoToBoolean(),
                isForced = attributes["FORCED"]?.yesNoToBoolean()
            )
        }

        private fun parseSessionData(line: String): SessionData {
            val attributes = parseAttributes(line)
            val dataId = attributes["DATA-ID"]!!
            val value = attributes["VALUE"]!!
            return SessionData(dataId, value)
        }

        private val _quoteList = listOf("GROUP-ID", "NAME", "URI", "CODECS", "AUDIO", "VIDEO")
        private fun shouldQuote(key: String, value: String?): Boolean {
            if (value == null)
                return false;

            if (value.contains(','))
                return true;

            return _quoteList.contains(key)
        }
        private fun appendAttributes(stringBuilder: StringBuilder, vararg attributes: Pair<String, String?>) {
            attributes.filter { it.second != null }
                .joinToString(",") {
                    val value = it.second
                    "${it.first}=${if (shouldQuote(it.first, it.second)) "\"$value\"" else value}"
                }
                .let { if (it.isNotEmpty()) stringBuilder.append(it) }
        }
    }

    data class SessionData(
        val dataId: String,
        val value: String
    ) {
        fun toM3U8Line(): String = buildString {
            append("#EXT-X-SESSION-DATA:")
            appendAttributes(this,
                "DATA-ID" to dataId,
                "VALUE" to value
            )
            append("\n")
        }
    }

    data class StreamInfo(
        val bandwidth: Int?,
        val resolution: String?,
        val codecs: String?,
        val frameRate: String?,
        val videoRange: String?,
        val audio: String?,
        val video: String?,
        val subtitles: String?,
        val closedCaptions: String?
    ) {
        fun toM3U8Line(): String = buildString {
            append("#EXT-X-STREAM-INF:")
            appendAttributes(this,
                "BANDWIDTH" to bandwidth?.toString(),
                "RESOLUTION" to resolution,
                "CODECS" to codecs,
                "FRAME-RATE" to frameRate,
                "VIDEO-RANGE" to videoRange,
                "AUDIO" to audio,
                "VIDEO" to video,
                "SUBTITLES" to subtitles,
                "CLOSED-CAPTIONS" to closedCaptions
            )
            append("\n")
        }
    }

    data class MediaRendition(
        val type: String?,
        val uri: String?,
        val groupID: String?,
        val language: String?,
        val name: String?,
        val isDefault: Boolean?,
        val isAutoSelect: Boolean?,
        val isForced: Boolean?,
    ) {
        fun toM3U8Line(): String = buildString {
            append("#EXT-X-MEDIA:")
            appendAttributes(this,
                "TYPE" to type,
                "URI" to uri,
                "GROUP-ID" to groupID,
                "LANGUAGE" to language,
                "NAME" to name,
                "DEFAULT" to isDefault.toYesNo(),
                "AUTOSELECT" to isAutoSelect.toYesNo(),
                "FORCED" to isForced.toYesNo()
            )
            append("\n")
        }
    }


    data class MasterPlaylist(
        val variantPlaylistsRefs: List<VariantPlaylistReference>,
        val mediaRenditions: List<MediaRendition>,
        val sessionDataList: List<SessionData>,
        val independentSegments: Boolean,
        val version: Int? = null,
        val mediaSequence: Long? = null,
        val unhandled: List<String> = emptyList()
    ) {
        fun buildM3U8(): String {
            val builder = StringBuilder()
            builder.append("#EXTM3U\n")

            version?.let {
                builder.append("#EXT-X-VERSION:$it\n")
            }
            mediaSequence?.let {
                builder.append("#EXT-X-MEDIA-SEQUENCE:$it\n")
            }

            if (independentSegments) {
                builder.append("#EXT-X-INDEPENDENT-SEGMENTS\n")
            }

            mediaRenditions.forEach { rendition ->
                builder.append(rendition.toM3U8Line())
            }

            variantPlaylistsRefs.forEach { variant ->
                builder.append(variant.toM3U8Line())
            }

            sessionDataList.forEach { data ->
                builder.append(data.toM3U8Line())
            }

            return builder.toString()
        }

        fun getVideoSources(): List<HLSVariantVideoUrlSource> {
            return variantPlaylistsRefs.map {
                variantReferenceToVariant(it)
            }
        }

        fun getAudioSources(): List<HLSVariantAudioUrlSource> {
            return mediaRenditions.mapNotNull {
                return@mapNotNull mediaRenditionToVariant(it)
            }
        }

        fun getSubtitleSources(): List<HLSVariantSubtitleUrlSource> {
            return mediaRenditions.mapNotNull {
                if (it.uri == null) {
                    return@mapNotNull null
                }

                val suffix = listOf(it.language, it.groupID).mapNotNull { x -> x?.ifEmpty { null } }.joinToString(", ")
                return@mapNotNull when (it.type) {
                    "SUBTITLE" -> HLSVariantSubtitleUrlSource(it.name?.ifEmpty { "Subtitle (${suffix})" } ?: "Subtitle (${suffix})", it.uri, "application/vnd.apple.mpegurl")
                    else -> null
                }
            }
        }
    }

    data class VariantPlaylistReference(val url: String, val streamInfo: StreamInfo) {
        fun toM3U8Line(): String = buildString {
            append(streamInfo.toM3U8Line())
            append("$url\n")
        }
    }

    data class DecryptionInfo(
        val method: String,
        val keyUrl: String?,
        val iv: String?,
        val keyFormat: String?,
        val keyFormatVersions: String?
    ) {
        val isEncrypted: Boolean
            get() = !method.equals("NONE", ignoreCase = true)
    }

    data class VariantPlaylist(
        val version: Int?,
        val targetDuration: Int?,
        val mediaSequence: Long?,
        val discontinuitySequence: Int?,
        val programDateTime: ZonedDateTime?,
        val playlistType: String?,
        val streamInfo: StreamInfo?,
        val segments: List<Segment>,
        val decryptionInfo: DecryptionInfo? = null,
        val mapUrl: String? = null,
        val mapBytesStart: Long = -1,
        val mapBytesLength: Long = -1,
        val unhandled: List<String> = emptyList()
    ) {
        fun buildM3U8(): String = buildString {
            append("#EXTM3U\n")
            version?.let { append("#EXT-X-VERSION:$it\n") }
            targetDuration?.let { append("#EXT-X-TARGETDURATION:$it\n") }
            mediaSequence?.let { append("#EXT-X-MEDIA-SEQUENCE:$it\n") }
            discontinuitySequence?.let { append("#EXT-X-DISCONTINUITY-SEQUENCE:$it\n") }
            playlistType?.let { append("#EXT-X-PLAYLIST-TYPE:$it\n") }
            programDateTime?.let {
                append(
                    "#EXT-X-PROGRAM-DATE-TIME:${
                        it.withZoneSameInstant(java.time.ZoneOffset.UTC)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
                    }\n"
                )
            }
            streamInfo?.let { append(it.toM3U8Line()) }

            decryptionInfo?.let { dec ->
                val sb = StringBuilder()
                sb.append("#EXT-X-KEY:METHOD=").append(dec.method)
                if (!dec.method.equals("NONE", ignoreCase = true)) {
                    dec.keyUrl?.let { url ->
                        sb.append(",URI=\"").append(url).append("\"")
                    }
                    dec.iv?.let { iv ->
                        sb.append(",IV=0x").append(iv)
                    }
                    dec.keyFormat?.let { kf ->
                        sb.append(",KEYFORMAT=\"").append(kf).append("\"")
                    }
                    dec.keyFormatVersions?.let { kfv ->
                        sb.append(",KEYFORMATVERSIONS=\"").append(kfv).append("\"")
                    }
                }
                append(sb.append("\n").toString())
            }

            if (!mapUrl.isNullOrEmpty()) {
                val sb = StringBuilder()
                sb.append("#EXT-X-MAP:URI=\"").append(mapUrl).append("\"")
                if (mapBytesLength > 0) {
                    if (mapBytesStart >= 0) {
                        sb.append(",BYTERANGE=\"").append(mapBytesLength)
                            .append("@").append(mapBytesStart).append("\"")
                    } else {
                        sb.append(",BYTERANGE=\"").append(mapBytesLength).append("\"")
                    }
                }
                append(sb.append("\n").toString())
            }

            segments.forEach { segment ->
                append(segment.toM3U8Line())
            }
        }
    }

    abstract class Segment {
        abstract fun toM3U8Line(): String
    }

    data class MediaSegment(
        val duration: Double,
        var uri: String = "",
        var bytesStart: Long = -1,
        var bytesLength: Long = -1,
        val unhandled: MutableList<String> = mutableListOf()
    ) : Segment() {
        override fun toM3U8Line(): String = buildString {
            append("#EXTINF:${duration},\n")

            if (bytesLength > 0) {
                if (bytesStart >= 0) {
                    append("#EXT-X-BYTERANGE:${bytesLength}@${bytesStart}\n")
                } else {
                    append("#EXT-X-BYTERANGE:${bytesLength}\n")
                }
            }

            append(uri).append("\n")
        }
    }

    class DiscontinuitySegment : Segment() {
        override fun toM3U8Line(): String = buildString {
            append("#EXT-X-DISCONTINUITY\n")
        }
    }

    class EndListSegment : Segment() {
        override fun toM3U8Line(): String = buildString {
            append("#EXT-X-ENDLIST\n")
        }
    }
}
