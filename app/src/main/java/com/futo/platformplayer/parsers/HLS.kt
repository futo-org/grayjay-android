package com.futo.platformplayer.parsers

import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.toURIRobust
import com.futo.platformplayer.yesNoToBoolean
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class HLS {
    companion object {
        fun downloadAndParseMasterPlaylist(client: ManagedHttpClient, sourceUrl: String): MasterPlaylist {
            val masterPlaylistResponse = client.get(sourceUrl)
            check(masterPlaylistResponse.isOk) { "Failed to get master playlist: ${masterPlaylistResponse.code}" }

            val masterPlaylistContent = masterPlaylistResponse.body?.string()
                ?: throw Exception("Master playlist content is empty")
            val baseUrl = sourceUrl.toURIRobust()!!.resolve("./").toString()

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
                        mediaRenditions.add(parseMediaRendition(client, line, baseUrl))
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

        fun downloadAndParseVariantPlaylist(client: ManagedHttpClient, sourceUrl: String): VariantPlaylist {
            val response = client.get(sourceUrl)
            check(response.isOk) { "Failed to get variant playlist: ${response.code}" }

            val content = response.body?.string()
                ?: throw Exception("Variant playlist content is empty")

            val lines = content.lines()
            val version = lines.find { it.startsWith("#EXT-X-VERSION:") }?.substringAfter(":")?.toIntOrNull() ?: 3
            val targetDuration = lines.find { it.startsWith("#EXT-X-TARGETDURATION:") }?.substringAfter(":")?.toIntOrNull()
                ?: throw Exception("Target duration not found in variant playlist")
            val mediaSequence = lines.find { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }?.substringAfter(":")?.toLongOrNull() ?: 0
            val discontinuitySequence = lines.find { it.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE:") }?.substringAfter(":")?.toIntOrNull() ?: 0
            val programDateTime = lines.find { it.startsWith("#EXT-X-PROGRAM-DATE-TIME:") }?.substringAfter(":")?.let {
                ZonedDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)
            }

            val segments = mutableListOf<Segment>()
            var currentSegment: Segment? = null
            lines.forEach { line ->
                when {
                    line.startsWith("#EXTINF:") -> {
                        val duration = line.substringAfter(":").substringBefore(",").toDoubleOrNull()
                            ?: throw Exception("Invalid segment duration format")
                        currentSegment = Segment(duration = duration)
                    }
                    line.startsWith("#") -> {
                        // Handle other tags if necessary
                    }
                    else -> {
                        currentSegment?.let {
                            it.uri = line
                            segments.add(it)
                        }
                        currentSegment = null
                    }
                }
            }

            return VariantPlaylist(version, targetDuration, mediaSequence, discontinuitySequence, programDateTime, segments)
        }

        private fun resolveUrl(baseUrl: String, url: String): String {
            return if (url.toURIRobust()!!.isAbsolute) url else baseUrl + url
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
                closedCaptions = attributes["CLOSED-CAPTIONS"]
            )
        }

        private fun parseMediaRendition(client: ManagedHttpClient, line: String, baseUrl: String): MediaRendition {
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
            val attributePairs = content.substringAfter(":").splitToSequence(',')

            var currentPair = StringBuilder()
            for (pair in attributePairs) {
                currentPair.append(pair)
                if (currentPair.count { it == '\"' } % 2 == 0) {  // Check if the number of quotes is even
                    val (key, value) = currentPair.toString().split('=')
                    attributes[key.trim()] = value.trim().removeSurrounding("\"")
                    currentPair = StringBuilder()  // Reset for the next attribute
                } else {
                    currentPair.append(',')  // Continue building the current attribute pair
                }
            }

            return attributes
        }

        private val _quoteList = listOf("GROUP-ID", "NAME", "URI", "CODECS", "AUDIO")
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
        val closedCaptions: String?
    )

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
                "DEFAULT" to isDefault?.toString()?.uppercase(),
                "AUTOSELECT" to isAutoSelect?.toString()?.uppercase(),
                "FORCED" to isForced?.toString()?.uppercase()
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
    }

    data class VariantPlaylistReference(val url: String, val streamInfo: StreamInfo) {
        fun toM3U8Line(): String = buildString {
            append("#EXT-X-STREAM-INF:")
            appendAttributes(this,
                "BANDWIDTH" to streamInfo.bandwidth?.toString(),
                "RESOLUTION" to streamInfo.resolution,
                "CODECS" to streamInfo.codecs,
                "FRAME-RATE" to streamInfo.frameRate,
                "VIDEO-RANGE" to streamInfo.videoRange,
                "AUDIO" to streamInfo.audio,
                "CLOSED-CAPTIONS" to streamInfo.closedCaptions
            )
            append("\n$url\n")
        }
    }

    data class VariantPlaylist(
        val version: Int,
        val targetDuration: Int,
        val mediaSequence: Long,
        val discontinuitySequence: Int,
        val programDateTime: ZonedDateTime?,
        val segments: List<Segment>
    ) {
        fun buildM3U8(): String = buildString {
            append("#EXTM3U\n")
            append("#EXT-X-VERSION:$version\n")
            append("#EXT-X-TARGETDURATION:$targetDuration\n")
            append("#EXT-X-MEDIA-SEQUENCE:$mediaSequence\n")
            append("#EXT-X-DISCONTINUITY-SEQUENCE:$discontinuitySequence\n")
            programDateTime?.let {
                append("#EXT-X-PROGRAM-DATE-TIME:${it.format(DateTimeFormatter.ISO_DATE_TIME)}\n")
            }

            segments.forEach { segment ->
                append("#EXTINF:${segment.duration},\n")
                append(segment.uri + "\n")
            }
        }
    }

    data class Segment(
        val duration: Double,
        var uri: String = ""
    )
}
