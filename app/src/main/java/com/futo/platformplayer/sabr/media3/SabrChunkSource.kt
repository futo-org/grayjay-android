package com.futo.platformplayer.sabr.media3

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.chunk.Chunk
import androidx.media3.exoplayer.source.chunk.ChunkExtractor
import androidx.media3.exoplayer.source.chunk.ChunkHolder
import androidx.media3.exoplayer.source.chunk.ChunkSource
import androidx.media3.exoplayer.source.chunk.ContainerMediaChunk
import androidx.media3.exoplayer.source.chunk.InitializationChunk
import androidx.media3.exoplayer.source.chunk.MediaChunk
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.futo.platformplayer.sabr.SabrBlockedException
import com.futo.platformplayer.sabr.SabrFormat
import com.futo.platformplayer.sabr.SabrFormatKey
import com.futo.platformplayer.sabr.SabrSession
import java.io.IOException

@UnstableApi
internal fun sabrLoadException(error: Throwable?): IOException {
    if (error is SabrBlockedException)
        return HttpDataSource.InvalidResponseCodeException(
            403, "Forbidden", error, emptyMap(),
            DataSpec.Builder().setUri(Uri.parse("sabr://blocked")).build(), ByteArray(0))
    return error as? IOException ?: IOException(error)
}

@UnstableApi
class SabrChunkSource(
    private val session: SabrSession,
    private val role: Int,
    private val formats: List<SabrFormat>,
    private val trackSelection: ExoTrackSelection,
    private val dataSource: DataSource,
    private val chunkExtractorFactory: ChunkExtractor.Factory,
    private val playerId: PlayerId
) : ChunkSource {

    private class FormatState(val format: SabrFormat, val extractor: ChunkExtractor)

    private var lastFormatKey: SabrFormatKey? = null

    private val states = HashMap<SabrFormatKey, FormatState>()
    private val emptyIterators = Array<MediaChunkIterator>(trackSelection.length()) { MediaChunkIterator.EMPTY }

    private val trackType = if (role == SabrSession.ROLE_VIDEO) C.TRACK_TYPE_VIDEO else C.TRACK_TYPE_AUDIO

    override fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: SeekParameters): Long = positionUs

    override fun maybeThrowError() {
        session.fatalError?.let { throw sabrLoadException(it) }
    }

    override fun getPreferredQueueSize(playbackPositionUs: Long, queue: List<MediaChunk>): Int = queue.size

    override fun shouldCancelLoad(playbackPositionUs: Long, loadingChunk: Chunk, queue: List<MediaChunk>): Boolean = false

    override fun getNextChunk(
        loadingInfo: LoadingInfo,
        loadPositionUs: Long,
        queue: List<MediaChunk>,
        out: ChunkHolder
    ) {
        if (session.fatalError != null) return

        val base = session.mediaBaseUs
        val playbackPositionUs = loadingInfo.playbackPositionUs
        val lastChunk = queue.lastOrNull()

        val acceptable = (0 until trackSelection.length())
            .map { formats[trackSelection.getIndexInTrackGroup(it)] }

        session.setPlaybackPosition(playbackPositionUs + base)
        session.setDemand(role, acceptable, loadPositionUs + base, owner = this)

        val format = session.activeFormat(role) ?: acceptable.first()
        val indexInGroup = formats.indexOfFirst { it.key == format.key }
        val trackFormat =
            if (indexInGroup >= 0) trackSelection.trackGroup.getFormat(indexInGroup)
            else trackSelection.selectedFormat

        val buffer = session.bufferFor(format)
        val state = states.getOrPut(format.key) {
            FormatState(format, createExtractor(format))
        }

        if (state.extractor.sampleFormats == null && session.hasSeparateInit(format)) {
            val init = buffer.initSegment
            if (init == null || !init.isComplete) {
                session.wakePump()
                return
            }
            out.chunk = InitializationChunk(
                dataSource,
                buildDataSpec(SabrSegmentRef(format.key, 0, true), init.size.toLong()),
                trackFormat,
                trackSelection.selectionReason,
                trackSelection.selectionData,
                state.extractor
            )
            return
        }

        val sameFormat = lastFormatKey == format.key
        var segment = if (lastChunk == null || !sameFormat)
            buffer.firstCovering(loadPositionUs + base)
        else
            buffer.get(lastChunk.chunkIndex.toInt() + 1)

        if (segment == null) {
            if (session.isComplete(format)) {
                out.endOfStream = true
                return
            }

            val wanted = loadPositionUs + base
            val front = buffer.firstAtOrAfter(-1)

            if (front != null && front.startUs > wanted) {
                if (session.isLive) segment = front
                else session.restart(wanted, force = true)
            } else {
                session.wakePump()
            }

            if (segment == null) return
        }

        lastFormatKey = format.key
        out.chunk = ContainerMediaChunk(
            dataSource,
            buildDataSpec(SabrSegmentRef(format.key, segment.sequenceNumber, false), C.LENGTH_UNSET.toLong()),
            trackFormat,
            trackSelection.selectionReason,
            trackSelection.selectionData,
            segment.startUs - base,
            segment.endUs - base,
            C.TIME_UNSET,
            C.TIME_UNSET,
            segment.sequenceNumber.toLong(),
            1,
            -base,
            state.extractor
        )
    }

    override fun onChunkLoadCompleted(chunk: Chunk) {}

    override fun onChunkLoadError(
        chunk: Chunk,
        cancelable: Boolean,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo,
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy
    ): Boolean = false

    override fun release() {
        for (state in states.values) state.extractor.release()
        states.clear()

        session.clearDemand(role, owner = this)
    }

    private fun buildDataSpec(ref: SabrSegmentRef, length: Long): DataSpec =
        DataSpec.Builder()
            .setUri(ref.toUri())
            .setPosition(0)
            .setLength(length)
            .setCustomData(ref)
            .build()

    private fun createExtractor(format: SabrFormat): ChunkExtractor =
        chunkExtractorFactory.createProgressiveMediaExtractor(
            trackType,
            SabrFormats.toMedia3Format(format),
            false,
            emptyList(),
            null,
            playerId
        ) ?: throw IllegalStateException("No extractor for ${format.mimeType}")
}
