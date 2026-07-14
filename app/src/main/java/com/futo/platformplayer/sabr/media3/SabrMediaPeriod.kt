package com.futo.platformplayer.sabr.media3

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.drm.DrmSessionEventListener
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.source.CompositeSequenceableLoaderFactory
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.source.SequenceableLoader
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.source.chunk.BundledChunkExtractor
import androidx.media3.exoplayer.source.chunk.ChunkSampleStream
import androidx.media3.exoplayer.source.chunk.ChunkExtractor
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.futo.platformplayer.sabr.SabrFormat
import com.futo.platformplayer.sabr.SabrSession

@UnstableApi
class SabrMediaPeriod(
    private val session: SabrSession,
    private val videoFormats: List<SabrFormat>,
    private val audioFormats: List<SabrFormat>,
    private val transferListener: TransferListener?,
    private val allocator: Allocator,
    private val compositeSequenceableLoaderFactory: CompositeSequenceableLoaderFactory,
    private val drmSessionManager: DrmSessionManager,
    private val drmEventDispatcher: DrmSessionEventListener.EventDispatcher,
    private val loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    private val mediaSourceEventDispatcher: androidx.media3.exoplayer.source.MediaSourceEventListener.EventDispatcher,
    private val playerId: PlayerId
) : MediaPeriod, SequenceableLoader.Callback<ChunkSampleStream<SabrChunkSource>> {

    private class Group(val role: Int, val formats: List<SabrFormat>, val trackGroup: TrackGroup)

    private val chunkExtractorFactory: ChunkExtractor.Factory = BundledChunkExtractor.Factory()

    private val groups: List<Group> = buildGroups()
    private val trackGroups: TrackGroupArray = TrackGroupArray(*groups.map { it.trackGroup }.toTypedArray())

    private var callback: MediaPeriod.Callback? = null
    private var sampleStreams: Array<ChunkSampleStream<SabrChunkSource>> = emptyArray()
    private var compositeLoader: SequenceableLoader = compositeSequenceableLoaderFactory.empty()

    private fun buildGroups(): List<Group> {
        val result = ArrayList<Group>()

        if (videoFormats.isNotEmpty()) {
            val sorted = videoFormats.sortedByDescending { it.height * 100000L + it.bitrate }
            val formats = sorted.map { withDrm(SabrFormats.toMedia3Format(it)) }
            result.add(Group(SabrSession.ROLE_VIDEO, sorted, TrackGroup("sabr-video", *formats.toTypedArray())))
        }

        audioFormats
            .groupBy { Triple(it.language ?: "", it.isDrc, it.isOriginalAudio) }
            .entries
            .sortedBy { "${it.key.first}-${it.key.second}-${it.key.third}" }
            .forEachIndexed { index, entry ->
                val sorted = entry.value.sortedByDescending { it.bitrate }
                val formats = sorted.map { withDrm(SabrFormats.toMedia3Format(it)) }
                result.add(Group(SabrSession.ROLE_AUDIO, sorted, TrackGroup("sabr-audio-$index", *formats.toTypedArray())))
            }

        return result
    }

    private fun withDrm(format: Format): Format =
        format.buildUpon().setCryptoType(drmSessionManager.getCryptoType(format)).build()

    override fun prepare(callback: MediaPeriod.Callback, positionUs: Long) {
        this.callback = callback
        val handler = android.os.Handler(android.os.Looper.myLooper() ?: android.os.Looper.getMainLooper())
        session.setSegmentsChangedListener(Runnable {
            handler.post { this.callback?.onContinueLoadingRequested(this) }
        }, owner = this)
        session.start()
        callback.onPrepared(this)
    }

    override fun maybeThrowPrepareError() {
        session.fatalError?.let { throw java.io.IOException(it) }
    }

    override fun getTrackGroups(): TrackGroupArray = trackGroups

    override fun selectTracks(
        selections: Array<out ExoTrackSelection?>,
        mayRetainStreamFlags: BooleanArray,
        streams: Array<SampleStream?>,
        streamResetFlags: BooleanArray,
        positionUs: Long
    ): Long {
        val streamList = ArrayList<ChunkSampleStream<SabrChunkSource>>()

        for (i in selections.indices) {
            @Suppress("UNCHECKED_CAST")
            val existing = streams[i] as? ChunkSampleStream<SabrChunkSource>
            if (existing != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
                existing.release()
                streams[i] = null
            }
            if (streams[i] == null && selections[i] != null) {
                val stream = buildSampleStream(selections[i]!!, positionUs)
                streamList.add(stream)
                streams[i] = stream
                streamResetFlags[i] = true
            } else if (streams[i] != null) {
                @Suppress("UNCHECKED_CAST")
                streamList.add(streams[i] as ChunkSampleStream<SabrChunkSource>)
            }
        }

        val selectedRoles = streamList.map { if (it.primaryTrackType == C.TRACK_TYPE_VIDEO) SabrSession.ROLE_VIDEO else SabrSession.ROLE_AUDIO }.toSet()
        for (role in listOf(SabrSession.ROLE_VIDEO, SabrSession.ROLE_AUDIO))
            if (role !in selectedRoles) session.clearDemand(role, owner = null)

        sampleStreams = streamList.toTypedArray()
        compositeLoader = compositeSequenceableLoaderFactory.create(
            streamList,
            streamList.map { listOf(it.primaryTrackType) }
        )
        return positionUs
    }

    private fun buildSampleStream(selection: ExoTrackSelection, positionUs: Long): ChunkSampleStream<SabrChunkSource> {
        val group = groups[trackGroups.indexOf(selection.trackGroup)]

        val dataSource = SabrDataSource(session)

        val chunkSource = SabrChunkSource(
            session,
            group.role,
            group.formats,
            selection,
            dataSource,
            chunkExtractorFactory,
            playerId
        )

        return ChunkSampleStream(
            if (group.role == SabrSession.ROLE_VIDEO) C.TRACK_TYPE_VIDEO else C.TRACK_TYPE_AUDIO,
            null,
            null,
            chunkSource,
            this,
            allocator,
            positionUs,
            drmSessionManager,
            drmEventDispatcher,
            loadErrorHandlingPolicy,
            mediaSourceEventDispatcher,
            false,
            null
        )
    }

    override fun discardBuffer(positionUs: Long, toKeyframe: Boolean) {
        for (stream in sampleStreams) stream.discardBuffer(positionUs, toKeyframe)
    }

    override fun reevaluateBuffer(positionUs: Long) {
        session.setPlaybackPosition(positionUs + session.mediaBaseUs)
        compositeLoader.reevaluateBuffer(positionUs)
    }

    override fun continueLoading(loadingInfo: LoadingInfo): Boolean {
        session.setPlaybackPosition(loadingInfo.playbackPositionUs + session.mediaBaseUs)
        return compositeLoader.continueLoading(loadingInfo)
    }

    override fun isLoading(): Boolean = compositeLoader.isLoading

    override fun getNextLoadPositionUs(): Long = compositeLoader.nextLoadPositionUs

    override fun readDiscontinuity(): Long = C.TIME_UNSET

    override fun getBufferedPositionUs(): Long = compositeLoader.bufferedPositionUs

    override fun seekToUs(positionUs: Long): Long {
        session.seekTo(positionUs + session.mediaBaseUs)
        for (stream in sampleStreams) stream.seekToUs(positionUs)
        return positionUs
    }

    override fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: SeekParameters): Long {
        for (stream in sampleStreams)
            if (stream.primaryTrackType == C.TRACK_TYPE_VIDEO)
                return stream.getAdjustedSeekPositionUs(positionUs, seekParameters)
        return positionUs
    }

    override fun onContinueLoadingRequested(source: ChunkSampleStream<SabrChunkSource>) {
        callback?.onContinueLoadingRequested(this)
    }

    fun release() {
        session.setSegmentsChangedListener(null, owner = this)
        for (stream in sampleStreams) stream.release()
        callback = null
    }
}
