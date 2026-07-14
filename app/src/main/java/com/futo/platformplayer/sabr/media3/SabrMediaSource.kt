package com.futo.platformplayer.sabr.media3

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.drm.DrmSessionEventListener
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.source.BaseMediaSource
import androidx.media3.exoplayer.source.DefaultCompositeSequenceableLoaderFactory
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSourceEventListener
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.sabr.SabrSession
import com.futo.platformplayer.sabr.SabrSessionListener
import com.futo.platformplayer.sabr.SabrStreamSpec
import com.futo.platformplayer.sabr.proto.FormatInitializationMetadata
import com.futo.platformplayer.sabr.proto.LiveMetadata

@UnstableApi
class SabrMediaSource(
    private val mediaItem: MediaItem,
    private val spec: SabrStreamSpec,
    private val loadErrorHandlingPolicy: LoadErrorHandlingPolicy = DefaultLoadErrorHandlingPolicy(),
    private val onBackoff: ((delayMs: Long?) -> Unit)? = null,
    private val claimInitialState: (() -> SabrSession.Transferable?)? = null,
    private val viewportWidth: Int = 0,
    private val viewportHeight: Int = 0,
    private val initialBandwidthBytesPerSec: Long = 0
) : BaseMediaSource(), SabrSessionListener {

    class Factory(private val spec: SabrStreamSpec) {
        private var onBackoff: ((delayMs: Long?) -> Unit)? = null
        private var claimInitialState: (() -> SabrSession.Transferable?)? = null
        private var viewportWidth = 0
        private var viewportHeight = 0
        private var initialBandwidthBytesPerSec = 0L

        fun setViewport(width: Int, height: Int): Factory {
            viewportWidth = width
            viewportHeight = height
            return this
        }

        fun setInitialBandwidth(bytesPerSec: Long): Factory {
            initialBandwidthBytesPerSec = bytesPerSec
            return this
        }

        fun setBackoffListener(listener: ((delayMs: Long?) -> Unit)?): Factory {
            onBackoff = listener
            return this
        }

        fun setInitialStateProvider(provider: (() -> SabrSession.Transferable?)?): Factory {
            claimInitialState = provider
            return this
        }

        fun createMediaSource(mediaItem: MediaItem): SabrMediaSource =
            SabrMediaSource(mediaItem, spec, onBackoff = onBackoff, claimInitialState = claimInitialState,
                viewportWidth = viewportWidth, viewportHeight = viewportHeight,
                initialBandwidthBytesPerSec = initialBandwidthBytesPerSec)
    }

    private val sessionLock = Any()
    @Volatile private var session: SabrSession? = null
    private var claimed = false

    @Volatile private var discarded = false
    @Volatile private var prepared = false

    fun exportTransferable(): SabrSession.Transferable? = synchronized(sessionLock) {
        val current = session ?: return null
        if (current.isReleased || current.fatalError != null) return null
        return current.exportTransferable()
    }

    private var transferListener: TransferListener? = null
    private var playbackHandler: Handler? = null
    @Volatile private var bootstrapped = false

    override fun getMediaItem(): MediaItem = mediaItem

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) = synchronized(sessionLock) {
        this.transferListener = mediaTransferListener
        playbackHandler = Handler(Looper.myLooper() ?: Looper.getMainLooper())

        if (discarded) {
            Logger.w(TAG, "Refusing to prepare a discarded source")
            return
        }

        val existing = session?.takeIf { !it.isReleased && it.fatalError == null }
        val restored = if (existing != null || claimed) null else claimInitialState?.invoke()
        claimed = true
        val current = existing ?: spec.createSession().also { fresh ->
            fresh.viewportWidth = viewportWidth
            fresh.viewportHeight = viewportHeight
            fresh.initialBandwidthBytesPerSec = initialBandwidthBytesPerSec
            restored?.let {
                Logger.i(TAG, "Continuing the playback casting left behind")
                fresh.restore(it)
            }
        }
        prepared = true

        session?.takeIf { it !== current }?.release()
        session = current

        bootstrapped = current.liveMetadata != null && existing != null
        current.setListener(this)
        if (spec.isLive) {
            if (existing == null) {
                spec.videoFormats.firstOrNull()?.let { current.setDemand(SabrSession.ROLE_VIDEO, it, 0, owner = this) }
                spec.audioFormats.firstOrNull()?.let { current.setDemand(SabrSession.ROLE_AUDIO, it, 0, owner = this) }
            }
            current.start()

            if (current.liveMetadata != null) {
                if (!bootstrapped) {
                    bootstrapped = true
                    val edgeUs = liveEdgeStartUs() + current.mediaBaseUs
                    current.restart(edgeUs, force = true)
                    current.setPlaybackPosition(edgeUs)
                }
                refreshTimeline()
            }
        } else {
            refreshTimeline()
        }
    }

    override fun maybeThrowSourceInfoRefreshError() {
        session?.fatalError?.let { throw java.io.IOException(it) }
    }

    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod {
        val eventDispatcher = createEventDispatcher(id)
        val drmEventDispatcher = createDrmEventDispatcher(id)
        val current = synchronized(sessionLock) {
            if (discarded) throw IllegalStateException("Cannot create a period on a discarded SABR source")
            session?.takeIf { !it.isReleased }
        } ?: throw IllegalStateException("Cannot create a period on an unprepared SABR source")
        return SabrMediaPeriod(
            current,
            spec.videoFormats,
            spec.audioFormats,
            transferListener,
            allocator,
            DefaultCompositeSequenceableLoaderFactory(),
            DrmSessionManager.DRM_UNSUPPORTED,
            drmEventDispatcher,
            loadErrorHandlingPolicy,
            eventDispatcher,
            playerId
        )
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        (mediaPeriod as SabrMediaPeriod).release()
    }

    override fun releaseSourceInternal() {
        synchronized(sessionLock) {
            prepared = false
            session?.setListener(null)
            if (discarded) session?.release()
        }
        playbackHandler = null
    }

    fun releaseSession() {
        synchronized(sessionLock) {
            discarded = true
            if (!prepared) session?.release()
        }
    }

    override fun onLiveMetadata(metadata: LiveMetadata) {
        playbackHandler?.post {
            val current = session ?: return@post
            if (spec.isLive && !bootstrapped) {
                bootstrapped = true
                val edgeUs = liveEdgeStartUs() + current.mediaBaseUs
                current.restart(edgeUs)
                current.setPlaybackPosition(edgeUs)
            }
            refreshTimeline()
        }
    }

    override fun onFormatInitialization(metadata: FormatInitializationMetadata) {}

    override fun onBackoff(delayMs: Long) {
        onBackoff?.invoke(delayMs)
    }

    override fun onBackoffEnded() {
        onBackoff?.invoke(null)
    }

    override fun onSessionError(error: Throwable) {
        Logger.w(TAG, "SABR session error", error)
    }

    private fun refreshTimeline() {
        refreshSourceInfo(buildTimeline())
    }

    private fun buildTimeline(): Timeline {
        if (!spec.isLive) {
            val durationUs = if (spec.durationUs > 0) spec.durationUs else C.TIME_UNSET
            return SinglePeriodTimeline(
                durationUs,
                true,
                false,
                false,
                null,
                mediaItem
            )
        }

        val windowDurationUs = liveWindowUs()

        return SinglePeriodTimeline(
            windowDurationUs,
            windowDurationUs,
            0L,
            liveEdgeStartUs(),
            true,
            true,
            true,
            null,
            mediaItem
        )
    }

    private fun liveWindowUs(): Long {
        val current = session ?: return C.TIME_UNSET
        val live = current.liveMetadata ?: return C.TIME_UNSET
        val headUs = live.headSequenceTimeMs * 1_000L - current.mediaBaseUs
        return if (headUs > 0) headUs else C.TIME_UNSET
    }

    private fun liveEdgeStartUs(): Long {
        val window = liveWindowUs()
        if (window == C.TIME_UNSET) return 0
        return (window - LIVE_TARGET_OFFSET_US).coerceAtLeast(0)
    }

    companion object {
        private const val TAG = "SabrMediaSource"
        private const val LIVE_TARGET_OFFSET_US = 15_000_000L
    }
}
