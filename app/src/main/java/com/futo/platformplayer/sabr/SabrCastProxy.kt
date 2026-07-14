package com.futo.platformplayer.sabr

import kotlin.concurrent.thread
import com.futo.platformplayer.logging.Logger
import java.util.TreeMap

class SabrCastProxy(
    private val session: SabrSession,
    private val video: SabrFormat?,
    private val audio: SabrFormat?
) {

    val isLive: Boolean get() = session.isLive
    val videoId: String get() = session.videoId

    @Volatile private var videoFirstSeq = 0
    @Volatile private var audioFirstSeq = 0
    @Volatile private var videoLastSeq = Int.MAX_VALUE
    @Volatile private var audioLastSeq = Int.MAX_VALUE
    @Volatile private var videoSegMs = 0L
    @Volatile private var audioSegMs = 0L
    @Volatile private var videoTiming: SidxTiming? = null
    @Volatile private var audioTiming: SidxTiming? = null
    @Volatile private var videoTimeline: CastTimeline? = null
    @Volatile private var audioTimeline: CastTimeline? = null

    @Volatile private var durationMs = 0L
    val durationSeconds: Double get() = durationMs / 1000.0

    @Volatile private var liveAnchorWallMs = 0L
    @Volatile private var liveAnchorMediaMs = 0L
    @Volatile private var liveEpochMs = 0L

    @Volatile private var lastGoodManifest: String? = null
    @Volatile private var lastGoodManifestAtMs = 0L

    private val videoAnchors = TreeMap<Int, Long>()
    private val audioAnchors = TreeMap<Int, Long>()

    @Volatile private var videoPosUs = 0L
    @Volatile private var audioPosUs = 0L

    private val seekLock = Any()
    private var videoSeekUs = -1L
    private var audioSeekUs = -1L
    private var videoSeekAtMs = 0L
    private var audioSeekAtMs = 0L
    private var lastSeekUs = Long.MIN_VALUE
    private var lastSeekAtMs = 0L

    private var videoInit: ByteArray? = null
    private var audioInit: ByteArray? = null

    var onBackoff: ((delayMs: Long?) -> Unit)? = null
    var onFatalError: ((Throwable) -> Unit)? = null


    var onReceiverLost: (() -> Unit)? = null

    @Volatile private var lastRecoverAtMs = 0L

    var playheadUs: (() -> Long?)? = null

    @Volatile private var rateAnchorUs = Long.MIN_VALUE
    @Volatile private var rateAnchorAtMs = 0L
    @Volatile private var measuredRate = Double.NaN
    @Volatile private var lastPlayheadUs = Long.MIN_VALUE
    @Volatile private var lastPlayheadAtMs = 0L
    @Volatile private var lastSlipMs = 0L
    @Volatile private var jumps = 0
    @Volatile private var lastHealthLogMs = 0L
    @Volatile private var lastManifestAtMs = 0L

    @Volatile private var released = false

    @Volatile private var loggedVideoBoxes = false
    @Volatile private var loggedAudioBoxes = false

    private val isDead: Boolean get() = released || session.isReleased || session.fatalError != null

    private val sessionListener = object : SabrSessionListener {
        override fun onLiveMetadata(metadata: com.futo.platformplayer.sabr.proto.LiveMetadata) {
            planner.onLiveEdge(metadata.headSequenceNumber)
        }
        override fun onFormatInitialization(metadata: com.futo.platformplayer.sabr.proto.FormatInitializationMetadata) {}
        override fun onSessionError(error: Throwable) {
            val fatal = session.fatalError ?: return
            if (released) return
            Logger.e(TAG, "SABR cast session failed fatally", fatal)
            onFatalError?.invoke(fatal)
        }
        override fun onBackoff(delayMs: Long) { this@SabrCastProxy.onBackoff?.invoke(delayMs) }
        override fun onBackoffEnded() { this@SabrCastProxy.onBackoff?.invoke(null) }
    }

    init {
        session.keepBehindUs = if (isLive) liveKeepBehindUs() else VOD_KEEP_BEHIND_US
        if (isLive) session.minReadaheadMs = LIVE_MIN_READAHEAD_MS
        session.setListener(sessionListener)
    }

    fun prepare(resumeUs: Long = 0): Boolean {
        if (video == null && audio == null) return false

        session.start()

        durationMs = if (session.durationUs > 0) session.durationUs / 1000 else 0

        var from = if (isLive) 0L else resumeUs.coerceAtLeast(0)

        val tailGuardUs = 4 * DEFAULT_SEG_MS * 1000
        if (!isLive && session.durationUs > 0)
            from = from.coerceAtMost((session.durationUs - tailGuardUs).coerceAtLeast(0))

        if (!demandFrom(from)) return false

        if (isLive && !awaitLiveMetadata()) {
            Logger.w(TAG, "No live metadata within the prepare timeout; refusing to cast")
            return false
        }

        readTimings()

        if (!isLive) {
            if (from > 0 && firstSequences() == null) {
                Logger.w(TAG, "Could not establish the segment numbering from ${from / 1000}ms; restarting at 0")
                from = 0
                session.restart(0, force = true)
                if (!demandFrom(0)) return false
                readTimings()
            }

            val firstSeqs = firstSequences()
            if (firstSeqs == null) {
                Logger.e(TAG, "Could not establish the segment numbering even from 0; refusing to cast")
                return false
            }
            videoFirstSeq = firstSeqs.first
            audioFirstSeq = firstSeqs.second
        } else {
            video?.let { videoFirstSeq = session.bufferFor(it).lowestSequence.coerceAtLeast(0) }
            audio?.let { audioFirstSeq = session.bufferFor(it).lowestSequence.coerceAtLeast(0) }
        }
        video?.let { videoLastSeq = lastSeqFor(it, videoFirstSeq, videoTiming) }
        audio?.let { audioLastSeq = lastSeqFor(it, audioFirstSeq, audioTiming) }

        if (isLive) session.liveMetadata?.let { lm ->
            val segMs = maxOf(videoSegMs, audioSegMs).coerceAtLeast(1)
            val minMs = session.mediaBaseUs / 1000
            val edgeMs = (lm.headSequenceTimeMs - LIVE_START_SEGMENTS * segMs)
                .coerceAtLeast(minMs)
            val ends = listOfNotNull(video, audio).map { session.bufferFor(it).bufferedEndFromFrontUs() }
            val landedMs = if (ends.isEmpty() || ends.any { it == Long.MIN_VALUE }) 0L
                else ends.min() / 1000
            if (edgeMs - landedMs > segMs) {
                val edgeUs = edgeMs * 1000
                video?.let { session.setDemand(SabrSession.ROLE_VIDEO, it, edgeUs) }
                audio?.let { session.setDemand(SabrSession.ROLE_AUDIO, it, edgeUs) }
                session.setPlaybackPosition(edgeUs)
                session.restart(edgeUs, force = true)
                video?.let {
                    val buffer = session.bufferFor(it)
                    if (buffer.awaitAnnounced(-1, PREPARE_TIMEOUT_MS) == null) return false
                    videoFirstSeq = buffer.lowestSequence.coerceAtLeast(0)
                    videoLastSeq = lastSeqFor(it, videoFirstSeq, videoTiming)
                }
                audio?.let {
                    val buffer = session.bufferFor(it)
                    if (buffer.awaitAnnounced(-1, PREPARE_TIMEOUT_MS) == null) return false
                    audioFirstSeq = buffer.lowestSequence.coerceAtLeast(0)
                    audioLastSeq = lastSeqFor(it, audioFirstSeq, audioTiming)
                }
            }
        }

        if (isLive) {
            videoTimeline = CastTimeline(1000)
            audioTimeline = CastTimeline(1000)

            session.liveMetadata?.let { planner.onLiveEdge(it.headSequenceNumber) }
            session.playheadProvider = { reportedPlayheadUs() }


            if (!awaitLiveHead()) {
                Logger.w(TAG, "No live segment completed within the prepare timeout")
                return false
            }

            liveRange()?.let { range ->
                video?.let { fillTimeline(SabrSession.ROLE_VIDEO, range) }
                audio?.let { fillTimeline(SabrSession.ROLE_AUDIO, range) }
            }

            if (video != null && videoTimeline?.isEmpty != false) {
                Logger.w(TAG, "Live video window is empty after prepare")
                return false
            }
            if (audio != null && audioTimeline?.isEmpty != false) {
                Logger.w(TAG, "Live audio window is empty after prepare")
                return false
            }

            val landedMs = listOfNotNull(video, audio)
                .map { session.bufferFor(it).bufferedExactEndUs() }
                .filter { it != Long.MIN_VALUE }
                .minOrNull()?.div(1000) ?: 0
            if (landedMs <= 0) {
                Logger.w(TAG, "Live prepare could not establish a media anchor")
                return false
            }
            liveAnchorMediaMs = landedMs
            liveAnchorWallMs = System.currentTimeMillis()

            liveEpochMs = listOfNotNull(videoTimeline, audioTimeline)
                .filter { !it.isEmpty }
                .mapNotNull { it.startUs(it.firstNumber) }
                .minOrNull()?.div(1000) ?: 0

            val vBuf = video?.let { session.bufferFor(it) }
            val firstSeq = vBuf?.lowestSequence ?: audio?.let { session.bufferFor(it).lowestSequence } ?: -1
            val firstStartMs = (vBuf?.firstAtOrAfter(-1)?.startUs
                ?: audio?.let { session.bufferFor(it).firstAtOrAfter(-1)?.startUs } ?: 0) / 1000
            val lm = session.liveMetadata
            val segMsNow = maxOf(videoSegMs, audioSegMs)
            if (lm != null) {
                SabrSession.liveLog("PREPARE landed firstSeq=$firstSeq firstStartMs=$firstStartMs " +
                    "headSeq=${lm.headSequenceNumber} headTimeMs=${lm.headSequenceTimeMs} " +
                    "=> behind edge by ~${lm.headSequenceNumber - firstSeq} segments " +
                    "(${lm.headSequenceTimeMs - firstStartMs}ms). segMs=$segMsNow " +
                    "anchorMediaMs=$liveAnchorMediaMs")
            } else {
                SabrSession.liveLog("PREPARE landed firstSeq=$firstSeq firstStartMs=$firstStartMs " +
                    "anchorMediaMs=$liveAnchorMediaMs (no LiveMetadata yet). segMs=$segMsNow")
            }
            startLiveWatchdog()
            return true
        }

        video?.let { videoTimeline = buildVodTimeline(it, videoFirstSeq, videoLastSeq, videoTiming) }
        audio?.let { audioTimeline = buildVodTimeline(it, audioFirstSeq, audioLastSeq, audioTiming) }

        videoTimeline?.let { if (!it.isEmpty) videoLastSeq = it.lastNumber }
        audioTimeline?.let { if (!it.isEmpty) audioLastSeq = it.lastNumber }

        val timelineMs = listOfNotNull(videoTimeline, audioTimeline)
            .filter { !it.isEmpty }
            .map { it.totalUs() / 1000 }
            .maxOrNull() ?: 0
        if (timelineMs > 0) durationMs = timelineMs
        if (durationMs <= 0) {
            durationMs = listOfNotNull(video, audio)
                .mapNotNull { session.formatInitializationFor(it)?.endTimeMs }
                .maxOrNull() ?: 0
        }
        return durationMs > 0
    }

    private fun awaitRole(format: SabrFormat): SabrTrackBuffer? {
        val buffer = session.bufferFor(format)
        if (buffer.awaitAnnounced(-1, PREPARE_TIMEOUT_MS) != null) return buffer

        session.fatalError?.let { throw it }

        val requested = setOfNotNull(video?.key, audio?.key)
        val foreign = session.observedFormatKeys().filter {
            it !in requested && session.bufferFor(it).firstAtOrAfter(-1) != null
        }
        if (foreign.isNotEmpty())
            throw SabrFormatSubstitutedException(
                "Requested itag=${format.itag} (lmt=${format.lastModified}) but the server returned " +
                    foreign.joinToString { "itag=${it.itag} lmt=${it.lastModified}" } +
                    ". The plugin's formats are out of sync with the app.")

        return null
    }

    private fun awaitLiveMetadata(): Boolean {
        val deadline = System.currentTimeMillis() + PREPARE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isDead) return false
            if (session.liveMetadata != null) return true
            session.wakePump()
            Thread.sleep(50)
        }
        return session.liveMetadata != null
    }

    private fun awaitLiveHead(): Boolean {
        val roles = listOfNotNull(video, audio)
        if (roles.isEmpty()) return false

        val hardDeadline = System.currentTimeMillis() + PREPARE_TIMEOUT_MS

        val minSegments = LIVE_EVICT_GUARD + 1

        while (System.currentTimeMillis() < hardDeadline) {
            if (isDead) return false
            if (roles.all { completedCount(it) >= minSegments }) break
            session.wakePump()
            Thread.sleep(50)
        }
        if (roles.any { completedCount(it) < minSegments }) return false

        val deepenDeadline = System.currentTimeMillis() + LIVE_DEEPEN_TIMEOUT_MS
        var lastTotal = -1
        while (System.currentTimeMillis() < minOf(deepenDeadline, hardDeadline)) {
            if (isDead) return false
            if (roles.all { completedCount(it) >= LIVE_START_SEGMENTS }) return true

            val total = roles.sumOf { completedCount(it) }
            if (total == lastTotal && roles.all { completedCount(it) >= LIVE_START_SEGMENTS }) break
            lastTotal = total

            session.wakePump()
            Thread.sleep(100)
        }

        val depth = roles.minOf { completedCount(it) }
        if (depth < LIVE_START_SEGMENTS)
            Logger.w(TAG, "Live prepare starting with a shallow window ($depth segments)")
        return true
    }

    private fun completedCount(format: SabrFormat): Int {
        val buffer = session.bufferFor(format)
        val head = buffer.lastCompletedFromFront()
        val low = buffer.lowestSequence
        if (head < 0 || low < 0 || head < low) return 0
        return head - low + 1
    }

    private fun deriveFirstSeq(buffer: SabrTrackBuffer, timing: SidxTiming?, demandedFromUs: Long): Int? {
        val lowest = buffer.lowestSequence.coerceAtLeast(0)
        val front = buffer.firstAtOrAfter(-1)

        val frontIsStart = front != null && front.startUs < (front.durationUs.coerceAtLeast(1)) / 2
        if (timing == null) return if (demandedFromUs <= 0 && frontIsStart) lowest else null

        val anchor = front
        val index = anchor?.let { timing.indexOfStartUs(it.startUs) }
        if (anchor == null || index == null) {
            Logger.w(TAG, "Could not anchor the numbering (seq=${anchor?.sequenceNumber}, " +
                "startMs=${anchor?.startUs?.div(1000)})")
            return if (demandedFromUs <= 0 && frontIsStart) lowest else null
        }

        val firstSeq = anchor.sequenceNumber - index
        if (firstSeq != lowest)
            SabrSession.sabrLog("Anchored firstSeq=$firstSeq from seq=${anchor.sequenceNumber} " +
                "at index=$index (buffer's lowest is $lowest)")
        return firstSeq
    }

    private fun readTimings() {
        video?.let {
            val buffer = session.bufferFor(it)
            if (!isLive) videoTiming = parseInitTiming(it, buffer)
            videoSegMs = measureSegMs(it, buffer, videoTiming)
        }
        audio?.let {
            val buffer = session.bufferFor(it)
            if (!isLive) audioTiming = parseInitTiming(it, buffer)
            audioSegMs = measureSegMs(it, buffer, audioTiming)
        }
        planner.segmentMs = maxOf(videoSegMs, audioSegMs).coerceAtLeast(1)
        if (isLive) session.keepBehindUs = liveKeepBehindUs()
    }

    private fun liveKeepBehindUs(): Long {
        val segMs = maxOf(videoSegMs, audioSegMs).coerceAtLeast(1)
        val windowUs = (LIVE_WINDOW_SEGMENTS + 2) * segMs * 1000
        return maxOf(LIVE_KEEP_BEHIND_US, windowUs + LIVE_MAX_REPORTED_LAG_US)
    }

    private fun firstSequences(): Pair<Int, Int>? {
        var v = 0
        var a = 0
        video?.let {
            v = deriveFirstSeq(session.bufferFor(it), videoTiming, lastDemandedFromUs) ?: return null
        }
        audio?.let {
            a = deriveFirstSeq(session.bufferFor(it), audioTiming, lastDemandedFromUs) ?: return null
        }
        return Pair(v, a)
    }

    @Volatile private var lastDemandedFromUs = 0L

    private fun demandFrom(fromUs: Long): Boolean {
        lastDemandedFromUs = fromUs
        session.setPlaybackPosition(fromUs)
        video?.let { session.setDemand(SabrSession.ROLE_VIDEO, it, fromUs) }
        audio?.let { session.setDemand(SabrSession.ROLE_AUDIO, it, fromUs) }

        video?.let { if (awaitRole(it) == null) return false }
        audio?.let { if (awaitRole(it) == null) return false }
        return true
    }

    private fun buildVodTimeline(format: SabrFormat, firstSeq: Int, lastSeq: Int, timing: SidxTiming?): CastTimeline? {
        if (timing != null && timing.segmentCount > 0)
            return CastTimeline.fromSidx(firstSeq, timing).truncateTo(lastSeq)

        val meta = session.formatInitializationFor(format)
        val endSeq = meta?.endSegmentNumber ?: 0
        val endMs = meta?.endTimeMs ?: 0
        val segMs = if (format.key == video?.key) videoSegMs else audioSegMs
        if (endSeq > 0 && segMs > 0) {
            Logger.w(TAG, "No sidx for itag=${format.itag}, building uniform timeline from endSegment=$endSeq endMs=$endMs")
            return CastTimeline.uniform(firstSeq, endSeq, segMs, endMs)
        }

        Logger.w(TAG, "No sidx and no endSegmentNumber for itag=${format.itag}; falling back to duration-based template")
        return null
    }

    private val planner = LiveCastPlanner(LiveCastPlanner.Config(
        windowSegments = LIVE_WINDOW_SEGMENTS,
        maxLagSegments = LIVE_MAX_LAG_SEGMENTS,
        minStartSegments = LIVE_MIN_START_SEGMENTS
    ))

    private fun receiverPlayheadUs(): Long {
        if (!isLive) return Long.MIN_VALUE
        val reported = playheadUs?.invoke()?.takeIf { it >= 0 } ?: return Long.MIN_VALUE

        val windowStartUs = windowFloorUs()
        if (windowStartUs <= 0) return Long.MIN_VALUE
        return windowStartUs + reported
    }

    private fun reportedPlayheadUs(): Long {
        val head = session.liveMetadata?.takeIf { it.headSequenceTimeMs > 0 }?.headSequenceTimeMs
            ?: return receiverPlayheadUs()
        val headUs = head * 1000L

        val playhead = receiverPlayheadUs().takeIf { it != Long.MIN_VALUE && it <= headUs } ?: Long.MIN_VALUE
        val floor = headUs - LIVE_MAX_REPORTED_LAG_US

        if (playhead == Long.MIN_VALUE) return floor
        return maxOf(playhead, floor).coerceAtMost(headUs)
    }

    private fun publishReceiverPlayhead() {
        val playhead = receiverPlayheadUs()
        if (playhead != Long.MIN_VALUE) session.setPlaybackPosition(playhead)
    }

    private fun noteReceiverRequest(role: Int, sequence: Int) {
        if (!isLive) return

        val format = formatFor(role) ?: return
        val head = session.bufferFor(format).highestSequence
        if (sequence < 0 || (head >= 0 && sequence > head + 2 * LIVE_READAHEAD_SEGMENTS)) {
            Logger.w(TAG, "Ignoring an implausible receiver request role=$role seq=$sequence (head=$head)")
            return
        }

        planner.noteRequest(role, sequence)
        publishReceiverPlayhead()
        session.wakePump()
    }

    private fun trackFor(role: Int): LiveCastPlanner.Track? {
        val format = formatFor(role) ?: return null
        val run = session.bufferFor(format).publishableRun() ?: return null
        return LiveCastPlanner.Track(run.first, run.last)
    }

    private fun fillTimeline(role: Int, range: IntRange): Boolean {
        val format = formatFor(role) ?: return false
        val timeline = timelineFor(role) ?: return false
        val buffer = session.bufferFor(format)

        for (sequence in range) {
            val segment = buffer.get(sequence) ?: return false
            if (!segment.isComplete || !segment.durationExact) return false

            val startMs = (segment.startUs + 500) / 1000
            val next = buffer.get(sequence + 1)
            val endMs = if (next != null) (next.startUs + 500) / 1000
                else startMs + ((segment.durationUs + 500) / 1000)
            timeline.put(sequence, startMs, (endMs - startMs).coerceAtLeast(1))
        }
        return true
    }

    private fun timelineFor(role: Int): CastTimeline? =
        if (role == SabrSession.ROLE_VIDEO) videoTimeline else audioTimeline

    private fun parseInitTiming(format: SabrFormat, buffer: SabrTrackBuffer): SidxTiming? {
        val init = awaitInit(buffer) ?: return null
        val bytes = init.toByteArray()

        val webm = format.containerMimeType.lowercase().let { it.contains("webm") || it.contains("matroska") }
        val timing = try {
            if (webm) WebmCuesParser.parse(bytes) else Mp4SidxParser.parse(bytes)
        } catch (ex: Throwable) {
            Logger.w(TAG, "Failed to parse the ${if (webm) "WebM Cues" else "MP4 sidx"} for itag=${format.itag}", ex)
            null
        }

        if (timing == null)
            Logger.w(TAG, "No ${if (webm) "Cues" else "sidx"} in the init for itag=${format.itag}; " +
                "falling back to a uniform timeline")
        else
            SabrSession.sabrLog("Timeline for itag=${format.itag}: ${timing.segmentCount} segments " +
                "@${timing.timescale} from ${if (webm) "WebM Cues" else "MP4 sidx"}")

        return timing
    }

    private fun lastSeqFor(format: SabrFormat, firstSeq: Int, timing: SidxTiming?): Int {
        if (isLive) return Int.MAX_VALUE

        val end = session.formatInitializationFor(format)?.endSegmentNumber ?: 0
        if (timing == null) return if (end > 0) end else Int.MAX_VALUE

        val fromTiming = firstSeq + timing.segmentCount - 1
        if (end > 0 && end != fromTiming)
            Logger.w(TAG, "Segment count disagrees with the server for itag=${format.itag}: " +
                "index says last=$fromTiming (first=$firstSeq + ${timing.segmentCount}), server says last=$end")
        return if (end > 0) minOf(end, fromTiming) else fromTiming
    }

    private fun measureSegMs(format: SabrFormat, buffer: SabrTrackBuffer, timing: SidxTiming?): Long {
        val meta = session.formatInitializationFor(format)
        val endMs = meta?.endTimeMs ?: 0

        if (timing != null && timing.segmentCount > 0 && endMs > 0)
            return (endMs / timing.segmentCount).coerceAtLeast(1)

        if (isLive) {
            val lm = session.liveMetadata
            val front = buffer.firstAtOrAfter(-1)
            if (lm != null && front != null) {
                val segments = lm.headSequenceNumber - front.sequenceNumber
                val spanMs = lm.headSequenceTimeMs - front.startUs / 1000
                if (segments > 0 && spanMs > 0) return (spanMs / segments).coerceAtLeast(1)
            }
        }

        val first = buffer.firstAtOrAfter(-1)
        if (first != null && first.durationExact && first.durationUs > 0) return first.durationUs / 1000

        val endSeq = meta?.endSegmentNumber ?: 0
        if (endSeq > 0 && endMs > 0) return (endMs / (endSeq + 1)).coerceAtLeast(1)
        return DEFAULT_SEG_MS
    }

    @Volatile private var lastLiveHeadSeq = -1
    @Volatile private var lastLiveHeadAtMs = 0L

    @Volatile private var liveEndedLatched = false

    private fun liveEnded(): Boolean {
        if (liveEndedLatched) return true

        val head = session.liveMetadata?.headSequenceNumber ?: return false
        val now = System.currentTimeMillis()
        val segMs = maxOf(videoSegMs, audioSegMs).coerceAtLeast(1)
        val stallMs = (segMs * LIVE_END_STALL_SEGMENTS).coerceIn(120_000L, 300_000L)

        val freshMs = maxOf(2 * segMs, 5_000L)
        if (now - session.liveMetadataAtMs > freshMs) {
            lastLiveHeadSeq = head
            lastLiveHeadAtMs = now
            return false
        }

        if (head != lastLiveHeadSeq) {
            lastLiveHeadSeq = head
            lastLiveHeadAtMs = now
            return false
        }
        if (lastLiveHeadAtMs == 0L) {
            lastLiveHeadAtMs = now
            return false
        }
        val ended = now - lastLiveHeadAtMs > stallMs
        if (ended) {
            liveEndedLatched = true
        }
        return ended
    }


    fun servableStartSeconds(): Double? {
        if (!isLive) return null
        val segMs = segmentMs()
        val depthMs = liveWindowDepthMs()

        val targetMs = LIVE_PRESENTATION_SEGMENTS * segMs
        val startMs = (depthMs - targetMs).coerceIn(2 * segMs, maxOf(2 * segMs, depthMs - segMs))
        return startMs / 1000.0
    }

    private fun guardReceiverCushion(publishedEndMs: Long, segMs: Long) {
        if (!isLive || liveEndedLatched) return
        val playheadUs = receiverPlayheadUs()
        if (playheadUs == Long.MIN_VALUE) return

        val now = System.currentTimeMillis()
        val cushionMs = publishedEndMs - playheadUs / 1000
        val targetMs = LIVE_PRESENTATION_SEGMENTS * segMs

        noteReceiverPlayhead(playheadUs, now)
        logCastHealth(publishedEndMs, cushionMs, targetMs, playheadUs, now)
        sampleReceiverRate(playheadUs, now)

        if (cushionMs < LIVE_RECOVER_SEGMENTS * segMs) {
            if (now - lastRecoverAtMs < LIVE_RECOVER_MIN_INTERVAL_MS) return
            lastRecoverAtMs = now
            rateAnchorUs = Long.MIN_VALUE

            Logger.w(TAG, "Receiver has ${cushionMs}ms of media left; seeking it back in")
            onReceiverLost?.invoke()
        }
    }

    private fun segmentMs(): Long = maxOf(videoSegMs, audioSegMs).coerceAtLeast(1)

    private fun startLiveWatchdog() {
        if (!isLive) return
        thread(isDaemon = true, name = "SabrCastWatchdog") {
            while (!released) {
                try { Thread.sleep(LIVE_WATCHDOG_INTERVAL_MS) } catch (_: InterruptedException) { break }
                try { tickLiveWatchdog() } catch (ex: Throwable) { Logger.w(TAG, "Live watchdog tick failed", ex) }
            }
        }
    }

    @Synchronized
    private fun tickLiveWatchdog() {
        if (released || !isLive || liveEndedLatched) return
        val shared = planner.window ?: return
        val publishedEndMs = listOfNotNull(videoTimeline, audioTimeline)
            .mapNotNull { tl -> tl.startUs(shared.last)?.plus(tl.durationUs(shared.last) ?: 0) }
            .minOrNull()?.div(1000) ?: return
        if (publishedEndMs <= 0) return
        guardReceiverCushion(publishedEndMs, segmentMs())
    }

    private fun noteReceiverPlayhead(playheadUs: Long, nowMs: Long) {
        val prevUs = lastPlayheadUs
        val prevAtMs = lastPlayheadAtMs
        lastPlayheadUs = playheadUs
        lastPlayheadAtMs = nowMs
        if (prevUs == Long.MIN_VALUE) return

        val wallMs = nowMs - prevAtMs
        if (wallMs <= 0) return

        lastSlipMs = (playheadUs - prevUs) / 1000 - wallMs
        if (Math.abs(lastSlipMs) < LIVE_PLAYHEAD_JUMP_MS) return

        jumps++
        Logger.w(TAG, "Receiver playhead moved ${lastSlipMs}ms more than ${wallMs}ms of wall time allows " +
            "(jump #$jumps); it did not get there by playing")
    }

    private fun sampleReceiverRate(playheadUs: Long, nowMs: Long) {
        if (rateAnchorUs == Long.MIN_VALUE) {
            rateAnchorUs = playheadUs
            rateAnchorAtMs = nowMs
            return
        }
        val wallMs = nowMs - rateAnchorAtMs
        if (wallMs < LIVE_RATE_SAMPLE_MS) return

        val mediaMs = (playheadUs - rateAnchorUs) / 1000
        rateAnchorUs = playheadUs
        rateAnchorAtMs = nowMs
        measuredRate = if (mediaMs < 0) Double.NaN else mediaMs.toDouble() / wallMs.toDouble()
    }

    private fun logCastHealth(publishedEndMs: Long, cushionMs: Long, targetMs: Long, playheadUs: Long, now: Long) {
        if (now - lastHealthLogMs < LIVE_HEALTH_LOG_INTERVAL_MS) return
        lastHealthLogMs = now

        val edgeLagMs = session.liveMetadata
            ?.takeIf { it.headSequenceTimeMs > 0 }
            ?.let { it.headSequenceTimeMs - publishedEndMs }
            ?: -1
        val rate = if (measuredRate.isNaN()) "?" else String.format(java.util.Locale.US, "%.4f", measuredRate)

        SabrSession.liveLog(
            "cast health: cushion=${cushionMs}ms/${targetMs}ms edgeLag=${edgeLagMs}ms " +
                "head=${publishedEndMs}ms receiver=${playheadUs / 1000}ms " +
                "rate=$rate slip=${lastSlipMs}ms jumps=$jumps " +
                "sinceManifest=${if (lastManifestAtMs == 0L) -1 else now - lastManifestAtMs}ms"
        )
    }

    private fun receiverIsStuck(): Boolean {
        if (!isLive || liveEndedLatched) return false
        val playhead = receiverPlayheadUs()
        if (playhead == Long.MIN_VALUE) return false

        val lm = session.liveMetadata ?: return false
        val minScale = lm.minSeekableTimescale
        if (minScale <= 0) return false
        val windowStartUs = lm.minSeekableTimeTicks * 1_000_000L / minScale

        return playhead < windowStartUs
    }
    @Synchronized
    fun buildManifest(videoInitUrl: String, videoMediaUrl: String, audioInitUrl: String, audioMediaUrl: String, timeUrl: String? = null): String? {
        if (receiverIsStuck()) {
            Logger.w(TAG, "The receiver has fallen out of the bottom of the DVR window; asking it to seek")
            onReceiverLost?.invoke()
            return null
        }

        if (isLive) {
            lastManifestAtMs = System.currentTimeMillis()
        }

        val shared = if (isLive) liveRange() ?: return recentManifestOrNull() else null
        if (shared != null) {
            val filled = listOfNotNull(
                video?.let { fillTimeline(SabrSession.ROLE_VIDEO, shared) },
                audio?.let { fillTimeline(SabrSession.ROLE_AUDIO, shared) }
            )
            if (filled.any { !it }) return recentManifestOrNull()
        }
        val videoWindow = if (isLive && video != null) liveWindow(SabrSession.ROLE_VIDEO, shared!!) ?: return recentManifestOrNull() else null
        val audioWindow = if (isLive && audio != null) liveWindow(SabrSession.ROLE_AUDIO, shared!!) ?: return recentManifestOrNull() else null

        val ended = isLive && liveEnded()

        val livePtoMs = liveEpochMs
        val periodStartMs = 0L

        val videoTemplate = video?.let {
            segmentTemplate(SabrSession.ROLE_VIDEO, videoSegMs, videoFirstSeq, videoTiming, videoInitUrl, videoMediaUrl, livePtoMs, videoWindow)
                ?: return recentManifestOrNull()
        }
        val audioTemplate = audio?.let {
            segmentTemplate(SabrSession.ROLE_AUDIO, audioSegMs, audioFirstSeq, audioTiming, audioInitUrl, audioMediaUrl, livePtoMs, audioWindow)
                ?: return recentManifestOrNull()
        }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")

        if (isLive) {
            val segMs = maxOf(videoSegMs, audioSegMs).coerceAtLeast(1)
            val publishedEndMs = shared?.let { r ->
                listOfNotNull(videoTimeline, audioTimeline)
                    .mapNotNull { tl -> tl.startUs(r.last)?.plus(tl.durationUs(r.last) ?: 0) }
                    .minOrNull()?.div(1000)
            } ?: 0
            val depthMs = liveWindowDepthMs(shared)
            val availIso = toIso8601(availabilityStartMs(publishedEndMs))
            val presentationDelayMs = (LIVE_PRESENTATION_SEGMENTS * segMs).coerceIn(segMs, maxOf(segMs, depthMs - segMs))
            val latencyMinMs = maxOf(LIVE_LATENCY_MIN_MS, presentationDelayMs - 2 * segMs)
                .coerceAtMost(presentationDelayMs)

            sb.append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" profiles=\"urn:mpeg:dash:profile:isoff-live:2011\" ")
            if (ended) {
                val endMs = (publishedEndMs - liveEpochMs).coerceAtLeast(0)
                sb.append("type=\"static\" availabilityStartTime=\"$availIso\" ")
                if (endMs > 0) sb.append("mediaPresentationDuration=\"${toIsoDuration(endMs)}\" ")
                sb.append("minBufferTime=\"PT4S\">\n")
            } else {
                sb.append("type=\"dynamic\" availabilityStartTime=\"$availIso\" ")
                sb.append("minimumUpdatePeriod=\"${toIsoDuration(segMs)}\" ")
                sb.append("timeShiftBufferDepth=\"${toIsoDuration(depthMs)}\" ")
                sb.append("suggestedPresentationDelay=\"${toIsoDuration(presentationDelayMs)}\" ")
                sb.append("minBufferTime=\"PT4S\">\n")

                sb.append("<ServiceDescription id=\"0\">\n")
                sb.append("<Latency target=\"$presentationDelayMs\" min=\"$latencyMinMs\" max=\"$presentationDelayMs\"/>\n")
                sb.append("<PlaybackRate min=\"$LIVE_MIN_PLAYBACK_RATE\" max=\"$LIVE_MAX_PLAYBACK_RATE\"/>\n")
                sb.append("</ServiceDescription>\n")
            }
        } else {
            sb.append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" profiles=\"urn:mpeg:dash:profile:isoff-main:2011\" ")
            sb.append("type=\"static\" mediaPresentationDuration=\"${toIsoDuration(durationMs)}\" minBufferTime=\"PT10S\">\n")
        }
        sb.append("<Period id=\"0\" start=\"${toIsoDuration(periodStartMs)}\">\n")

        if (video != null) {
            sb.append("<AdaptationSet mimeType=\"${video.containerMimeType}\" contentType=\"video\" segmentAlignment=\"true\" startWithSAP=\"1\">\n")
            sb.append("<Representation id=\"v\" codecs=\"${video.codecs}\" bandwidth=\"${maxOf(video.bitrate, 1)}\" width=\"${video.width}\" height=\"${video.height}\"")
            if (video.fps > 0) sb.append(" frameRate=\"${video.fps}\"")
            sb.append(">\n")
            sb.append(videoTemplate)
            sb.append("</Representation>\n</AdaptationSet>\n")
        }

        if (audio != null) {
            val lang = audio.language ?: "und"
            sb.append("<AdaptationSet mimeType=\"${audio.containerMimeType}\" contentType=\"audio\" lang=\"$lang\" segmentAlignment=\"true\">\n")
            sb.append("<Representation id=\"a\" codecs=\"${audio.codecs}\" bandwidth=\"${maxOf(audio.bitrate, 1)}\"")
            if (audio.audioSampleRate > 0) sb.append(" audioSamplingRate=\"${audio.audioSampleRate}\"")
            sb.append(">\n")
            if (audio.audioChannels > 0)
                sb.append("<AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"${audio.audioChannels}\"/>\n")
            sb.append(audioTemplate)
            sb.append("</Representation>\n</AdaptationSet>\n")
        }

        sb.append("</Period>\n")

        if (isLive) {
            if (timeUrl != null)
                sb.append("<UTCTiming schemeIdUri=\"urn:mpeg:dash:utc:http-iso:2014\" value=\"$timeUrl\"/>\n")
            else
                sb.append("<UTCTiming schemeIdUri=\"urn:mpeg:dash:utc:direct:2014\" value=\"${toIso8601(System.currentTimeMillis())}\"/>\n")
        }

        sb.append("</MPD>\n")

        val manifest = sb.toString()

        if (isLive && shared != null) {
            if (planner.commit(shared)) {
                Logger.w(TAG, "The window moved to $shared, away from what the receiver holds; seeking it back in")
                onReceiverLost?.invoke()
            }

            val retain = planner.retainFrom()
            if (retain > 0) {
                listOfNotNull(videoTimeline, audioTimeline).forEach { it.dropBefore(retain) }
                for (anchors in listOf(videoAnchors, audioAnchors))
                    synchronized(anchors) { anchors.headMap(retain, false).clear() }
            }

            SabrSession.liveLog("manifest window startNumber=${shared.first} head=${shared.last} " +
                "count=${shared.last - shared.first + 1} edge=${planner.liveEdge} retain=$retain")
        }

        lastGoodManifest = manifest
        lastGoodManifestAtMs = System.currentTimeMillis()
        return manifest
    }

    private fun recentManifestOrNull(): String? = lastGoodManifest

    private fun liveWindowDepthMs(range: IntRange? = planner.window): Long {
        val depths = listOf(SabrSession.ROLE_VIDEO, SabrSession.ROLE_AUDIO).mapNotNull { role ->
            if (range == null) return@mapNotNull null
            val timeline = timelineFor(role) ?: return@mapNotNull null
            val startUs = timeline.startUs(range.first) ?: return@mapNotNull null
            val endUs = timeline.startUs(range.last)?.plus(timeline.durationUs(range.last) ?: 0)
                ?: return@mapNotNull null
            (endUs - startUs) / 1000
        }
        return depths.minOrNull()?.coerceAtLeast(1)
            ?: maxOf(videoSegMs, audioSegMs).coerceAtLeast(1)
    }

    private fun segmentTemplate(role: Int, segMs: Long, startNumber: Int, timing: SidxTiming?, initUrl: String, mediaUrl: String, livePtoMs: Long = 0, liveWindow: LiveWindow? = null): String? {
        val media = if (mediaUrl.contains('?')) "$mediaUrl&amp;n=\$Number\$" else "$mediaUrl?n=\$Number\$"

        if (isLive) {
            val window = liveWindow ?: return null
            val pto = if (livePtoMs > 0) " presentationTimeOffset=\"$livePtoMs\"" else ""
            return "<SegmentTemplate timescale=\"1000\"$pto startNumber=\"${window.startSequence}\" " +
                "initialization=\"$initUrl\" media=\"$media\">\n" +
                window.xml +
                "</SegmentTemplate>\n"
        }

        val timeline = timelineFor(role)
        if (timeline != null && !timeline.isEmpty) {
            val xml = timeline.segmentTimelineXml(timeline.firstNumber, timeline.lastNumber)
            if (xml != null) {
                val pto = if (timeline.presentationOffsetTicks > 0)
                    " presentationTimeOffset=\"${timeline.presentationOffsetTicks}\"" else ""
                return "<SegmentTemplate timescale=\"${timeline.timescale}\"$pto startNumber=\"${timeline.firstNumber}\" " +
                    "initialization=\"$initUrl\" media=\"$media\">\n" +
                    xml +
                    "</SegmentTemplate>\n"
            }
        }

        val dur = segMs.coerceAtLeast(1)
        return "<SegmentTemplate timescale=\"1000\" duration=\"$dur\" startNumber=\"$startNumber\" " +
            "initialization=\"$initUrl\" media=\"$media\"/>\n"
    }

    private fun presentationOffsetUs(role: Int): Long = timelineFor(role)?.presentationOffsetUs ?: 0

    private fun availabilityStartMs(publishedEndMs: Long = 0): Long {
        lastAvailabilityStartMs.takeIf { it > 0 }?.let { return it }

        val broadcastHeadMs = session.liveMetadata?.headSequenceTimeMs?.takeIf { it > 0 }
        if (broadcastHeadMs != null) {
            val result = System.currentTimeMillis() - (broadcastHeadMs - liveEpochMs)
            lastAvailabilityStartMs = result
            return result
        }

        if (publishedEndMs > 0) {
            val result = System.currentTimeMillis() - (publishedEndMs - liveEpochMs)
            lastAvailabilityStartMs = result
            return result
        }

        val headMs = listOfNotNull(video, audio)
            .map { session.bufferFor(it).bufferedExactEndUs() }
            .filter { it != Long.MIN_VALUE }
            .minOrNull()?.div(1000)
            ?: return liveAnchorWallMs - liveAnchorMediaMs + liveEpochMs

        val result = System.currentTimeMillis() - (headMs - liveEpochMs)
        lastAvailabilityStartMs = result
        return result
    }

    @Volatile private var lastAvailabilityStartMs = 0L

    private fun windowFloorUs(): Long {
        if (!isLive) return 0
        val range = planner.window ?: return 0
        return listOf(SabrSession.ROLE_VIDEO, SabrSession.ROLE_AUDIO)
            .mapNotNull { timelineFor(it)?.startUs(range.first) }.minOrNull() ?: 0
    }

    private class LiveWindow(val startSequence: Int, val xml: String)

    private fun liveRange(): IntRange? {
        val tracks = listOfNotNull(
            video?.let { trackFor(SabrSession.ROLE_VIDEO) ?: return null },
            audio?.let { trackFor(SabrSession.ROLE_AUDIO) ?: return null }
        )
        return planner.planWindow(tracks)
    }

    private fun liveWindow(role: Int, shared: IntRange): LiveWindow? {
        val timeline = timelineFor(role) ?: return null
        val range = shared

        val xml = timeline.segmentTimelineXml(range.first, range.last) ?: return null
        return LiveWindow(range.first, xml)
    }

    private class SegmentRequest {
        private val lock = Object()
        @Volatile var stale = false
        @Volatile private var done = false
        private var result: ByteArray? = null

        fun complete(value: ByteArray?) = synchronized(lock) {
            result = value
            done = true
            lock.notifyAll()
        }

        fun await(timeoutMs: Long): ByteArray? = synchronized(lock) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!done) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return null
                lock.wait(remaining)
            }
            return result
        }
    }

    private val videoInflight = java.util.concurrent.ConcurrentHashMap<Int, SegmentRequest>()
    private val audioInflight = java.util.concurrent.ConcurrentHashMap<Int, SegmentRequest>()

    private fun inflightFor(role: Int) =
        if (role == SabrSession.ROLE_VIDEO) videoInflight else audioInflight

    private val videoInitLock = Any()
    private val audioInitLock = Any()
    private fun initLockFor(role: Int) = if (role == SabrSession.ROLE_VIDEO) videoInitLock else audioInitLock

    fun getInit(role: Int): ByteArray? {
        if (isDead) return null
        cachedInit(role)?.let { return it }

        return synchronized(initLockFor(role)) {
            cachedInit(role)?.let { return@synchronized it }
            if (isDead) return@synchronized null

            val format = formatFor(role) ?: return@synchronized null
            val buffer = session.bufferFor(format)
            if (isLive) return@synchronized liveInit(role, buffer)

            val init = awaitInit(buffer) ?: return@synchronized null
            val bytes = init.toByteArray()
            setCachedInit(role, bytes)
            bytes
        }
    }

    private fun liveInit(role: Int, buffer: SabrTrackBuffer): ByteArray? {
        cachedInit(role)?.let { return it }

        buffer.initSegment?.let { init ->
            if (init.isComplete) {
                val bytes = init.toByteArray()
                setCachedInit(role, bytes)
                SabrSession.liveLog("live init role=$role from the announced init segment len=${bytes.size}")
                return bytes
            }
        }

        val deadline = System.currentTimeMillis() + PREPARE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isDead) return null
            val head = buffer.lastCompletedFromFront()
            if (head >= 0) {
                var seq = buffer.lowestSequence.coerceAtLeast(0)
                while (seq <= head) {
                    val seg = buffer.get(seq)
                    if (seg != null && seg.isComplete) {
                        val bytes = seg.toByteArray()
                        val len = initPrefixLength(role, bytes)
                        if (len > 0) {
                            logBoxLayout("segment role=$role seq=$seq", bytes)
                            val init = bytes.copyOf(len)
                            setCachedInit(role, init)
                            SabrSession.liveLog("live init role=$role seq=$seq len=$len")
                            return init
                        }
                    }
                    seq++
                }
            }
            session.wakePump()
            Thread.sleep(50)
        }
        return null
    }

    private fun anchorsFor(role: Int): TreeMap<Int, Long> =
        if (role == SabrSession.ROLE_VIDEO) videoAnchors else audioAnchors

    private fun targetUsFor(role: Int, sequence: Int): Long? {
        val timeline = timelineFor(role) ?: return null
        val nominalMid = timeline.midUs(sequence) ?: return null

        val anchors = anchorsFor(role)
        synchronized(anchors) {
            if (anchors.isEmpty()) return nominalMid
            val exact = anchors[sequence]
            if (exact != null) return exact + (timeline.durationUs(sequence) ?: 0) / 2

            val floor = anchors.floorEntry(sequence)
            val ceil = anchors.ceilingEntry(sequence)
            val nearest = when {
                floor == null -> ceil
                ceil == null -> floor
                sequence - floor.key <= ceil.key - sequence -> floor
                else -> ceil
            } ?: return nominalMid

            val nominalNearest = timeline.startUs(nearest.key) ?: return nominalMid
            return (nominalMid + (nearest.value - nominalNearest)).coerceAtLeast(0)
        }
    }

    private fun checkAnchor(role: Int, sequence: Int, segment: SabrSegment) {
        val timeline = timelineFor(role) ?: return
        val nominal = timeline.startUs(sequence) ?: return
        val drift = segment.startUs - nominal
        if (Math.abs(drift) < ANCHOR_TOLERANCE_US) return

        val anchors = anchorsFor(role)
        val isNew = synchronized(anchors) {
            val existing = anchors[sequence]
            anchors[sequence] = segment.startUs
            existing == null
        }
        if (isNew)
            Logger.w(TAG, "Timeline drift role=$role seq=$sequence nominalMs=${nominal / 1000} " +
                "actualMs=${segment.startUs / 1000} driftMs=${drift / 1000}; anchoring")
    }

    private fun requestSeek(role: Int, targetUs: Long, mustRestart: Boolean = false) {
        val format = formatFor(role) ?: return
        synchronized(seekLock) {
            val now = System.currentTimeMillis()
            if (role == SabrSession.ROLE_VIDEO) { videoSeekUs = targetUs; videoSeekAtMs = now }
            else { audioSeekUs = targetUs; audioSeekAtMs = now }

            val coveredByRestart = !mustRestart && lastSeekUs != Long.MIN_VALUE && targetUs >= lastSeekUs

            val buffer = session.bufferFor(format)

            if (mustRestart && now - lastSeekAtMs < MIN_RESTART_INTERVAL_MS &&
                lastSeekUs != Long.MIN_VALUE && targetUs >= lastSeekUs &&
                targetUs - lastSeekUs <= FORWARD_GAP_SLACK_US) {
                session.setDemand(role, format, streamThroughDemand(buffer, targetUs))
                SabrSession.sabrLog("cast seek role=$role targetMs=${targetUs / 1000} suppressed (restart in flight, within streaming range)")
                return
            }

            if (coveredByRestart && now - lastSeekAtMs < SEEK_COALESCE_MS &&
                Math.abs(targetUs - lastSeekUs) < seekCoalesceUs()) {
                session.setDemand(role, format, streamThroughDemand(buffer, targetUs))
                return
            }

            if (coveredByRestart && now - lastSeekAtMs < MIN_RESTART_INTERVAL_MS &&
                targetUs - lastSeekUs <= FORWARD_GAP_SLACK_US) {
                session.setDemand(role, format, streamThroughDemand(buffer, targetUs))
                SabrSession.sabrLog("cast seek role=$role targetMs=${targetUs / 1000} suppressed (restart floor, already covered)")
                return
            }

            val nearThisSeek = { t: Long -> Math.abs(t - targetUs) <= FORWARD_GAP_SLACK_US }
            val freshVideo = videoSeekUs.takeIf {
                it >= 0 && now - videoSeekAtMs < SEEK_COALESCE_MS && nearThisSeek(it)
            }
            val freshAudio = audioSeekUs.takeIf {
                it >= 0 && now - audioSeekAtMs < SEEK_COALESCE_MS && nearThisSeek(it)
            }

            val targets = listOfNotNull(
                freshVideo.takeIf { video != null },
                freshAudio.takeIf { audio != null }
            )

            val from = ((targets.minOrNull() ?: targetUs) - seekCoalesceUs()).coerceAtLeast(0)

            video?.let { session.setDemand(SabrSession.ROLE_VIDEO, it, from) }
            audio?.let { session.setDemand(SabrSession.ROLE_AUDIO, it, from) }
            session.setPlaybackPosition(from)
            session.restart(from)

            lastSeekUs = from
            lastSeekAtMs = now
            abandonRequestsFarFrom(from)
            SabrSession.sabrLog("cast seek role=$role targetMs=${targetUs / 1000} restartFromMs=${from / 1000}")
        }
    }

    private fun abandonRequestsFarFrom(fromUs: Long) {
        val behindUs = seekCoalesceUs()
        for (role in listOf(SabrSession.ROLE_VIDEO, SabrSession.ROLE_AUDIO)) {
            val inflight = inflightFor(role)
            for ((seq, request) in inflight) {
                val targetUs = targetUsFor(role, seq) ?: continue
                val stranded = targetUs < fromUs - behindUs || targetUs > fromUs + FORWARD_GAP_SLACK_US
                if (!stranded) continue

                request.stale = true
                inflight.remove(seq, request)
                request.complete(null)
            }
        }
    }

    private fun seekCoalesceUs(): Long =
        (2 * maxOf(videoSegMs, audioSegMs).coerceAtLeast(1)) * 1000

    private fun streamThroughDemand(buffer: SabrTrackBuffer, targetUs: Long): Long =
        minOf(targetUs, fetchFloorFor(buffer).takeIf { it != Long.MIN_VALUE } ?: targetUs)

    private fun fetchFloorFor(buffer: SabrTrackBuffer): Long =
        fetchFloor(buffer.bufferedEndFromFrontUs(), buffer.firstAtOrAfter(-1)?.startUs, sessionFloorUs())

    private fun gapFloorFor(buffer: SabrTrackBuffer): Long =
        gapFloor(buffer.bufferedEndFromFrontUs(), buffer.firstAtOrAfter(-1)?.startUs, sessionFloorUs(), seekCoalesceUs())

    private fun sessionFloorUs(): Long = if (isLive) Long.MIN_VALUE else session.fetchFloorUs

    private fun cachedInit(role: Int): ByteArray? =
        if (role == SabrSession.ROLE_VIDEO) videoInit else audioInit

    private fun setCachedInit(role: Int, value: ByteArray) {
        if (role == SabrSession.ROLE_VIDEO) videoInit = value else audioInit = value
    }

    fun getSegment(role: Int, sequence: Int): ByteArray? {
        if (isDead) return null

        if (formatFor(role) == null) return null
        val lastSeq = if (role == SabrSession.ROLE_VIDEO) videoLastSeq else audioLastSeq
        if (sequence > lastSeq) return null

        noteReceiverRequest(role, sequence)

        val inflight = inflightFor(role)
        var owned: SegmentRequest? = null
        val request = inflight.compute(sequence) { _, existing ->
            existing ?: SegmentRequest().also { owned = it }
        }!!

        if (owned == null) return request.await(segmentTimeoutMs())

        var result: ByteArray? = null
        try {
            result = fetchSegment(role, sequence, request)
        } catch (ex: Throwable) {
            Logger.w(TAG, "getSegment role=$role seq=$sequence failed", ex)
        } finally {
            inflight.remove(sequence, request)
            request.complete(result)
        }
        return result
    }

    private fun segmentTimeoutMs(): Long {
        if (!isLive) return SEGMENT_TIMEOUT_MS
        val segMs = maxOf(videoSegMs, audioSegMs).coerceAtLeast(1)
        return (3 * segMs).coerceIn(4_000L, 10_000L)
    }

    private fun fetchSegment(role: Int, sequence: Int, request: SegmentRequest): ByteArray? {
        val format = formatFor(role) ?: return null
        val buffer = session.bufferFor(format)
        val firstSeq = if (role == SabrSession.ROLE_VIDEO) videoFirstSeq else audioFirstSeq
        val segMs = if (role == SabrSession.ROLE_VIDEO) videoSegMs else audioSegMs

        val targetUs = targetUsFor(role, sequence) ?: buffer.get(sequence)?.startUs ?: run {
            val head = buffer.lastCompletedFromFront()
            val headSeg = if (head >= 0) buffer.get(head) else null
            if (headSeg != null && sequence > head)
                headSeg.endUs + (sequence - head - 1).coerceAtLeast(0) * segMs * 1000
            else if (isLive) return null
            else (sequence - firstSeq).coerceAtLeast(0) * segMs * 1000
        }

        val low = buffer.lowestSequence
        val head = buffer.lastCompletedFromFront()
        val absent = buffer.get(sequence) == null

        val floor = fetchFloorFor(buffer)
        val halfSegmentUs = (if (role == SabrSession.ROLE_VIDEO) videoSegMs else audioSegMs) * 500
        val backwards = absent && (
            (low >= 0 && sequence < low) ||
                (low < 0 && !isLive && floor != Long.MIN_VALUE && targetUs + halfSegmentUs < floor)
            )

        val gapFloor = gapFloorFor(buffer)
        val forwardGap = absent && !isLive && gapFloor != Long.MIN_VALUE &&
            targetUs > gapFloor + FORWARD_GAP_SLACK_US

        SabrSession.sabrLog("getSegment role=$role reqSeq=$sequence buffered=${!absent} " +
            "head=$head low=$low targetMs=${targetUs / 1000} backwards=$backwards forwardGap=$forwardGap")

        val ready = buffer.get(sequence)?.takeIf { it.isComplete }
        if (ready != null) {
            checkAnchor(role, sequence, ready)
            return finishSegment(role, sequence, ready)
        }

        if (backwards && isLive) {
            SabrSession.liveLog("getSegment role=$role reqSeq=$sequence below window (low=$low) -> 404")
            onReceiverLost?.invoke()
            return null
        }

        if (!isLive && (backwards || forwardGap)) requestSeek(role, targetUs, mustRestart = forwardGap)

        val deadline = System.currentTimeMillis() + segmentTimeoutMs()
        var seekedFromWait = false
        while (System.currentTimeMillis() < deadline) {
            if (isDead || request.stale) return null

            val segment = buffer.get(sequence)
            if (segment == null) {
                val lowNow = buffer.lowestSequence
                if (!isLive && !seekedFromWait && lowNow >= 0 && sequence < lowNow) {
                    seekedFromWait = true
                    SabrSession.sabrLog("getSegment role=$role reqSeq=$sequence fell below the refill (low=$lowNow); seeking")
                    requestSeek(role, targetUs)
                    continue
                }

                if (!isLive) session.setDemand(role, format, streamThroughDemand(buffer, targetUs))
                session.wakePump()
                buffer.awaitSequence(sequence, 250)
                continue
            }

            if (!segment.isComplete) {
                if (!buffer.awaitBytes(segment, segment.size, 250) && !segment.isComplete)
                    session.wakePump()
                continue
            }

            checkAnchor(role, sequence, segment)
            return finishSegment(role, sequence, segment)
        }
        SabrSession.sabrLog("getSegment role=$role reqSeq=$sequence TIMEOUT (404) head=${buffer.lastCompletedFromFront()}")
        return null
    }

    private fun finishSegment(role: Int, sequence: Int, segment: SabrSegment): ByteArray {
        val bytes = segment.toByteArray()
        if (!isLive) return bytes
        if (role == SabrSession.ROLE_VIDEO && !loggedVideoBoxes) { loggedVideoBoxes = true; logBoxLayout("media v seq=$sequence", bytes) }
        if (role == SabrSession.ROLE_AUDIO && !loggedAudioBoxes) { loggedAudioBoxes = true; logBoxLayout("media a seq=$sequence", bytes) }
        val strip = initPrefixLength(role, bytes).coerceAtMost(bytes.size)
        return if (strip > 0) bytes.copyOfRange(strip, bytes.size) else bytes
    }

    fun exportTransferable(): SabrSession.Transferable? {
        if (session.isReleased || session.fatalError != null) return null
        return session.exportTransferable()
    }

    private fun failAllInflight() {
        for (map in listOf(videoInflight, audioInflight)) {
            for (request in map.values) {
                request.stale = true
                request.complete(null)
            }
            map.clear()
        }
    }

    fun release() {
        released = true

        onBackoff = null
        onFatalError = null
        onReceiverLost = null
        playheadUs = null

        session.setListener(null)
        session.release()

        failAllInflight()
    }

    private fun formatFor(role: Int): SabrFormat? = if (role == SabrSession.ROLE_VIDEO) video else audio

    private fun awaitInit(buffer: SabrTrackBuffer): SabrSegment? {
        val deadline = System.currentTimeMillis() + SEGMENT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isDead) return null

            val init = buffer.initSegment
            if (init == null) {
                session.wakePump()
                Thread.sleep(50)
                continue
            }

            if (init.isComplete) return init

            if (!buffer.awaitBytes(init, init.size, 250) && !init.isComplete)
                session.wakePump()
        }
        return null
    }

    private fun toIsoDuration(ms: Long): String {
        val totalSeconds = ms / 1000.0
        return "PT${String.format(java.util.Locale.US, "%.3f", totalSeconds)}S"
    }

    private fun toIso8601(epochMs: Long): String = java.time.Instant.ofEpochMilli(epochMs).toString()

    private fun isWebm(role: Int): Boolean {
        val mime = formatFor(role)?.containerMimeType?.lowercase() ?: return false
        return mime.contains("webm") || mime.contains("matroska")
    }

    private fun initPrefixLength(role: Int, data: ByteArray): Int =
        if (isWebm(role)) webmInitPrefixLength(data) else mp4InitPrefixLength(data)

    private fun logBoxLayout(tag: String, data: ByteArray) {
        if (isWebm(SabrSession.ROLE_VIDEO)) { SabrSession.liveLog("boxes $tag webm total=${data.size}"); return }
        val sb = StringBuilder()
        var pos = 0
        var n = 0
        while (pos + 8 <= data.size && n < 24) {
            val size32 = readU32(data, pos)
            val type = String(data, pos + 4, 4, Charsets.US_ASCII)
            val boxSize = when (size32) {
                1L -> if (pos + 16 <= data.size) readU64(data, pos + 8) else break
                0L -> (data.size - pos).toLong()
                else -> size32
            }
            if (boxSize < 8) break
            sb.append("$type($boxSize) ")
            pos += boxSize.toInt()
            n++
        }
        SabrSession.liveLog("boxes $tag total=${data.size}: $sb")
    }

    private fun mp4InitPrefixLength(data: ByteArray): Int {
        var pos = 0
        while (pos + 8 <= data.size) {
            val size32 = readU32(data, pos)
            val type = String(data, pos + 4, 4, Charsets.US_ASCII)
            val boxSize = when (size32) {
                1L -> if (pos + 16 <= data.size) readU64(data, pos + 8) else return 0
                0L -> (data.size - pos).toLong()
                else -> size32
            }
            if (boxSize < 8) return 0
            when (type) {
                "styp", "sidx", "moof", "mdat", "emsg" -> return pos
            }
            pos += boxSize.toInt()
        }
        return 0
    }

    private fun webmInitPrefixLength(data: ByteArray): Int {
        var pos = 0
        while (pos + 4 <= data.size) {
            val idLen = ebmlIdLength(data[pos])
            if (idLen == 0 || pos + idLen > data.size) return 0
            val id = readEbmlId(data, pos, idLen)
            val sz = readEbmlSize(data, pos + idLen) ?: return 0
            val contentStart = pos + idLen + sz.second
            if (id == 0x18538067L) {
                var cpos = contentStart
                while (cpos + 4 <= data.size) {
                    val cidLen = ebmlIdLength(data[cpos])
                    if (cidLen == 0 || cpos + cidLen > data.size) return 0
                    val cid = readEbmlId(data, cpos, cidLen)
                    if (cid == 0x1F43B675L) return cpos
                    val csz = readEbmlSize(data, cpos + cidLen) ?: return 0
                    if (csz.first < 0) return 0
                    cpos += cidLen + csz.second + csz.first.toInt()
                }
                return 0
            }
            if (sz.first < 0) return 0
            pos = contentStart + sz.first.toInt()
        }
        return 0
    }

    private fun readU32(d: ByteArray, p: Int): Long =
        ((d[p].toLong() and 0xFF) shl 24) or ((d[p + 1].toLong() and 0xFF) shl 16) or
            ((d[p + 2].toLong() and 0xFF) shl 8) or (d[p + 3].toLong() and 0xFF)

    private fun readU64(d: ByteArray, p: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (d[p + i].toLong() and 0xFF)
        return v
    }

    private fun ebmlIdLength(b: Byte): Int {
        val v = b.toInt() and 0xFF
        return when {
            v and 0x80 != 0 -> 1
            v and 0x40 != 0 -> 2
            v and 0x20 != 0 -> 3
            v and 0x10 != 0 -> 4
            else -> 0
        }
    }

    private fun readEbmlId(d: ByteArray, p: Int, len: Int): Long {
        var v = 0L
        for (i in 0 until len) v = (v shl 8) or (d[p + i].toLong() and 0xFF)
        return v
    }

    private fun readEbmlSize(d: ByteArray, p: Int): Pair<Long, Int>? {
        if (p >= d.size) return null
        val first = d[p].toInt() and 0xFF
        var len = 0
        var mask = 0x80
        while (len < 8) { len++; if (first and mask != 0) break; mask = mask shr 1 }
        if (len == 0 || first and mask == 0 || p + len > d.size) return null
        var value = (first and (mask - 1)).toLong()
        var allOnes = value == (mask - 1).toLong()
        for (i in 1 until len) {
            val bv = d[p + i].toInt() and 0xFF
            if (bv != 0xFF) allOnes = false
            value = (value shl 8) or bv.toLong()
        }
        return if (allOnes) Pair(-1L, len) else Pair(value, len)
    }

    companion object {
        fun fetchFloor(bufferedEndUs: Long, frontStartUs: Long?, sessionFloorUs: Long): Long {
            if (bufferedEndUs != Long.MIN_VALUE) return bufferedEndUs
            if (frontStartUs != null) return frontStartUs
            return sessionFloorUs
        }

        fun gapFloor(bufferedEndUs: Long, frontStartUs: Long?, sessionFloorUs: Long, preRollUs: Long): Long {
            val floor = fetchFloor(bufferedEndUs, frontStartUs, sessionFloorUs)
            if (sessionFloorUs == Long.MIN_VALUE) return floor
            val target = sessionFloorUs + preRollUs
            return if (floor == Long.MIN_VALUE) target else maxOf(floor, target)
        }

        private const val LIVE_END_STALL_SEGMENTS = 30
        private const val PREPARE_TIMEOUT_MS = 20_000L
        private const val SEGMENT_TIMEOUT_MS = 20_000L
        private const val DEFAULT_SEG_MS = 5_000L
        private const val LIVE_WINDOW_SEGMENTS = 10

        private const val LIVE_MIN_PLAYBACK_RATE = "0.97"
        private const val LIVE_MAX_PLAYBACK_RATE = "1.03"
        private const val LIVE_LATENCY_MIN_MS = 4_000L
        private const val LIVE_RECOVER_SEGMENTS = 3
        private const val LIVE_RECOVER_MIN_INTERVAL_MS = 10_000L

        private const val LIVE_MIN_READAHEAD_MS = 20_000L
        private const val LIVE_RATE_SAMPLE_MS = 30_000L
        private const val LIVE_PLAYHEAD_JUMP_MS = 1_000L
        private const val LIVE_HEALTH_LOG_INTERVAL_MS = 5_000L
        private const val LIVE_WATCHDOG_INTERVAL_MS = 2_000L

        private const val LIVE_MAX_REPORTED_LAG_US = 12_000_000L

        private const val LIVE_PRESENTATION_SEGMENTS = 6

        private const val LIVE_READAHEAD_SEGMENTS = 4
        private const val LIVE_EVICT_GUARD = 3
        private const val LIVE_START_SEGMENTS = LIVE_PRESENTATION_SEGMENTS + 2

        private const val LIVE_MIN_START_SEGMENTS = 3
        private const val LIVE_MAX_LAG_SEGMENTS = 40
        private const val LIVE_DEEPEN_TIMEOUT_MS = 8_000L
        private const val ANCHOR_TOLERANCE_US = 100_000L
        private const val SEEK_COALESCE_MS = 3_000L
        private const val MIN_RESTART_INTERVAL_MS = 2_000L
        private const val FORWARD_GAP_SLACK_US = 30_000_000L
        private const val VOD_KEEP_BEHIND_US = 60_000_000L
        private const val LIVE_KEEP_BEHIND_US = 90_000_000L
        private const val TAG = "SabrCastProxy"
    }
}
