package com.futo.platformplayer.sabr

import android.util.Base64
import okhttp3.Call
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.models.modifier.IRequestModifier
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.sabr.proto.AdCuepointConfig
import com.futo.platformplayer.sabr.proto.BufferedRange
import com.futo.platformplayer.sabr.proto.CuepointEvent
import com.futo.platformplayer.sabr.proto.CuepointList
import com.futo.platformplayer.sabr.proto.ClientAbrState
import com.futo.platformplayer.sabr.proto.ClientInfo
import com.futo.platformplayer.sabr.proto.FormatInitializationMetadata
import com.futo.platformplayer.sabr.proto.LiveMetadata
import com.futo.platformplayer.sabr.proto.MediaHeader
import com.futo.platformplayer.sabr.proto.MediaType
import com.futo.platformplayer.sabr.proto.NextRequestPolicy
import com.futo.platformplayer.sabr.proto.SabrContext
import com.futo.platformplayer.sabr.proto.SabrContextSendingPolicy
import com.futo.platformplayer.sabr.proto.SabrContextUpdate
import com.futo.platformplayer.sabr.proto.SabrRedirect
import com.futo.platformplayer.sabr.proto.SnackbarMessage
import com.futo.platformplayer.sabr.proto.StreamProtectionStatus
import com.futo.platformplayer.sabr.proto.StreamerContext
import com.futo.platformplayer.sabr.proto.VideoPlaybackAbrRequest
import com.google.protobuf.ByteString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random

interface SabrSessionListener {
    fun onLiveMetadata(metadata: LiveMetadata)
    fun onFormatInitialization(metadata: FormatInitializationMetadata)
    fun onSessionError(error: Throwable)

    fun onBackoff(delayMs: Long) {}

    fun onBackoffEnded() {}
}

class SabrSession(
    private val httpClient: ManagedHttpClient,
    serverAbrStreamingUrl: String,
    private val ustreamerConfig: ByteArray,
    val videoId: String,
    private val clientInfo: ClientInfo,
    private val poTokenRaw: String?,
    val isLive: Boolean,
    val durationUs: Long,
    private val ownsHttpClient: Boolean = false
) {
    private class Demand(
        val format: SabrFormat,
        val fromUs: Long,
        val owner: Any?,
        val alternates: List<SabrFormat> = listOf(format)
    )

    private val serverChosen = ConcurrentHashMap<Int, SabrFormatKey>()

    private val adCuepoints = java.util.concurrent.ConcurrentSkipListMap<String, Long>()

    private val pumpLock = Object()
    private val buffers = ConcurrentHashMap<SabrFormatKey, SabrTrackBuffer>()
    private val sabrContexts = ConcurrentHashMap<Int, SabrContext>()
    private val activeSabrContexts = java.util.Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    private val formatInitialization = ConcurrentHashMap<SabrFormatKey, FormatInitializationMetadata>()
    private val formatComplete = ConcurrentHashMap<SabrFormatKey, Boolean>()
    private val formatNoProgress = ConcurrentHashMap<SabrFormatKey, Int>()
    @Volatile private var emptyResponses = 0

    @Volatile private var demandedHeaders = 0
    @Volatile private var foreignHeaders = 0
    @Volatile private var substitutedResponses = 0
    @Volatile private var consecutiveRedirects = 0

    private val demandedKeys = java.util.Collections.newSetFromMap(ConcurrentHashMap<SabrFormatKey, Boolean>())
    private val requestNumber = AtomicInteger(0)
    @Volatile private var createdAtMs = System.currentTimeMillis()

    private var pumpThread: Thread? = null

    init {
        val live = liveSessions.incrementAndGet()
        sabrLog("Session created for $videoId (live sessions: $live)")
    }

    private val releasedFlag = java.util.concurrent.atomic.AtomicBoolean(false)
    private val released: Boolean get() = releasedFlag.get()
    val isReleased: Boolean get() = releasedFlag.get()
    @Volatile private var streamingUrl = serverAbrStreamingUrl
    @Volatile private var poTokenDecoded: ByteString? = null
    @Volatile private var poTokenResolved = false
    @Volatile private var playbackCookie: ByteString? = null
    @Volatile private var backoffUntilMs = 0L
    @Volatile private var serverBackoffUntilMs = 0L
    @Volatile private var errorBackoffUntilMs = 0L
    @Volatile private var consecutiveErrors = 0
    @Volatile private var backoffNotified = false
    @Volatile private var backoffShown = false
    @Volatile private var lastWaitLogMs = 0L
    @Volatile private var lastRequestMs = 0L
    @Volatile private var lastActionMs = System.currentTimeMillis()
    @Volatile private var currentResponse: ManagedHttpClient.Response? = null
    @Volatile private var currentCall: Call? = null
    @Volatile private var aborting = false
    @Volatile private var restartEpoch = 0

    val restartCount: Int get() = restartEpoch

    @Volatile var mediaBaseUs = 0L
        private set
    @Volatile private var mediaBaseSet = false

    @Volatile private var targetVideoReadaheadMs = DEFAULT_READAHEAD_MS
    @Volatile private var targetAudioReadaheadMs = DEFAULT_READAHEAD_MS

    @Volatile private var videoDemand: Demand? = null
    @Volatile private var audioDemand: Demand? = null

    @Volatile private var playbackPositionUs = 0L

    @Volatile private var resumePositionUs: Long? = null

    @Volatile private var restartFromUs = Long.MIN_VALUE

    val fetchFloorUs: Long get() = resumePositionUs ?: restartFromUs

    @Volatile private var listener: SabrSessionListener? = null

    @Volatile var onSegmentsChanged: Runnable? = null

    @Volatile var keepBehindUs: Long = DEFAULT_KEEP_BEHIND_US

    @Volatile var viewportWidth = 0
    @Volatile var viewportHeight = 0

    @Volatile var initialBandwidthBytesPerSec = 0L

    @Volatile var fatalError: Throwable? = null
        private set

    @Volatile var liveMetadata: LiveMetadata? = null
        private set

    @Volatile var liveMetadataAtMs = 0L
        private set

    fun setListener(listener: SabrSessionListener?) {
        this.listener = listener
    }

    fun setSegmentsChangedListener(runnable: Runnable?, owner: Any? = null) {
        if (runnable == null && owner != null && segmentsChangedOwner !== owner) return
        segmentsChangedOwner = if (runnable == null) null else owner
        onSegmentsChanged = runnable
    }

    @Volatile private var segmentsChangedOwner: Any? = null

    class Transferable(
        val requestNumber: Int,
        val playbackCookie: ByteString?,
        val sabrContexts: Map<Int, SabrContext>,
        val activeSabrContexts: Set<Int>,
        val streamingUrl: String,
        val backoffUntilMs: Long,
        val serverBackoffUntilMs: Long,
        val mediaBaseUs: Long,
        val mediaBaseSet: Boolean,
        val formatInitialization: Map<SabrFormatKey, FormatInitializationMetadata>,
        val liveMetadata: LiveMetadata?
    )

    fun exportTransferable(): Transferable = Transferable(
        requestNumber = requestNumber.get(),
        playbackCookie = playbackCookie,
        sabrContexts = sabrContexts.toMap(),
        activeSabrContexts = activeSabrContexts.toSet(),
        streamingUrl = streamingUrl,
        backoffUntilMs = backoffUntilMs,
        serverBackoffUntilMs = serverBackoffUntilMs,
        mediaBaseUs = mediaBaseUs,
        mediaBaseSet = mediaBaseSet,
        formatInitialization = formatInitialization.toMap(),
        liveMetadata = liveMetadata
    ).also {
        val remainingMs = backoffUntilMs - System.currentTimeMillis()
        sabrLog("Exported session state: rn=${it.requestNumber} contexts=${it.sabrContexts.keys.sorted()} " +
            "active=${it.activeSabrContexts.sorted()} " +
            "cookie=${it.playbackCookie?.size() ?: 0}b backoffRemaining=${if (remainingMs > 0) remainingMs else 0}ms")
    }

    fun restore(state: Transferable) {
        sabrContexts.putAll(state.sabrContexts)
        activeSabrContexts.addAll(state.activeSabrContexts)
        inheritedContexts = true

        sabrLog("Restored SABR contexts: ${state.sabrContexts.keys.sorted()} " +
            "active=${state.activeSabrContexts.sorted()} (the session's own identity is NOT inherited)")
    }

    @Volatile private var inheritedContexts = false

    private fun hostOf(url: String): String =
        try { java.net.URI(url).host ?: "?" } catch (_: Throwable) { "?" }

    fun bufferFor(format: SabrFormat): SabrTrackBuffer = bufferFor(format.key)

    fun bufferFor(key: SabrFormatKey): SabrTrackBuffer = buffers.computeIfAbsent(key) { SabrTrackBuffer(it) }

    fun formatInitializationFor(format: SabrFormat): FormatInitializationMetadata? = formatInitialization[format.key]

    fun observedFormatKeys(): Set<SabrFormatKey> = buffers.keys.toSet()

    fun hasSeparateInit(format: SabrFormat): Boolean {
        if (bufferFor(format.key).initSegment != null) return true
        return !isLive
    }

    fun start() {
        synchronized(pumpLock) {
            if (pumpThread != null || released || fatalError != null) return
            pumpThread = thread(name = "SabrSession-$videoId", isDaemon = true) { pump() }
        }
    }

    fun release() {
        if (releasedFlag.getAndSet(true)) return
        sabrLog("Session released for $videoId (live sessions: ${liveSessions.decrementAndGet()})")
        synchronized(pumpLock) { pumpLock.notifyAll() }
        buffers.values.forEach { it.notifyChanged() }
        try { currentCall?.cancel() } catch (_: Throwable) {}
        pumpThread?.interrupt()
        pumpThread = null

        buffers.clear()
        formatInitialization.clear()
        listener = null
        onSegmentsChanged = null
        segmentsChangedOwner = null
        if (ownsHttpClient) try { httpClient.close() } catch (_: Throwable) {}
    }

    fun setDemand(role: Int, format: SabrFormat, fromUs: Long, owner: Any? = null) =
        setDemand(role, listOf(format), fromUs, owner)

    fun setDemand(role: Int, acceptable: List<SabrFormat>, fromUs: Long, owner: Any? = null) {
        if (acceptable.isEmpty()) return
        val previous = if (role == ROLE_VIDEO) videoDemand else audioDemand

        val active = previous?.format?.takeIf { current -> acceptable.any { it.key == current.key } }
            ?: serverChosen[role]?.let { chosen -> acceptable.firstOrNull { it.key == chosen } }
            ?: acceptable.first()

        if (previous != null && previous.format.key == active.key && previous.fromUs == fromUs &&
            previous.alternates.size == acceptable.size &&
            previous.alternates.zip(acceptable).all { (a, b) -> a.key == b.key }) {
            if (previous.owner !== owner) {
                val demand = Demand(active, fromUs, owner, acceptable)
                if (role == ROLE_VIDEO) videoDemand = demand else audioDemand = demand
            }
            return
        }

        if (previous == null || previous.format.key != active.key) {
            lastActionMs = System.currentTimeMillis()
            formatComplete.remove(active.key)
            formatNoProgress.remove(active.key)
        }

        acceptable.forEach { demandedKeys.add(it.key) }

        val demand = Demand(active, fromUs, owner, acceptable)
        if (role == ROLE_VIDEO) videoDemand = demand else audioDemand = demand
        wakePump()
    }

    fun activeFormat(role: Int): SabrFormat? =
        (if (role == ROLE_VIDEO) videoDemand else audioDemand)?.format

    private fun adoptServerFormat(key: SabrFormatKey) {
        for (role in intArrayOf(ROLE_VIDEO, ROLE_AUDIO)) {
            val demand = if (role == ROLE_VIDEO) videoDemand else audioDemand ?: continue
            if (demand == null || demand.format.key == key) continue
            val chosen = demand.alternates.firstOrNull { it.key == key } ?: continue
            if (demand.alternates.size <= 1) continue

            sabrLog("Server switched role=$role from itag=${demand.format.itag} to itag=${chosen.itag}")
            serverChosen[role] = key
            val moved = Demand(chosen, demand.fromUs, demand.owner, demand.alternates)
            if (role == ROLE_VIDEO) videoDemand = moved else audioDemand = moved
            return
        }
    }


    fun clearDemand(role: Int, owner: Any? = null) {
        val current = if (role == ROLE_VIDEO) videoDemand else audioDemand
        if (current == null || current.owner !== owner) return
        if (role == ROLE_VIDEO) videoDemand = null else audioDemand = null
        sabrLog("Demand for role=$role cleared by its owner")
    }

    fun setPlaybackPosition(positionUs: Long) {
        playbackPositionUs = positionUs
    }

    fun seekTo(fromUs: Long) {
        synchronized(pumpLock) {
            val demands = listOfNotNull(videoDemand, audioDemand)
            val buffered = demands.isNotEmpty() && demands.all { demand ->
                val segment = bufferFor(demand.format).firstCovering(fromUs)
                segment != null && segment.startUs <= fromUs
            }
            if (!buffered) {
                restart(fromUs)
                return
            }

            playbackPositionUs = fromUs
            videoDemand?.let { videoDemand = Demand(it.format, fromUs, it.owner, it.alternates) }
            audioDemand?.let { audioDemand = Demand(it.format, fromUs, it.owner, it.alternates) }
            lastActionMs = System.currentTimeMillis()
            pumpLock.notifyAll()
        }
    }

    fun restart(fromUs: Long, force: Boolean = false) {
        synchronized(pumpLock) {
            val current = resumePositionUs
            if (!force && current != null && Math.abs(current - fromUs) < RESTART_TOLERANCE_US) return

            playbackPositionUs = fromUs
            resumePositionUs = fromUs
            restartFromUs = fromUs
            seekPendingUs = null
            lastActionMs = System.currentTimeMillis()
            restartEpoch++
            buffers.values.forEach { it.clear() }
            formatComplete.clear()
            formatNoProgress.clear()
            emptyResponses = 0
            substitutedResponses = 0
            consecutiveRedirects = 0

            videoDemand?.let { videoDemand = Demand(it.format, fromUs, it.owner, it.alternates) }
            audioDemand?.let { audioDemand = Demand(it.format, fromUs, it.owner, it.alternates) }

            backoffUntilMs = maxOf(serverBackoffUntilMs, errorBackoffUntilMs)
            aborting = true
            try { currentCall?.cancel() } catch (_: Throwable) {}
            pumpLock.notifyAll()
        }
    }

    fun wakePump() {
        synchronized(pumpLock) { pumpLock.notifyAll() }
    }

    private fun pump() {
        try {
            pumpLoop()
        } finally {
            synchronized(pumpLock) { if (pumpThread === Thread.currentThread()) pumpThread = null }
        }
    }

    private fun pumpLoop() {
        while (!released) {
            try {
                synchronized(pumpLock) {
                    while (!released && !needsData()) pumpLock.wait(PUMP_IDLE_POLL_MS)
                }
                if (released) return

                val now = System.currentTimeMillis()
                val waitMs = backoffUntilMs - now
                val serverWaitMs = serverBackoffUntilMs - now
                if (waitMs > 0) {
                    if (serverWaitMs > 0) {
                        if (!backoffNotified) {
                            backoffNotified = true
                            sabrLog("Waiting ${serverWaitMs}ms -- SERVER")

                            if (starved()) {
                                backoffShown = true
                                listener?.onBackoff(serverWaitMs)
                            }
                        }
                    } else if (now - lastWaitLogMs > 1_000) {
                        lastWaitLogMs = now
                        val reason = if (errorBackoffUntilMs > now) "error retry" else "stall (empty responses)"
                        sabrLog("Waiting ${waitMs}ms -- ours, $reason (not shown)")
                    }
                    synchronized(pumpLock) { pumpLock.wait(waitMs.coerceAtMost(PUMP_IDLE_POLL_MS)) }
                    continue
                }
                if (backoffNotified) {
                    backoffNotified = false
                    if (backoffShown) {
                        backoffShown = false
                        listener?.onBackoffEnded()
                    }
                }

                evictConsumedSegments()
                performRequest()
                consecutiveErrors = 0
                aborting = false
            } catch (interrupted: InterruptedException) {
                if (!released) {
                    fatalError = interrupted
                    listener?.onSessionError(interrupted)
                    buffers.values.forEach { it.notifyChanged() }
                }
                return
            } catch (ex: Throwable) {
                if (released) return
                if (aborting) {
                    aborting = false

                    if (resumePositionUs != null)
                        synchronized(pumpLock) { buffers.values.forEach { it.clear() } }
                    continue
                }
                Logger.e(TAG, "SABR request failed", ex)

                if (ex is SabrBlockedException) {
                    sabrLog("BLOCKED: ${ex.message}. po token is static; surfacing for reload.")
                    fatalError = ex
                    listener?.onSessionError(ex)
                    buffers.values.forEach { it.notifyChanged() }
                    return
                }

                if (ex is SabrReloadRequiredException) {
                    fatalError = ex
                    listener?.onSessionError(ex)
                    buffers.values.forEach { it.notifyChanged() }
                    return
                }
                consecutiveErrors++
                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    fatalError = ex
                    listener?.onSessionError(ex)
                    buffers.values.forEach { it.notifyChanged() }
                    return
                }
                listener?.onSessionError(ex)
                val delay = (ERROR_BACKOFF_BASE_MS shl (consecutiveErrors - 1).coerceAtMost(ERROR_BACKOFF_MAX_SHIFT))
                    .coerceAtMost(ERROR_BACKOFF_MAX_MS)
                errorBackoffUntilMs = System.currentTimeMillis() + delay
                backoffUntilMs = maxOf(serverBackoffUntilMs, errorBackoffUntilMs)
            }
        }
    }

    @Volatile private var mediaBytes = 0L
    @Volatile private var mediaUsDelivered = 0L
    @Volatile private var throughputBytesPerSec = 0L

    private fun bandwidthEstimate(): Long =
        throughputBytesPerSec.takeIf { it > 0 }
            ?: initialBandwidthBytesPerSec.takeIf { it > 0 }
            ?: BANDWIDTH_ESTIMATE

    private fun recordThroughput(bytes: Long, elapsedMs: Long, mediaUs: Long) {
        if (bytes < THROUGHPUT_MIN_BYTES || elapsedMs <= 0) return
        if (mediaUs < elapsedMs * 1000 * THROUGHPUT_MIN_SPEEDUP) return

        val sample = bytes * 1000 / elapsedMs
        throughputBytesPerSec =
            if (throughputBytesPerSec == 0L) sample
            else (throughputBytesPerSec * (100 - THROUGHPUT_SMOOTHING) + sample * THROUGHPUT_SMOOTHING) / 100
    }

    private fun starved(): Boolean {
        val demands = listOfNotNull(videoDemand, audioDemand)
        if (demands.isEmpty()) return false
        for (demand in demands) {
            val buffer = bufferFor(demand.format)
            val end = buffer.bufferedEndUs(effectiveFromUs(buffer, demand.fromUs))
            if (end == Long.MIN_VALUE) return true
            if (end - playbackPositionUs < STARVED_US) return true
        }
        return false
    }

    private fun needsData(): Boolean {
        val video = videoDemand
        val audio = audioDemand
        if (video == null && audio == null) return false
        if (resumePositionUs != null) return true
        if (video != null && needsData(video, targetVideoReadaheadMs)) return true
        if (audio != null && needsData(audio, targetAudioReadaheadMs)) return true
        return false
    }

    private fun needsData(demand: Demand, targetMs: Long): Boolean {
        if (isComplete(demand.format)) return false
        val buffer = bufferFor(demand.format)

        val anchor = if (isLive) maxOf(demand.fromUs, refreshPlayheadUs()) else demand.fromUs
        val from = effectiveFromUs(buffer, anchor)
        val end = buffer.bufferedEndUs(from)
        if (end == Long.MIN_VALUE) return true

        var target = maxOf(targetMs, minReadaheadMs)
        if (maxReadaheadMs > 0) target = minOf(target, maxReadaheadMs)
        if (end - from >= target * 1000) return false
        return true
    }

    private fun effectiveFromUs(buffer: SabrTrackBuffer, fromUs: Long): Long {
        val first = buffer.firstAtOrAfter(-1)?.startUs ?: return fromUs
        return maxOf(fromUs, first)
    }

    @Volatile var minReadaheadMs = 0L

    @Volatile var maxReadaheadMs = 0L

    fun isComplete(format: SabrFormat): Boolean {
        if (isLive) return false
        if (formatComplete[format.key] == true) return true
        val endSegment = formatInitialization[format.key]?.endSegmentNumber ?: 0
        if (endSegment <= 0) return false

        val fromUs = demandFromUs(format)
        if (bufferFor(format).lastCompletedSequence(fromUs) >= endSegment) {
            formatComplete[format.key] = true
            return true
        }
        return false
    }

    private fun demandFromUs(format: SabrFormat): Long {
        val raw = when {
            videoDemand?.alternates?.any { it.key == format.key } == true -> videoDemand?.fromUs
            audioDemand?.alternates?.any { it.key == format.key } == true -> audioDemand?.fromUs
            else -> null
        } ?: return Long.MIN_VALUE
        return effectiveFromUs(bufferFor(format), raw)
    }

    @Volatile var playheadProvider: (() -> Long)? = null

    private fun refreshPlayheadUs(): Long {
        if (resumePositionUs == null && seekPendingUs == null) {
            playheadProvider?.invoke()?.takeIf { it != Long.MIN_VALUE }?.let { playbackPositionUs = it }
        }
        return playbackPositionUs
    }

    private fun requestPositionUs(): Long {
        resumePositionUs?.let { return it }
        seekPendingUs?.let { return it }

        refreshPlayheadUs()

        if (isLive && liveMetadata == null) return LIVE_HEAD_PLAYER_TIME_US

        var earliest = Long.MAX_VALUE
        var lowestStart = Long.MAX_VALUE
        for (demand in listOfNotNull(videoDemand, audioDemand)) {
            val buffer = bufferFor(demand.format)
            val effective = effectiveFromUs(buffer, demand.fromUs)
            val end = buffer.bufferedEndUs(effective)
            val from = if (end == Long.MIN_VALUE) effective else maxOf(effective, end)
            earliest = minOf(earliest, from)
            buffer.firstAtOrAfter(-1)?.startUs?.let { lowestStart = minOf(lowestStart, it) }
        }
        if (!isLive) return if (earliest == Long.MAX_VALUE) playbackPositionUs else earliest

        val headUs = liveMetadata?.headSequenceTimeMs?.takeIf { it > 0 }?.times(1000L) ?: Long.MAX_VALUE
        if (earliest == Long.MAX_VALUE) return playbackPositionUs.coerceAtMost(headUs)
        return playbackPositionUs.coerceAtMost(earliest).coerceAtMost(headUs)
    }

    private fun evictConsumedSegments() {
        val floor = resumePositionUs ?: playbackPositionUs
        val threshold = minOf(playbackPositionUs, floor) - keepBehindUs
        if (threshold <= 0) return
        for (buffer in buffers.values)
            buffer.evictBefore(threshold)
    }

    private fun performRequest() {
        val startEpoch: Int
        val video: SabrFormat?
        val audio: SabrFormat?
        val requestedResume: Long?
        val positionUs: Long
        synchronized(pumpLock) {
            aborting = false
            startEpoch = restartEpoch
            video = videoDemand?.format
            audio = audioDemand?.format
            if (video == null && audio == null) return
            requestedResume = resumePositionUs
            positionUs = requestPositionUs()
        }
        demandedHeaders = 0
        foreignHeaders = 0

        run {
            val v = video?.let { "v[itag=${it.itag} lastSeq=${bufferFor(it).lastCompletedFromFront()} end=${bufferFor(it).bufferedEndFromFrontUs() / 1000}ms]" } ?: ""
            val a = audio?.let { "a[itag=${it.itag} lastSeq=${bufferFor(it).lastCompletedFromFront()} end=${bufferFor(it).bufferedEndFromFrontUs() / 1000}ms]" } ?: ""
            val edge = liveMetadata?.takeIf { isLive && it.headSequenceTimeMs > 0 }?.headSequenceTimeMs
            val lag = edge?.let { e ->
                listOfNotNull(video, audio)
                    .map { bufferFor(it).bufferedEndFromFrontUs() }
                    .filter { it != Long.MIN_VALUE }
                    .minOrNull()?.let { " edgeLag=${e - it / 1000}ms" }
            } ?: ""
            sabrLog("Request #${requestNumber.get() + 1} live=$isLive playerTimeMs=${positionUs / 1000} $v $a$lag " +
                "bw=${bandwidthEstimate() / 1024}KB/s${if (throughputBytesPerSec == 0L) "(seed)" else ""} " +
                "readahead=v${targetVideoReadaheadMs}ms/a${targetAudioReadaheadMs}ms")
        }

        val body = buildRequest(video, audio, positionUs / 1000).toByteArray()

        val url = appendRequestNumber(streamingUrl)
        val headers = mutableMapOf(
            "Content-Type" to "application/x-protobuf",
            "Accept" to "application/vnd.yt-ump",
            "Accept-Encoding" to "identity",
            "Origin" to "https://www.youtube.com",
            "Referer" to "https://www.youtube.com/"
        )
        val finalUrl = url
        val finalHeaders = headers

        val videoBefore = video?.let { Pair(bufferFor(it).highestSequence, bufferFor(it).bufferedEndUs(demandFromUs(it))) }
        val audioBefore = audio?.let { Pair(bufferFor(it).highestSequence, bufferFor(it).bufferedEndUs(demandFromUs(it))) }
        val videoCountBefore = video?.let { bufferFor(it).segmentCount } ?: 0
        val audioCountBefore = audio?.let { bufferFor(it).segmentCount } ?: 0
        val videoInitBefore = video?.let { bufferFor(it).initSegment }
        val audioInitBefore = audio?.let { bufferFor(it).initSegment }

        lastRequestMs = System.currentTimeMillis()
        val request = ManagedHttpClient.Request(finalUrl, "POST", body, finalHeaders)
        request.onCallCreated.subscribe { currentCall = it }

        val rn = requestNumber.get()
        val sentMs = System.currentTimeMillis()
        val response = try {
            httpClient.execute(request)
        } catch (ex: Throwable) {
            sabrLog("REQUEST FAILED after ${System.currentTimeMillis() - sentMs}ms " +
                "rn=$rn host=${hostOf(finalUrl)} firstOfSession=${rn == 1} inheritedContexts=$inheritedContexts " +
                "sentContexts=${activeSabrContexts.sorted()} cookie=${playbackCookie?.size() ?: 0}b " +
                "v=${video?.itag} a=${audio?.itag} bodyBytes=${body.size} -- ${ex.javaClass.simpleName}: ${ex.message}")
            throw ex
        }
        val headersMs = System.currentTimeMillis() - sentMs
        if (headersMs > SLOW_REQUEST_LOG_MS)
            sabrLog("SLOW REQUEST ${headersMs}ms to first response byte " +
                "rn=$rn host=${hostOf(finalUrl)} firstOfSession=${rn == 1} inheritedContexts=$inheritedContexts " +
                "sentContexts=${activeSabrContexts.sorted()} cookie=${playbackCookie?.size() ?: 0}b " +
                "v=${video?.itag} a=${audio?.itag} bodyBytes=${body.size} code=${response.code}")

        currentResponse = response
        if (released) {
            try { response.body?.close() } catch (_: Throwable) {}
            currentResponse = null
            currentCall = null
            return
        }
        if (!response.isOk) {
            response.body?.close()
            currentResponse = null
            currentCall = null
            if (response.code == 403)
                throw SabrBlockedException("SABR request returned HTTP 403")
            throw SabrException("SABR request returned HTTP ${response.code}")
        }

        synchronized(pumpLock) {
            if (resumePositionUs == requestedResume) resumePositionUs = null
        }

        val acceptedKeys = (videoDemand?.alternates.orEmpty() + audioDemand?.alternates.orEmpty())
            .map { it.key }.toSet()

        val redirected: Boolean
        val bytesBefore = mediaBytes
        val mediaUsBefore = mediaUsDelivered
        try {
            val stream = response.body?.byteStream() ?: throw SabrException("SABR response had no body")
            redirected = stream.use { consume(UmpReader(it), positionUs, acceptedKeys) }
        } finally {
            recordThroughput(
                mediaBytes - bytesBefore,
                System.currentTimeMillis() - sentMs,
                mediaUsDelivered - mediaUsBefore
            )
            try { response.body?.close() } catch (_: Throwable) {}
            currentResponse = null
            currentCall = null
        }

        if (restartEpoch != startEpoch) {
            synchronized(pumpLock) { buffers.values.forEach { it.clear() } }
            emptyResponses = 0
            return
        }

        val advanced = (video != null && bufferFor(video).segmentCount > videoCountBefore) ||
            (audio != null && bufferFor(audio).segmentCount > audioCountBefore) ||
            (video != null && videoInitBefore == null && bufferFor(video).initSegment != null) ||
            (audio != null && audioInitBefore == null && bufferFor(audio).initSegment != null)

        clearSeekIfLanded(advanced)

        if (advanced) {
            emptyResponses = 0
            substitutedResponses = 0
            consecutiveRedirects = 0
            errorBackoffUntilMs = 0
            backoffUntilMs = serverBackoffUntilMs
        }

        if (isLive) {
            if (!aborting && !redirected) clampToSeekableWindow()
            if (!advanced && !redirected)
                backoffUntilMs = maxOf(backoffUntilMs, System.currentTimeMillis() + LIVE_POLL_MS)
        } else if (!advanced && !aborting && !redirected) {
            updateProgress(video, videoBefore, positionUs)
            updateProgress(audio, audioBefore, positionUs)
            if ((video == null || isComplete(video)) && (audio == null || isComplete(audio))) return

            val demandStable = videoDemand?.format?.key == video?.key && audioDemand?.format?.key == audio?.key
            if (demandedHeaders == 0 && foreignHeaders > 0 && demandStable) {
                substitutedResponses++
                if (substitutedResponses >= MAX_SUBSTITUTED_RESPONSES)
                    throw SabrFormatSubstitutedException(
                        "Requested itag=${video?.itag ?: audio?.itag} but the server served a different " +
                            "format for $substitutedResponses consecutive requests. The plugin's formats " +
                            "are out of sync with the app.")
            }

            emptyResponses++
            val delay = (EMPTY_BACKOFF_BASE_MS shl (emptyResponses - 1).coerceAtMost(ERROR_BACKOFF_MAX_SHIFT))
                .coerceAtMost(ERROR_BACKOFF_MAX_MS)
            backoffUntilMs = maxOf(backoffUntilMs, System.currentTimeMillis() + delay)
            sabrLog("Response carried no new media ($emptyResponses/$MAX_EMPTY_RESPONSES) " +
                "at playerTimeMs=${positionUs / 1000} demandedHeaders=$demandedHeaders foreign=$foreignHeaders")
            if (emptyResponses >= MAX_EMPTY_RESPONSES)
                throw SabrException("Server returned no media for $emptyResponses consecutive requests")
        } else if (!isLive && !aborting && !redirected) {
            updateProgress(video, videoBefore, positionUs)
            updateProgress(audio, audioBefore, positionUs)
        }
    }

    private fun updateProgress(format: SabrFormat?, before: Pair<Int, Long>?, requestedUs: Long) {
        if (format == null || before == null) return
        if (formatInitialization[format.key]?.let { it.endSegmentNumber > 0 } == true) return

        val (beforeSeq, beforeEnd) = before
        val advanced = bufferFor(format).highestSequence > beforeSeq
        if (advanced) {
            formatNoProgress[format.key] = 0
            return
        }

        if (beforeSeq < 0 || beforeEnd == Long.MIN_VALUE) return
        if (!formatInitialization.containsKey(format.key)) return

        val endUs = formatInitialization[format.key]?.endTimeMs?.takeIf { it > 0 }?.times(1000)
            ?: durationUs
        val slackUs = maxOf(FRONTIER_EPSILON_US, bufferFor(format).get(beforeSeq)?.durationUs ?: 0)
        if (endUs > 0 && beforeEnd < endUs - FRONTIER_EPSILON_US) return
        if (endUs > 0 && requestedUs < endUs - slackUs) return

        val atFrontier = beforeEnd <= requestedUs + FRONTIER_EPSILON_US
        if (!atFrontier) return
        val n = (formatNoProgress[format.key] ?: 0) + 1
        formatNoProgress[format.key] = n
        if (n >= NO_PROGRESS_THRESHOLD) formatComplete[format.key] = true
    }


    private fun seekSourceName(source: Int): String = when (source) {
        9 -> "SABR_PARTIAL_CHUNK"
        10 -> "SABR_SEEK_TO_HEAD"
        12 -> "SABR_SEEK_TO_DVR_LOWER_BOUND"
        13 -> "SABR_SEEK_TO_DVR_UPPER_BOUND"
        17 -> "SABR_ACCURATE_SEEK"
        29 -> "SABR_INGESTION_WALL_TIME_SEEK"
        59 -> "SABR_SEEK_TO_CLOSEST_KEYFRAME"
        108 -> "SABR_RELOAD_PLAYER_RESPONSE_TOKEN_SEEK"
        else -> source.toString()
    }

    @Volatile private var lastSabrSeekUs = Long.MIN_VALUE

    @Volatile private var seekPendingUs: Long? = null

    private fun applySabrSeek(seekToUs: Long, requestedPositionUs: Long) {
        if (seekToUs == lastSabrSeekUs && seekPendingUs == null) return

        val metadata = liveMetadata
        if (isLive && metadata != null) {
            val minScale = metadata.minSeekableTimescale
            val maxScale = metadata.maxSeekableTimescale
            if (minScale <= 0 || maxScale <= 0) return
            val windowStartUs = metadata.minSeekableTimeTicks * 1_000_000L / minScale
            val windowEndUs = metadata.maxSeekableTimeTicks * 1_000_000L / maxScale
            if (seekToUs < windowStartUs || seekToUs > windowEndUs + SABR_SEEK_SLACK_US) {
                liveLog("Ignoring SabrSeek to ${seekToUs / 1000}ms: outside the seekable window " +
                    "${windowStartUs / 1000}..${windowEndUs / 1000}ms")
                return
            }
        }

        liveLog("SabrSeek: the server will not serve ${requestedPositionUs / 1000}ms, moving to ${seekToUs / 1000}ms")
        lastSabrSeekUs = seekToUs

        synchronized(pumpLock) {
            if (adCuepoints.isNotEmpty()) {
                sabrLog("Dropping ${adCuepoints.size} cuepoint(s) across a server seek")
                adCuepoints.clear()
            }
            seekPendingUs = seekToUs
            playbackPositionUs = seekToUs
            videoDemand?.let { videoDemand = Demand(it.format, seekToUs, it.owner, it.alternates) }
            audioDemand?.let { audioDemand = Demand(it.format, seekToUs, it.owner, it.alternates) }
            lastActionMs = System.currentTimeMillis()
            pumpLock.notifyAll()
        }
    }

    private fun onCuepointList(list: CuepointList) {
        for (info in list.cuepointInfoList) {
            val cuepoint = info.cuepoint ?: continue
            val id = cuepoint.identifier
            if (id.isNullOrEmpty()) continue

            if (cuepoint.event == CuepointEvent.CUEPOINT_EVENT_STOP) {
                if (adCuepoints.remove(id) != null)
                    sabrLog("Cuepoint $id stopped")
                continue
            }

            val endMs = info.timeRange?.takeIf { it.timescale > 0 }?.let {
                (it.startTicks + it.durationTicks) * 1000 / it.timescale
            } ?: 0
            val expiryMs = maxOf(endMs, (cuepoint.offsetSec + cuepoint.durationSec).toLong() * 1000)

            if (adCuepoints.put(id, expiryMs) == null) {
                sabrLog("Cuepoint $id (${cuepoint.type}, ${cuepoint.event}, ${cuepoint.durationSec}s)")
            }
        }
    }

    private fun expireCuepoints(positionMs: Long) {
        if (adCuepoints.isEmpty()) return
        val iterator = adCuepoints.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value in 1 until positionMs) {
                sabrLog("Cuepoint ${entry.key} has passed (${entry.value}ms < ${positionMs}ms); dropping it")
                iterator.remove()
            }
        }
    }

    private fun clearSeekIfLanded(landedAfterSeek: Boolean) {
        val pending = seekPendingUs ?: return
        if (!landedAfterSeek) return
        liveLog("SabrSeek to ${pending / 1000}ms satisfied by fresh media; resuming")
        seekPendingUs = null
    }

    private fun consume(reader: UmpReader, requestedPositionUs: Long, requestedKeys: Set<SabrFormatKey> = emptySet()): Boolean {
        val pending = HashMap<Int, SabrSegment>()
        var redirect: String? = null
        var seekToUs: Long? = null
        val partCounts = LinkedHashMap<Int, Int>()

        try {
            while (!released) {
                val part = reader.next() ?: break
                partCounts[part.type] = (partCounts[part.type] ?: 0) + 1
                when (part.type) {
                    UmpPartType.MEDIA_HEADER -> onMediaHeader(MediaHeader.parseFrom(part.data), pending, requestedKeys)

                    UmpPartType.MEDIA -> {
                        val (headerId, offset) = UmpReader.decodeVarInt(part.data, 0)
                        mediaBytes += (part.data.size - offset).toLong()
                        pending[headerId.toInt()]?.let { segment ->
                            segment.append(part.data, offset, part.data.size - offset)
                            bufferFor(segment.formatKey).notifyChanged()
                        }
                    }

                    UmpPartType.MEDIA_END -> {
                        val (headerId, _) = UmpReader.decodeVarInt(part.data, 0)
                        pending.remove(headerId.toInt())?.let { segment ->
                            if (segment.contentLength > 0 && segment.size != segment.contentLength) {
                                sabrLog("Dropping truncated seq=${segment.sequenceNumber} itag=${segment.formatKey.itag} got=${segment.size} want=${segment.contentLength}")
                                bufferFor(segment.formatKey).discard(segment)
                            } else {
                                segment.markComplete()
                                bufferFor(segment.formatKey).notifyChanged()
                                onSegmentsChanged?.run()
                            }
                        }
                    }

                    UmpPartType.NEXT_REQUEST_POLICY -> onNextRequestPolicy(NextRequestPolicy.parseFrom(part.data))

                    UmpPartType.FORMAT_INITIALIZATION_METADATA -> {
                        val metadata = FormatInitializationMetadata.parseFrom(part.data)
                        val key = SabrFormatKey.of(metadata.formatId.itag, metadata.formatId.lmt, metadata.formatId.xtags)
                        formatInitialization[key] = metadata
                        sabrLog("FormatInit itag=${metadata.formatId.itag} mime='${metadata.mimeType}' " +
                            "initRange=${metadata.initRange.start}-${metadata.initRange.end} " +
                            "indexRange=${metadata.indexRange.start}-${metadata.indexRange.end} " +
                            "endSeg=${metadata.endSegmentNumber} endMs=${metadata.endTimeMs}")
                        listener?.onFormatInitialization(metadata)
                    }

                    UmpPartType.LIVE_METADATA -> {
                        val metadata = LiveMetadata.parseFrom(part.data)
                        val first = !reestimated
                        reestimated = true
                        liveMetadata = metadata
                        liveMetadataAtMs = System.currentTimeMillis()

                        if (first) reestimateInexactDurations()
                        if (isLive) {
                            val minS = if (metadata.minSeekableTimescale > 0) metadata.minSeekableTimeTicks.toDouble() / metadata.minSeekableTimescale else Double.NaN
                            val maxS = if (metadata.maxSeekableTimescale > 0) metadata.maxSeekableTimeTicks.toDouble() / metadata.maxSeekableTimescale else Double.NaN
                            liveLog("LiveMetadata headSeq=${metadata.headSequenceNumber} headTimeMs=${metadata.headSequenceTimeMs} " +
                                "minSeekable=${metadata.minSeekableTimeTicks}/${metadata.minSeekableTimescale} (${"%.1f".format(minS)}s) " +
                                "maxSeekable=${metadata.maxSeekableTimeTicks}/${metadata.maxSeekableTimescale} (${"%.1f".format(maxS)}s)")

                            if (!mediaBaseSet && metadata.minSeekableTimescale > 0) {
                                mediaBaseUs = metadata.minSeekableTimeTicks * 1_000_000L / metadata.minSeekableTimescale
                                mediaBaseSet = true
                                liveLog("mediaBaseUs=${mediaBaseUs / 1000}ms (head ${metadata.headSequenceTimeMs}ms)")
                            }
                        }
                        listener?.onLiveMetadata(metadata)
                    }

                    UmpPartType.SABR_CONTEXT_UPDATE -> {
                        val update = SabrContextUpdate.parseFrom(part.data)
                        sabrLog("SabrContextUpdate type=${update.type} scope=${update.scope} " +
                            "sendByDefault=${update.sendByDefault} valueBytes=${update.value.size()}")

                        sabrContexts[update.type] = SabrContext.newBuilder()
                            .setType(update.type)
                            .setValue(update.value)
                            .build()

                        if (update.sendByDefault) activeSabrContexts.add(update.type)
                    }

                    UmpPartType.SABR_CONTEXT_SENDING_POLICY -> {
                        val policy = SabrContextSendingPolicy.parseFrom(part.data)
                        sabrLog("SabrContextSendingPolicy start=${policy.startPolicyList} " +
                            "stop=${policy.stopPolicyList} discard=${policy.discardPolicyList}")

                        activeSabrContexts.addAll(policy.startPolicyList)
                        activeSabrContexts.removeAll(policy.stopPolicyList.toSet())
                        for (type in policy.discardPolicyList) {
                            sabrContexts.remove(type)
                            activeSabrContexts.remove(type)
                        }
                    }

                    UmpPartType.STREAM_PROTECTION_STATUS -> {
                        val status = StreamProtectionStatus.parseFrom(part.data).status
                        sabrLog("StreamProtectionStatus=$status rn=${requestNumber.get()} " +
                            "sentContexts=${activeSabrContexts.sorted()} heldContexts=${sabrContexts.keys.sorted()} " +
                            "cookie=${playbackCookie?.size() ?: 0}b " +
                            "poTokenBytes=${resolvePoToken()?.size() ?: 0}")
                        if (status == PROTECTION_STATUS_ATTESTATION_REQUIRED)
                            throw SabrBlockedException("po token rejected (attestation required)")
                    }

                    UmpPartType.SABR_REDIRECT -> redirect = SabrRedirect.parseFrom(part.data).url

                    UmpPartType.SABR_SEEK -> {
                        val seek = com.futo.platformplayer.sabr.proto.SabrSeek.parseFrom(part.data)
                        val scale = seek.seekMediaTimescale
                        seekToUs = if (scale > 0) seek.seekMediaTime * 1_000_000L / scale else null
                        liveLog("SabrSeek to ${seek.seekMediaTime}/$scale (${(seekToUs ?: 0) / 1000}ms) " +
                            "source=${seekSourceName(seek.seekSource)}")
                    }

                    UmpPartType.SABR_ERROR -> {
                        val error = com.futo.platformplayer.sabr.proto.SabrError.parseFrom(part.data)
                        throw SabrException("SABR error ${error.code} ${error.type}")
                    }

                    UmpPartType.RELOAD_PLAYER_RESPONSE ->
                        throw SabrReloadRequiredException("Server asked for a fresh player response")

                    UmpPartType.SNACKBAR_MESSAGE -> {
                        sabrLog("Snackbar message id=${SnackbarMessage.parseFrom(part.data).id} (ignored)")
                    }

                    UmpPartType.CUEPOINT_LIST -> onCuepointList(CuepointList.parseFrom(part.data))
                }
            }
        } finally {
            sabrLog("Response rn=${requestNumber.get()} parts=" +
                partCounts.entries.joinToString(",") { "${UmpPartType.name(it.key)}x${it.value}" })

            for (segment in pending.values) {
                val buffer = bufferFor(segment.formatKey)
                buffer.discard(segment)
                buffer.notifyChanged()
            }
        }

        if (redirect != null) {
            Logger.i(TAG, "SABR redirect issued")
            streamingUrl = redirect
            consecutiveRedirects++
            if (consecutiveRedirects >= MAX_REDIRECTS)
                throw SabrException("SABR redirected $consecutiveRedirects times without delivering media")
            backoffUntilMs = serverBackoffUntilMs
            resumePositionUs = requestedPositionUs
            return true
        }

        seekToUs?.let { applySabrSeek(it, requestedPositionUs) }
        return false
    }

    private fun onMediaHeader(header: MediaHeader, pending: HashMap<Int, SabrSegment>, requestedKeys: Set<SabrFormatKey>) {
        val key = SabrFormatKey.of(header.itag, header.lmt, header.xtags)
        val buffer = bufferFor(key)

        if (key in requestedKeys) {
            demandedHeaders++
            adoptServerFormat(key)
        }
        else if (key !in demandedKeys) foreignHeaders++

        val existing = if (header.isInitSegment) buffer.initSegment else buffer.get(header.sequenceNumber)
        if (existing != null && existing.isComplete) {
            pending[header.headerId] = SabrSegment(key, header.sequenceNumber, header.isInitSegment, 0, 0, 0)
            return
        }

        val timescale = header.timeRange.timescale
        val startUs: Long
        var durationUs: Long
        if (timescale > 0) {
            startUs = ticksToUs(header.timeRange.startTicks, timescale)
            durationUs = ticksToUs(header.timeRange.durationTicks, timescale)
        } else {
            startUs = header.startMs * 1000
            durationUs = header.durationMs * 1000
        }
        val exact = durationUs > 0
        if (!header.isInitSegment) {
            backPatchPrevious(buffer, header.sequenceNumber, startUs)
            if (!exact) durationUs = estimateSegmentUs(header.sequenceNumber, startUs, buffer)

            val clock = activeFormat(ROLE_VIDEO) ?: activeFormat(ROLE_AUDIO)
            if (clock != null && clock.key == key) mediaUsDelivered += durationUs
        }

        val segment = SabrSegment(
            key,
            header.sequenceNumber,
            header.isInitSegment,
            startUs,
            durationUs,
            header.contentLength.toInt(),
            if (timescale > 0) header.timeRange.startTicks else 0,
            timescale
        )
        if (exact) segment.setDuration(durationUs, exact = true)
        pending[header.headerId] = segment
        buffer.announce(segment)

        sabrLog("MediaHeader itag=${header.itag} seq=${header.sequenceNumber} init=${header.isInitSegment} " +
            "startMs=${startUs / 1000} durMs=${durationUs / 1000} tr=${header.timeRange.startTicks}/${header.timeRange.durationTicks}@${header.timeRange.timescale} " +
            "hdrStartMs=${header.startMs} hdrDurMs=${header.durationMs} len=${header.contentLength}")
    }

    private fun estimateSegmentUs(sequence: Int, startUs: Long, buffer: SabrTrackBuffer): Long {
        val cadence = liveCadenceUs(buffer)
        if (cadence > 0) return cadence

        val lm = liveMetadata
        if (lm != null && lm.headSequenceNumber > sequence) {
            val segments = lm.headSequenceNumber - sequence
            val spanUs = lm.headSequenceTimeMs * 1000 - startUs
            if (spanUs > 0) return (spanUs / segments).coerceAtLeast(1)
        }

        val prev = buffer.get(sequence - 1)
        if (prev != null && prev.durationUs > 0) return prev.durationUs

        return DEFAULT_LIVE_SEGMENT_US
    }

    private fun liveCadenceUs(buffer: SabrTrackBuffer): Long {
        val observed = buffer.recentStartDeltasUs(8)
        if (observed.isNotEmpty()) return observed.sorted()[observed.size / 2].coerceAtLeast(1)

        val lm = liveMetadata ?: return -1
        val anchor = buffer.firstAtOrAfter(-1) ?: return -1
        if (lm.headSequenceNumber <= anchor.sequenceNumber) return -1

        val spanUs = lm.headSequenceTimeMs * 1000 - anchor.startUs
        if (spanUs <= 0) return -1
        return (spanUs / (lm.headSequenceNumber - anchor.sequenceNumber)).coerceAtLeast(1)
    }

    private fun backPatchPrevious(buffer: SabrTrackBuffer, sequence: Int, startUs: Long) {
        val prev = buffer.get(sequence - 1) ?: return
        if (prev.durationExact) return

        val deltaUs = startUs - prev.startUs
        if (deltaUs <= 0) return

        val cadence = liveCadenceUs(buffer).takeIf { it > 0 } ?: prev.durationUs
        if (cadence > 0 && deltaUs > cadence * 3 / 2) return

        prev.setDuration(deltaUs, exact = true)
    }

    @Volatile private var reestimated = false

    private fun reestimateInexactDurations() {
        for (buffer in buffers.values) {
            for (segment in buffer.snapshot()) {
                if (segment.durationExact || segment.isInit) continue
                buffer.get(segment.sequenceNumber + 1)?.let { backPatchPrevious(buffer, it.sequenceNumber, it.startUs) }
                if (segment.durationExact) continue
                segment.setDuration(estimateSegmentUs(segment.sequenceNumber, segment.startUs, buffer), exact = false)
            }
        }
    }

    private fun onNextRequestPolicy(policy: NextRequestPolicy) {
        if (!policy.playbackCookie.isEmpty) playbackCookie = policy.playbackCookie
        if (policy.targetVideoReadaheadMs > 0) targetVideoReadaheadMs = policy.targetVideoReadaheadMs.toLong()
        if (policy.targetAudioReadaheadMs > 0) targetAudioReadaheadMs = policy.targetAudioReadaheadMs.toLong()
        if (policy.backoffTimeMs > 0) {
            sabrLog("SERVER BACKOFF ${policy.backoffTimeMs}ms at rn=${requestNumber.get()} " +
                "sentContexts=${activeSabrContexts.sorted()} heldContexts=${sabrContexts.keys.sorted()} " +
                "cookie=${playbackCookie?.size() ?: 0}b poTokenBytes=${resolvePoToken()?.size() ?: 0}")
            serverBackoffUntilMs = System.currentTimeMillis() + policy.backoffTimeMs
            backoffUntilMs = maxOf(backoffUntilMs, serverBackoffUntilMs)
        }
    }

    private fun buildRequest(video: SabrFormat?, audio: SabrFormat?, positionMs: Long): VideoPlaybackAbrRequest {
        val now = System.currentTimeMillis()
        val abrState = ClientAbrState.newBuilder()
            .setPlayerTimeMs(positionMs)
            .setBandwidthEstimate(bandwidthEstimate())
            .setNetworkLatencyMs(Random.nextLong(7, 97))
            .setTimeSinceLastActionMs(now - lastActionMs)
            .setTimeSinceLastManualFormatSelectionMs(now - createdAtMs)
            .setLastManualDirection(0)
            .setDrcEnabled(true)
            .setVisibility(0)
            .setPreferVp9(false)

        if (lastRequestMs > 0) abrState.setTimeSinceLastRequestMs(now - lastRequestMs)

        val videoAlternates = videoDemand?.alternates.orEmpty()
        val audioAlternates = audioDemand?.alternates.orEmpty()

        if (video != null) {
            val cap = videoAlternates.maxByOrNull { it.height } ?: video
            abrState.setClientViewportWidth((if (viewportWidth > 0) viewportWidth else cap.width).toLong())
            abrState.setClientViewportHeight((if (viewportHeight > 0) viewportHeight else cap.height).toLong())

            if (videoAlternates.size <= 1) {
                abrState.setLastManualSelectedResolution(video.height.toLong())
                abrState.setStickyResolution(video.height.toLong())
                abrState.setSelectedQualityHeight(video.height.toLong())
            }

            if (audio == null) abrState.setEnabledTrackTypesBitfield(MediaType.MEDIA_TYPE_VIDEO)
        } else if (audio != null) {
            abrState.setEnabledTrackTypesBitfield(MediaType.MEDIA_TYPE_AUDIO)
        }

        val streamerContext = StreamerContext.newBuilder()
            .setClientInfo(clientInfo)
            .addAllSabrContexts(sabrContexts.filterKeys { it in activeSabrContexts }.values)
            .addAllUnsentSabrContexts(sabrContexts.keys.filter { it !in activeSabrContexts })

        resolvePoToken()?.let { streamerContext.setPoToken(it) }
        playbackCookie?.let { streamerContext.setPlaybackCookie(it) }

        val request = VideoPlaybackAbrRequest.newBuilder()
            .setClientAbrState(abrState)
            .setVideoPlaybackUstreamerConfig(ByteString.copyFrom(ustreamerConfig))
            .setStreamerContext(streamerContext)

        expireCuepoints(positionMs)
        for (id in adCuepoints.keys) {
            request.addAdCuepoints(AdCuepointConfig.newBuilder()
                .setCuepointId(id)
                .setMagicValue(AD_CUEPOINT_MAGIC))
        }

        if (videoAlternates.isEmpty()) video?.let { request.addPreferredVideoFormatIds(it.toFormatId()) }
        else videoAlternates.forEach { request.addPreferredVideoFormatIds(it.toFormatId()) }

        if (audioAlternates.isEmpty()) audio?.let { request.addPreferredAudioFormatIds(it.toFormatId()) }
        else audioAlternates.forEach { request.addPreferredAudioFormatIds(it.toFormatId()) }

        val held = (videoAlternates + audioAlternates).ifEmpty { listOfNotNull(video, audio) }
        for (format in held) {
            if (!formatInitialization.containsKey(format.key)) continue
            if (bufferFor(format).segmentCount == 0) continue
            request.addSelectedFormatIds(format.toFormatId())

            val buffer = bufferFor(format)
            val fromUs = demandFromUs(format)
            val lastSequence = buffer.lastCompletedSequence(fromUs)
            if (lastSequence < 0) continue

            val range = BufferedRange.newBuilder()
                .setFormatId(format.toFormatId())
                .setEndSegmentIndex(lastSequence.toLong())

            val startSeq = firstSequenceOfRun(buffer, fromUs)
            val startUs = buffer.get(startSeq)?.startUs ?: continue
            val exactEnd = if (isLive) buffer.exactEndFromSequence(startSeq) else Long.MIN_VALUE
            val end = if (exactEnd != Long.MIN_VALUE) exactEnd else buffer.bufferedEndUs(fromUs)
            val durationUs = if (end == Long.MIN_VALUE) 0 else (end - startUs).coerceAtLeast(0)
            range.setStartSegmentIndex(startSeq.toLong())
                .setStartTimeMs(startUs / 1000)
                .setDurationMs(durationUs / 1000)

            val first = buffer.get(startSeq)
            if (first != null && first.timescale > 0) {
                range.setTimeRange(
                    com.futo.platformplayer.sabr.proto.TimeRange.newBuilder()
                        .setStartTicks(first.startTicks)
                        .setDurationTicks(usToTicks(durationUs, first.timescale))
                        .setTimescale(first.timescale)
                )
            }
            request.addBufferedRanges(range.build())
        }

        return request.build()
    }


    private fun clampToSeekableWindow() {
        val lm = liveMetadata ?: return
        if (seekPendingUs != null) return

        val minScale = lm.minSeekableTimescale
        val maxScale = lm.maxSeekableTimescale
        if (minScale <= 0 || maxScale <= 0) return

        val windowStartUs = lm.minSeekableTimeTicks * 1_000_000L / minScale
        val windowEndUs = lm.maxSeekableTimeTicks * 1_000_000L / maxScale
        val position = playbackPositionUs

        if (position < windowStartUs) {
            liveLog("Position ${position / 1000}ms fell below the seekable window; moving to its start " +
                "${windowStartUs / 1000}ms")
            applySabrSeek(windowStartUs, position)
            return
        }

        if (position > windowEndUs + SABR_SEEK_SLACK_US) {
            liveLog("Position ${position / 1000}ms is past the servable head; pulling back to " +
                "${windowEndUs / 1000}ms")
            applySabrSeek(windowEndUs, position)
        }
    }

    private fun firstSequenceOfRun(buffer: SabrTrackBuffer, fromUs: Long): Int {
        val last = buffer.lastCompletedSequence(fromUs)
        if (last < 0) return buffer.lowestSequence.coerceAtLeast(1)
        var first = last
        while (first > 0 && buffer.get(first - 1)?.isComplete == true) first--
        return first.coerceAtLeast(0)
    }

    private fun resolvePoToken(): ByteString? {
        if (poTokenResolved) return poTokenDecoded
        poTokenResolved = true

        val token = poTokenRaw
        if (token == null) {
            sabrLog("poToken: none set on source")
            return null
        }

        poTokenDecoded = decodeBase64Lenient(token)?.let { ByteString.copyFrom(it) }
        if (poTokenDecoded == null)
            Logger.e(TAG, "Po token is not valid base64; requests will be unattested and YouTube will " +
                "block the session once the attestation grace period expires")

        sabrLog("poToken: len=${token.length} decodedBytes=${poTokenDecoded?.size() ?: 0} prefix='${token.take(12)}'")
        return poTokenDecoded
    }

    private fun decodeBase64Lenient(value: String): ByteArray? {
        val flags = Base64.NO_WRAP or Base64.NO_PADDING
        try {
            return Base64.decode(value, Base64.URL_SAFE or flags)
        } catch (_: Throwable) {
        }
        return try {
            Base64.decode(value.replace('-', '+').replace('_', '/'), Base64.DEFAULT)
        } catch (ex: Throwable) {
            Logger.w(TAG, "Not valid base64 in either alphabet: '${value.take(16)}...' (${value.length} chars)", ex)
            null
        }
    }

    private fun appendRequestNumber(url: String): String {
        val separator = if (url.contains('?')) '&' else '?'
        return "$url${separator}rn=${requestNumber.incrementAndGet()}"
    }


    companion object {
        private const val TAG = "SabrSession"

        const val TAG_LIVE = "SABRLIVE"
        fun liveLog(msg: String) { android.util.Log.i(TAG_LIVE, msg) }
        fun sabrLog(msg: String) { android.util.Log.i(TAG_LIVE, msg) }

        const val ROLE_VIDEO = 0
        const val ROLE_AUDIO = 1

        private const val LIVE_POLL_MS = 1_000L
        private const val DEFAULT_LIVE_SEGMENT_US = 5_000_000L

        private const val MICROS_PER_SECOND = 1_000_000L

        private fun ticksToUs(ticks: Long, timescale: Int): Long =
            ticks / timescale * MICROS_PER_SECOND + (ticks % timescale) * MICROS_PER_SECOND / timescale

        private fun usToTicks(us: Long, timescale: Int): Long =
            us / MICROS_PER_SECOND * timescale + (us % MICROS_PER_SECOND) * timescale / MICROS_PER_SECOND
        private const val DEFAULT_READAHEAD_MS = 20_000L
        private const val PUMP_IDLE_POLL_MS = 250L
        private const val ERROR_BACKOFF_BASE_MS = 1_000L

        private const val LIVE_HEAD_PLAYER_TIME_US = 9_007_199_254_740_991L * 1000L
        private const val SLOW_REQUEST_LOG_MS = 2_000L

        private const val STARVED_US = 3_000_000L

        private const val SABR_SEEK_SLACK_US = 30_000_000L
        private const val ERROR_BACKOFF_MAX_MS = 30_000L
        private const val ERROR_BACKOFF_MAX_SHIFT = 5
        private const val MAX_CONSECUTIVE_ERRORS = 4
        private const val DEFAULT_KEEP_BEHIND_US = 30_000_000L
        private const val AD_CUEPOINT_MAGIC = 11
        private const val RESTART_TOLERANCE_US = 1_000_000L
        private const val FRONTIER_EPSILON_US = 1_000_000L
        private const val NO_PROGRESS_THRESHOLD = 2
        private const val MAX_EMPTY_RESPONSES = 8
        private const val MAX_SUBSTITUTED_RESPONSES = 4
        private const val MAX_REDIRECTS = 3

        private val liveSessions = AtomicInteger(0)
        private const val EMPTY_BACKOFF_BASE_MS = 500L
        private const val BANDWIDTH_ESTIMATE = 104857L

        private const val THROUGHPUT_MIN_BYTES = 65_536L
        private const val THROUGHPUT_SMOOTHING = 30L

        private const val THROUGHPUT_MIN_SPEEDUP = 2
        private const val PROTECTION_STATUS_ATTESTATION_REQUIRED = 3
        private const val SNACKBAR_PLAYBACK_BLOCKED = 1
    }
}
