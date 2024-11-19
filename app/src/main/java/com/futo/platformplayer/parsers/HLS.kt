package com.futo.platformplayer.parsers

import com.futo.platformplayer.api.media.models.streams.sources.HLSVariantAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.HLSVariantSubtitleUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.HLSVariantVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.toYesNo
import com.futo.platformplayer.yesNoToBoolean
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class HLS {
    companion object {
        fun parseMasterPlaylist(masterPlaylistContent: String, sourceUrl: String): MasterPlaylist {
            val baseUrl = URI(sourceUrl).resolve("./").toString()

            val variantPlaylists = mutableListOf<VariantPlaylistReference>()
            val mediaRenditions = mutableListOf<MediaRendition>()
            val sessionDataList = mutableListOf<SessionData>()
            var independentSegments = false

            masterPlaylistContent.lines().forEachIndexed { index, line ->
                when {
                    line.startsWith("#EXT-X-STREAM-INF") -> {
                        val nextLine = masterPlaylistContent.lines().getOrNull(index + 1)
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
                }
            }

            return MasterPlaylist(variantPlaylists, mediaRenditions, sessionDataList, independentSegments)
        }

        fun parseVariantPlaylist(content: String, sourceUrl: String): VariantPlaylist {
            val lines = content.lines()
            val version = lines.find { it.startsWith("#EXT-X-VERSION:") }?.substringAfter(":")?.toIntOrNull()
            val targetDuration = lines.find { it.startsWith("#EXT-X-TARGETDURATION:") }?.substringAfter(":")?.toIntOrNull()
            val mediaSequence = lines.find { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }?.substringAfter(":")?.toLongOrNull()
            val discontinuitySequence = lines.find { it.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE:") }?.substringAfter(":")?.toIntOrNull()
            val programDateTime = lines.find { it.startsWith("#EXT-X-PROGRAM-DATE-TIME:") }?.substringAfter(":")?.let {
                ZonedDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)
            }
            val playlistType = lines.find { it.startsWith("#EXT-X-PLAYLIST-TYPE:") }?.substringAfter(":")
            val streamInfo = lines.find { it.startsWith("#EXT-X-STREAM-INF:") }?.let { parseStreamInfo(it) }

            val segments = mutableListOf<Segment>()
            var currentSegment: MediaSegment? = null
            lines.forEach { line ->
                when {
                    line.startsWith("#EXTINF:") -> {
                        val duration = line.substringAfter(":").substringBefore(",").toDoubleOrNull()
                            ?: throw Exception("Invalid segment duration format")
                        currentSegment = MediaSegment(duration = duration)
                    }
                    line == "#EXT-X-DISCONTINUITY" -> {
                        segments.add(DiscontinuitySegment())
                    }
                    line =="#EXT-X-ENDLIST" -> {
                        segments.add(EndListSegment())
                    }
                    else -> {
                        currentSegment?.let {
                            it.uri = resolveUrl(sourceUrl, line)
                            segments.add(it)
                        }
                        currentSegment = null
                    }
                }
            }

            return VariantPlaylist(version, targetDuration, mediaSequence, discontinuitySequence, programDateTime, playlistType, streamInfo, segments)
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
                        listOf(HLSVariantAudioUrlSource("variant", 0, "application/vnd.apple.mpegurl", "", "", null, false, url))
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

        private fun parseAttributes(content: String): Map<String, String> {
            val attributes = mutableMapOf<String, String>()
            val maybeAttributePairs = content.substringAfter(":").splitToSequence(',')

            var currentPair = StringBuilder()
            for (pair in maybeAttributePairs) {
                currentPair.append(pair)
                if (currentPair.count { it == '\"' } % 2 == 0) {  // Check if the number of quotes is even
                    val key = currentPair.toString().substringBefore("=")
                    val value = currentPair.toString().substringAfter("=")
                    attributes[key.trim()] = value.trim().removeSurrounding("\"")
                    currentPair = StringBuilder()  // Reset for the next attribute
                } else {
                    currentPair.append(',')  // Continue building the current attribute pair
                }
            }

            return attributes
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
        val isForced: Boolean?
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
        val independentSegments: Boolean
    ) {
        fun buildM3U8(): String {
            val builder = StringBuilder()
            builder.append("#EXTM3U\n")
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
                var width: Int? = null
                var height: Int? = null
                val resolutionTokens = it.streamInfo.resolution?.split('x')
                if (resolutionTokens?.isNotEmpty() == true) {
                    width = resolutionTokens[0].toIntOrNull()
                    height = resolutionTokens[1].toIntOrNull()
                }

                val suffix = listOf(it.streamInfo.video, it.streamInfo.codecs).mapNotNull { x -> x?.ifEmpty { null } }.joinToString(", ")
                HLSVariantVideoUrlSource(suffix, width ?: 0, height ?: 0, "application/vnd.apple.mpegurl", it.streamInfo.codecs ?: "", it.streamInfo.bandwidth, 0, false, it.url)
            }
        }

        fun getAudioSources(): List<HLSVariantAudioUrlSource> {
            return mediaRenditions.mapNotNull {
                if (it.uri == null) {
                    return@mapNotNull null
                }

                val suffix = listOf(it.language, it.groupID).mapNotNull { x -> x?.ifEmpty { null } }.joinToString(", ")
                return@mapNotNull when (it.type) {
                    "AUDIO" -> HLSVariantAudioUrlSource(it.name?.ifEmpty { "Audio (${suffix})" } ?: "Audio (${suffix})", 0, "application/vnd.apple.mpegurl", "", it.language ?: "", null, false, it.uri)
                    else -> null
                }
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

    data class VariantPlaylist(
        val version: Int?,
        val targetDuration: Int?,
        val mediaSequence: Long?,
        val discontinuitySequence: Int?,
        val programDateTime: ZonedDateTime?,
        val playlistType: String?,
        val streamInfo: StreamInfo?,
        val segments: List<Segment>
    ) {
        fun buildM3U8(): String = buildString {
            append("#EXTM3U\n")
            version?.let { append("#EXT-X-VERSION:$it\n") }
            targetDuration?.let { append("#EXT-X-TARGETDURATION:$it\n") }
            mediaSequence?.let { append("#EXT-X-MEDIA-SEQUENCE:$it\n") }
            discontinuitySequence?.let { append("#EXT-X-DISCONTINUITY-SEQUENCE:$it\n") }
            playlistType?.let { append("#EXT-X-PLAYLIST-TYPE:$it\n") }
            programDateTime?.let { append("#EXT-X-PROGRAM-DATE-TIME:${it.format(DateTimeFormatter.ISO_DATE_TIME)}\n") }
            streamInfo?.let { append(it.toM3U8Line()) }

            segments.forEach { segment ->
                append(segment.toM3U8Line())
            }
        }
    }

    abstract class Segment {
        abstract fun toM3U8Line(): String
    }

    data class MediaSegment (
        val duration: Double,
        var uri: String = ""
    ) : Segment() {
        override fun toM3U8Line(): String = buildString {
            append("#EXTINF:${duration},\n")
            append(uri + "\n")
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
