package com.futo.platformplayer.casting.dlna

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Reads byte ranges from a remote or local file.
 */
interface RangeReader {
    fun read(offset: Long, length: Int): ByteArray
    /**
     * Like [read], but returns fewer bytes when the resource ends first.
     */
    fun readUpTo(offset: Long, maxLength: Int): ByteArray
    fun copyTo(offset: Long, length: Long, output: OutputStream)
    fun readAll(): ByteArray
}

/**
 * Combines fragmented MP4 tracks (DASH on-demand files with init segment + sidx, or fMP4 HLS segment lists) into one
 * flat, non-fragmented MP4 without downloading the media itself. Takes a video track, an audio track or both.
 *
 * Only the init segments, the indexes and every moof box are read up front to rebuild the sample tables. The
 * output's mdat maps chunk by chunk onto the sources' mdat payloads, so any byte range of the output can be served
 * by copying the matching source ranges. Needed because DLNA renderers such as Samsung TVs only play the first
 * fragment of a fragmented MP4, cannot combine separate audio and video streams and handle HLS poorly.
 */
class FlatMp4Muxer private constructor(
    val size: Long,
    val durationSeconds: Double,
    val contentType: String,
    private val header: ByteArray,
    private val pieces: List<Piece>
) {
    private class Piece(val outOffset: Long, val size: Long, val reader: RangeReader, val srcOffset: Long)

    //Renderers read with many short, overlapping range requests that jump between the interleaved audio and video
    //chunks. Whole chunks are cached and the following ones prefetched so those reads don't each hit the source.
    private val _cacheLock = Any()
    private val _cache = LinkedHashMap<Int, ByteArray>(32, 0.75f, true)
    private var _cacheBytes = 0L
    private val _inflight = HashMap<Int, CompletableFuture<ByteArray>>()
    private val _fetchExecutor = ThreadPoolExecutor(0, PREFETCH_COUNT + 1, 30, TimeUnit.SECONDS, LinkedBlockingQueue()) { r ->
        Thread(r, "FlatMp4Muxer").apply { isDaemon = true }
    }

    /**
     * Writes the inclusive output byte range [start, end].
     */
    fun writeTo(output: OutputStream, start: Long, end: Long) {
        if (start < header.size) {
            val headerEnd = minOf(end + 1, header.size.toLong()).toInt()
            output.write(header, start.toInt(), headerEnd - start.toInt())
        }

        var index = pieces.binarySearchBy(start) { it.outOffset }.let { if (it < 0) -it - 2 else it }.coerceAtLeast(0)
        while (index < pieces.size) {
            val pieceIndex = index++
            val piece = pieces[pieceIndex]
            if (piece.outOffset > end) break
            if (piece.outOffset + piece.size <= start) continue

            val relStart = maxOf(start - piece.outOffset, 0L)
            val relEnd = minOf(end - piece.outOffset, piece.size - 1)
            if (piece.size > MAX_CACHED_PIECE_SIZE) {
                piece.reader.copyTo(piece.srcOffset + relStart, relEnd - relStart + 1, output)
                continue
            }

            val bytes = getPiece(pieceIndex)
            prefetch(pieceIndex + 1)
            output.write(bytes, relStart.toInt(), (relEnd - relStart + 1).toInt())
        }
    }

    private fun getPiece(index: Int): ByteArray {
        val future = synchronized(_cacheLock) {
            _cache[index]?.let { return it }
            _inflight[index] ?: fetch(index)
        }
        try {
            return future.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    private fun prefetch(from: Int) {
        synchronized(_cacheLock) {
            for (i in from until minOf(from + PREFETCH_COUNT, pieces.size)) {
                if (pieces[i].size <= MAX_CACHED_PIECE_SIZE && !_cache.containsKey(i) && !_inflight.containsKey(i)) {
                    fetch(i)
                }
            }
        }
    }

    /**
     * Must be called with [_cacheLock] held.
     */
    private fun fetch(index: Int): CompletableFuture<ByteArray> {
        val piece = pieces[index]
        val future = CompletableFuture.supplyAsync({ piece.reader.read(piece.srcOffset, piece.size.toInt()) }, _fetchExecutor)
        _inflight[index] = future
        future.whenComplete { bytes, _ ->
            synchronized(_cacheLock) {
                _inflight.remove(index)
                if (bytes != null && _cache.put(index, bytes) == null) {
                    _cacheBytes += bytes.size
                    val iterator = _cache.entries.iterator()
                    while (_cacheBytes > CACHE_BUDGET && iterator.hasNext()) {
                        val eldest = iterator.next()
                        if (eldest.key == index) continue
                        _cacheBytes -= eldest.value.size
                        iterator.remove()
                    }
                }
            }
        }
        return future
    }

    sealed interface TrackSource

    /**
     * A fragmented MP4 file with known init and sidx byte ranges (DASH on-demand).
     */
    class Source(val reader: RangeReader, val initStart: Long, val initEnd: Long, val indexStart: Long, val indexEnd: Long) : TrackSource

    /**
     * Separately addressed fMP4 segments (HLS with EXT-X-MAP). A length of -1 means the whole resource.
     */
    class SegmentSource(val init: Segment, val segments: List<Segment>) : TrackSource {
        class Segment(val reader: RangeReader, val offset: Long = 0, val length: Long = -1)
    }

    companion object {
        private const val MOOF_PROBE_SIZE = 4096
        private const val MAX_MOOF_PROBES = 4
        private val SKIPPABLE_BOXES = setOf("styp", "sidx", "ssix", "prft", "emsg", "free", "skip")
        private const val FETCH_PARALLELISM = 16
        private const val PREFETCH_COUNT = 4
        private const val MAX_CACHED_PIECE_SIZE = 16L * 1024 * 1024
        private const val CACHE_BUDGET = 48L * 1024 * 1024

        fun create(video: TrackSource?, audio: TrackSource?): FlatMp4Muxer {
            require(video != null || audio != null) { "At least one track is required" }
            val executor = Executors.newFixedThreadPool(FETCH_PARALLELISM)
            try {
                var trackId = 1
                val videoTrack = video?.let { Track.load(InitInfo.load(it), trackId++, executor) }
                val audioTrack = audio?.let { Track.load(InitInfo.load(it), trackId++, executor) }
                return build(videoTrack, audioTrack)
            } finally {
                executor.shutdownNow()
            }
        }

        private fun build(video: Track?, audio: Track?): FlatMp4Muxer {
            val tracks = listOfNotNull(video, audio)
            val first = tracks.first()
            val movieTimescale = first.init.movieTimescale

            //Interleave chunks by start time: each video chunk is followed by the audio chunks that start before the next video chunk.
            val order = ArrayList<Pair<Track, Int>>(tracks.sumOf { it.chunks.size })
            var audioIndex = 0
            val audioChunks = audio?.chunks ?: emptyList()
            if (video != null) {
                for (i in video.chunks.indices) {
                    val nextVideoStart = if (i == video.chunks.size - 1) Double.MAX_VALUE else video.chunks[i + 1].startTime.toDouble() / video.timescale
                    order.add(Pair(video, i))
                    while (audioIndex < audioChunks.size && audioChunks[audioIndex].startTime.toDouble() / audio!!.timescale < nextVideoStart) {
                        order.add(Pair(audio, audioIndex++))
                    }
                }
            }
            while (audioIndex < audioChunks.size) {
                order.add(Pair(audio!!, audioIndex++))
            }

            val movieDuration = tracks.maxOf { it.totalDuration * movieTimescale / it.timescale }
            val ftyp = if (video != null) {
                Mp4.box("ftyp", "isom".toByteArray() + Mp4.u32(512) + "isomiso2avc1mp41".toByteArray())
            } else {
                Mp4.box("ftyp", "M4A ".toByteArray() + Mp4.u32(512) + "M4A isomiso2mp41".toByteArray())
            }
            val payloadSize = tracks.sumOf { t -> t.chunks.sumOf { it.size } }

            fun buildMoov(offsets: Map<Track, LongArray>): ByteArray {
                val mvhd = first.init.mvhd.copyOf()
                val nextTrackId = tracks.size + 1L
                if (mvhd[8].toInt() == 0) {
                    Mp4.putU32(mvhd, 24, minOf(movieDuration, 0xFFFFFFFFL))
                    Mp4.putU32(mvhd, 104, nextTrackId)
                } else {
                    Mp4.putU64(mvhd, 32, movieDuration)
                    Mp4.putU32(mvhd, 116, nextTrackId)
                }
                val traks = tracks.map { it.buildTrak(offsets[it]!!, movieTimescale) }
                return Mp4.box("moov", traks.fold(mvhd) { acc, trak -> acc + trak })
            }

            //The moov size only depends on whether 64 bit chunk offsets are needed, so lay out with a placeholder first.
            val placeholder = buildMoov(tracks.associateWith { LongArray(it.chunks.size) })
            val largeFile = ftyp.size + placeholder.size + 16 + payloadSize > 0xFFFFFFFFL
            val mdatHeaderSize = if (largeFile) 16 else 8
            val dataStart = ftyp.size.toLong() + placeholder.size + mdatHeaderSize

            val offsets = tracks.associateWith { LongArray(it.chunks.size) }
            val pieces = ArrayList<Piece>(order.size)
            var out = dataStart
            for ((track, index) in order) {
                val chunk = track.chunks[index]
                offsets[track]!![index] = out
                pieces.add(Piece(out, chunk.size, chunk.reader, chunk.srcOffset))
                out += chunk.size
            }

            val moov = buildMoov(offsets)
            check(moov.size == placeholder.size) { "moov size changed between layout passes" }

            val mdatHeader = if (largeFile) Mp4.u32(1) + "mdat".toByteArray() + Mp4.u64(payloadSize + 16) else Mp4.u32(payloadSize + 8) + "mdat".toByteArray()
            return FlatMp4Muxer(out, movieDuration.toDouble() / movieTimescale, if (video != null) "video/mp4" else "audio/mp4", ftyp + moov + mdatHeader, pieces)
        }
    }

    private class InitInfo(val moov: ByteArray, val mvhd: ByteArray, val trak: ByteArray, val trex: ByteArray, val movieTimescale: Long, val mediaTimescale: Long, val fragments: List<Fragment>) {
        /**
         * A moof+mdat pair, size -1 when unknown.
         */
        class Fragment(val reader: RangeReader, val offset: Long, val size: Long)

        companion object {
            fun load(source: TrackSource): InitInfo = when (source) {
                is Source -> {
                    val init = source.reader.read(source.initStart, (source.initEnd - source.initStart + 1).toInt())
                    val index = source.reader.read(source.indexStart, (source.indexEnd - source.indexStart + 1).toInt())
                    val sidxBox = Mp4.find(index, "sidx", 0, index.size) ?: throw IllegalStateException("Index range has no sidx")
                    parse(init, parseSidx(source.reader, index, sidxBox, source.indexStart))
                }
                is SegmentSource -> {
                    val init = source.init.let { if (it.length < 0) it.reader.readAll() else it.reader.read(it.offset, it.length.toInt()) }
                    parse(init, source.segments.map { Fragment(it.reader, it.offset, it.length) })
                }
            }

            private fun parse(init: ByteArray, fragments: List<Fragment>): InitInfo {
                val moovBox = Mp4.find(init, "moov", 0, init.size) ?: throw IllegalStateException("Init segment has no moov")
                val moov = init.copyOfRange(moovBox.offset, moovBox.end)

                val mvhdBox = Mp4.find(moov, "mvhd", 8, moov.size) ?: throw IllegalStateException("moov has no mvhd")
                val trakBox = Mp4.find(moov, "trak", 8, moov.size) ?: throw IllegalStateException("moov has no trak")
                if (Mp4.boxes(moov, 8, moov.size).count { it.type == "trak" } != 1) throw IllegalStateException("Only single track sources are supported")
                val mvexBox = Mp4.find(moov, "mvex", 8, moov.size) ?: throw IllegalStateException("Source is not fragmented (no mvex)")
                val trexBox = Mp4.find(moov, "trex", mvexBox.contentStart, mvexBox.end) ?: throw IllegalStateException("mvex has no trex")
                val mvhd = moov.copyOfRange(mvhdBox.offset, mvhdBox.end)
                val trak = moov.copyOfRange(trakBox.offset, trakBox.end)

                val mdiaBox = Mp4.find(trak, "mdia", 8, trak.size) ?: throw IllegalStateException("trak has no mdia")
                val mdhdBox = Mp4.find(trak, "mdhd", mdiaBox.contentStart, mdiaBox.end) ?: throw IllegalStateException("mdia has no mdhd")

                return InitInfo(moov, mvhd, trak, moov.copyOfRange(trexBox.offset, trexBox.end),
                    Mp4.timescale(mvhd), Mp4.timescale(trak.copyOfRange(mdhdBox.offset, mdhdBox.end)), fragments)
            }

            private fun parseSidx(reader: RangeReader, data: ByteArray, sidx: Mp4.Box, fileOffset: Long): List<Fragment> {
                val version = data[sidx.offset + 8].toInt()
                var p = sidx.offset + 20
                val firstOffset: Long
                if (version == 0) {
                    firstOffset = Mp4.u32(data, p + 4); p += 8
                } else {
                    firstOffset = Mp4.u64(data, p + 8); p += 16
                }
                val count = Mp4.u16(data, p + 2); p += 4

                var offset = fileOffset + sidx.end + firstOffset
                val fragments = ArrayList<Fragment>(count)
                for (i in 0 until count) {
                    val reference = Mp4.u32(data, p)
                    if (reference ushr 31 != 0L) throw IllegalStateException("Hierarchical sidx is not supported")
                    val size = reference and 0x7FFFFFFF
                    fragments.add(Fragment(reader, offset, size))
                    offset += size
                    p += 12
                }
                return fragments
            }
        }
    }

    private class MoofData(val offset: Long, val bytes: ByteArray)

    private class Chunk(val reader: RangeReader, val srcOffset: Long, val size: Long, val sampleCount: Int, val startTime: Long)

    private class Track(
        val init: InitInfo,
        val trackId: Int,
        val trak: ByteArray,
        val timescale: Long,
        val chunks: List<Chunk>,
        val durations: IntList,
        val sizes: IntList,
        val compositionOffsets: IntList?,
        val syncSamples: IntList?,
        val totalDuration: Long
    ) {
        fun buildTrak(chunkOffsets: LongArray, movieTimescale: Long): ByteArray {
            val trackMovieDuration = totalDuration * movieTimescale / timescale
            return Mp4.box("trak", rebuild(trak, 8, trak.size, chunkOffsets, trackMovieDuration))
        }

        private fun rebuild(data: ByteArray, start: Int, end: Int, chunkOffsets: LongArray, trackMovieDuration: Long): ByteArray {
            val out = ByteArrayOutputStream()
            for (b in Mp4.boxes(data, start, end)) {
                when (b.type) {
                    "mdia", "minf", "edts" -> out.write(Mp4.box(b.type, rebuild(data, b.contentStart, b.end, chunkOffsets, trackMovieDuration)))
                    "stbl" -> {
                        val stsd = Mp4.find(data, "stsd", b.contentStart, b.end) ?: throw IllegalStateException("stbl has no stsd")
                        out.write(Mp4.box("stbl", data.copyOfRange(stsd.offset, stsd.end) + buildSampleTables(chunkOffsets)))
                    }
                    "tkhd" -> {
                        val box = data.copyOfRange(b.offset, b.end)
                        if (box[8].toInt() == 0) {
                            Mp4.putU32(box, 20, trackId.toLong())
                            Mp4.putU32(box, 28, minOf(trackMovieDuration, 0xFFFFFFFFL))
                        } else {
                            Mp4.putU32(box, 28, trackId.toLong())
                            Mp4.putU64(box, 36, trackMovieDuration)
                        }
                        out.write(box)
                    }
                    "mdhd" -> {
                        val box = data.copyOfRange(b.offset, b.end)
                        if (box[8].toInt() == 0) Mp4.putU32(box, 24, minOf(totalDuration, 0xFFFFFFFFL)) else Mp4.putU64(box, 32, totalDuration)
                        out.write(box)
                    }
                    "elst" -> {
                        //Fragmented files use a zero segment duration to mean "everything"; flat files need the real duration.
                        val box = data.copyOfRange(b.offset, b.end)
                        val version = box[8].toInt()
                        val count = Mp4.u32(box, 12).toInt()
                        var q = 16
                        for (i in 0 until count) {
                            if (version == 0) {
                                if (Mp4.u32(box, q) == 0L) Mp4.putU32(box, q, minOf(trackMovieDuration, 0xFFFFFFFFL))
                                q += 12
                            } else {
                                if (Mp4.u64(box, q) == 0L) Mp4.putU64(box, q, trackMovieDuration)
                                q += 20
                            }
                        }
                        out.write(box)
                    }
                    else -> out.write(data, b.offset, b.size)
                }
            }
            return out.toByteArray()
        }

        private fun buildSampleTables(chunkOffsets: LongArray): ByteArray {
            val out = ByteArrayOutputStream()

            val stts = runs(durations)
            out.write(Mp4.fullBox("stts", 0, 0, Mp4.u32(stts.size / 2L) + Mp4.u32Array(stts)))

            if (compositionOffsets != null) {
                val ctts = runs(compositionOffsets)
                out.write(Mp4.fullBox("ctts", 1, 0, Mp4.u32(ctts.size / 2L) + Mp4.u32Array(ctts)))
            }

            val stsc = IntList()
            for ((i, chunk) in chunks.withIndex()) {
                if (stsc.size == 0 || stsc[stsc.size - 2] != chunk.sampleCount) {
                    stsc.add(i + 1); stsc.add(chunk.sampleCount); stsc.add(1)
                }
            }
            out.write(Mp4.fullBox("stsc", 0, 0, Mp4.u32(stsc.size / 3L) + Mp4.u32Array(stsc)))

            out.write(Mp4.fullBox("stsz", 0, 0, Mp4.u32(0) + Mp4.u32(sizes.size.toLong()) + Mp4.u32Array(sizes)))

            if (chunkOffsets.any { it > 0xFFFFFFFFL }) {
                val payload = ByteArrayOutputStream()
                payload.write(Mp4.u32(chunkOffsets.size.toLong()))
                for (o in chunkOffsets) payload.write(Mp4.u64(o))
                out.write(Mp4.fullBox("co64", 0, 0, payload.toByteArray()))
            } else {
                val payload = ByteArrayOutputStream()
                payload.write(Mp4.u32(chunkOffsets.size.toLong()))
                for (o in chunkOffsets) payload.write(Mp4.u32(o))
                out.write(Mp4.fullBox("stco", 0, 0, payload.toByteArray()))
            }

            if (syncSamples != null && syncSamples.size < sizes.size) {
                out.write(Mp4.fullBox("stss", 0, 0, Mp4.u32(syncSamples.size.toLong()) + Mp4.u32Array(syncSamples)))
            }
            return out.toByteArray()
        }

        private fun runs(values: IntList): IntList {
            val out = IntList()
            for (i in 0 until values.size) {
                val v = values[i]
                if (out.size > 0 && out[out.size - 1] == v) {
                    out[out.size - 2] = out[out.size - 2] + 1
                } else {
                    out.add(1); out.add(v)
                }
            }
            return out
        }

        companion object {
            fun load(init: InitInfo, trackId: Int, executor: java.util.concurrent.ExecutorService): Track {
                val trex = init.trex
                val trexDuration = Mp4.u32(trex, 20).toInt()
                val trexSize = Mp4.u32(trex, 24).toInt()
                val trexFlags = Mp4.u32(trex, 28).toInt()

                val moofs = init.fragments.map { fragment ->
                    executor.submit(Callable { readMoof(fragment) })
                }.map { it.get() }

                val chunks = ArrayList<Chunk>(moofs.size)
                val durations = IntList()
                val sizes = IntList()
                val compositionOffsets = IntList()
                val syncSamples = IntList()
                var hasComposition = false
                var time = 0L

                for ((fragmentIndex, moofData) in moofs.withIndex()) {
                    val moof = moofData.bytes
                    val moofOffset = moofs[fragmentIndex].offset
                    for (traf in Mp4.boxes(moof, 8, moof.size)) {
                        if (traf.type != "traf") continue
                        var defaultDuration = trexDuration
                        var defaultSize = trexSize
                        var defaultFlags = trexFlags
                        var baseOffset = moofOffset

                        for (b in Mp4.boxes(moof, traf.contentStart, traf.end)) {
                            val flags = Mp4.u24(moof, b.offset + 9)
                            if (b.type == "tfhd") {
                                var q = b.offset + 16
                                if (flags and 0x01 != 0) { baseOffset = Mp4.u64(moof, q); q += 8 }
                                if (flags and 0x02 != 0) q += 4
                                if (flags and 0x08 != 0) { defaultDuration = Mp4.u32(moof, q).toInt(); q += 4 }
                                if (flags and 0x10 != 0) { defaultSize = Mp4.u32(moof, q).toInt(); q += 4 }
                                if (flags and 0x20 != 0) { defaultFlags = Mp4.u32(moof, q).toInt() }
                            } else if (b.type == "trun") {
                                val version = moof[b.offset + 8].toInt()
                                val count = Mp4.u32(moof, b.offset + 12).toInt()
                                var q = b.offset + 16
                                var dataOffset = 0L
                                if (flags and 0x01 != 0) { dataOffset = Mp4.i32(moof, q).toLong(); q += 4 }
                                var firstFlags: Int? = null
                                if (flags and 0x04 != 0) { firstFlags = Mp4.u32(moof, q).toInt(); q += 4 }

                                val chunkStart = time
                                var chunkSize = 0L
                                for (i in 0 until count) {
                                    var duration = defaultDuration
                                    var size = defaultSize
                                    var sampleFlags = defaultFlags
                                    var composition = 0
                                    if (flags and 0x100 != 0) { duration = Mp4.u32(moof, q).toInt(); q += 4 }
                                    if (flags and 0x200 != 0) { size = Mp4.u32(moof, q).toInt(); q += 4 }
                                    if (flags and 0x400 != 0) { sampleFlags = Mp4.u32(moof, q).toInt(); q += 4 }
                                    if (flags and 0x800 != 0) {
                                        composition = if (version == 0) Mp4.u32(moof, q).toInt() else Mp4.i32(moof, q)
                                        q += 4
                                        hasComposition = true
                                    }
                                    if (i == 0 && firstFlags != null) sampleFlags = firstFlags

                                    durations.add(duration)
                                    sizes.add(size)
                                    compositionOffsets.add(composition)
                                    if ((sampleFlags ushr 16) and 1 == 0) syncSamples.add(sizes.size)
                                    chunkSize += size.toLong() and 0xFFFFFFFFL
                                    time += duration.toLong() and 0xFFFFFFFFL
                                }
                                chunks.add(Chunk(init.fragments[fragmentIndex].reader, baseOffset + dataOffset, chunkSize, count, chunkStart))
                            }
                        }
                    }
                }

                return Track(init, trackId, init.trak, init.mediaTimescale, chunks, durations, sizes,
                    if (hasComposition) compositionOffsets else null, syncSamples, time)
            }

            /**
             * Reads the moof box of a fragment, skipping boxes that may precede it (styp in HLS segments, sidx, ...).
             */
            private fun readMoof(fragment: InitInfo.Fragment): MoofData {
                val reader = fragment.reader
                var offset = fragment.offset
                for (attempt in 0 until MAX_MOOF_PROBES) {
                    val probeSize = if (fragment.size < 0) MOOF_PROBE_SIZE.toLong() else minOf(MOOF_PROBE_SIZE.toLong(), fragment.size - (offset - fragment.offset))
                    val probe = reader.readUpTo(offset, probeSize.toInt())
                    var pos = 0
                    while (pos + 8 <= probe.size) {
                        val size = Mp4.u32(probe, pos)
                        val type = String(probe, pos + 4, 4, Charsets.US_ASCII)
                        if (type == "moof") {
                            val moofOffset = offset + pos
                            return MoofData(moofOffset, if (pos + size <= probe.size) probe.copyOfRange(pos, pos + size.toInt()) else reader.read(moofOffset, size.toInt()))
                        }
                        if (type !in SKIPPABLE_BOXES || size < 8) {
                            throw IllegalStateException("Fragment at ${fragment.offset} has unexpected box '$type' before moof")
                        }
                        pos += size.toInt()
                    }
                    if (probe.size < 8) break
                    offset += pos
                }
                throw IllegalStateException("No moof box found in fragment at ${fragment.offset}")
            }
        }
    }
}

/**
 * Minimal growable int array, avoids boxing for the large per-sample tables.
 */
internal class IntList {
    private var data = IntArray(256)
    var size = 0
        private set

    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    operator fun get(index: Int): Int = data[index]
    operator fun set(index: Int, value: Int) { data[index] = value }
    fun toIntArray(): IntArray = data.copyOf(size)
}

internal object Mp4 {
    class Box(val type: String, val offset: Int, val size: Int, val headerSize: Int) {
        val end: Int get() = offset + size
        val contentStart: Int get() = offset + headerSize
    }

    fun boxes(data: ByteArray, start: Int, end: Int): List<Box> {
        val result = ArrayList<Box>()
        var pos = start
        while (pos + 8 <= end) {
            var size = u32(data, pos)
            var headerSize = 8
            if (size == 1L) {
                size = u64(data, pos + 8)
                headerSize = 16
            } else if (size == 0L) {
                size = (end - pos).toLong()
            }
            if (size < headerSize || pos + size > end) break
            result.add(Box(String(data, pos + 4, 4, Charsets.US_ASCII), pos, size.toInt(), headerSize))
            pos += size.toInt()
        }
        return result
    }

    fun find(data: ByteArray, type: String, start: Int, end: Int): Box? = boxes(data, start, end).firstOrNull { it.type == type }

    fun timescale(fullBoxWithTimescale: ByteArray): Long =
        if (fullBoxWithTimescale[8].toInt() == 0) u32(fullBoxWithTimescale, 20) else u32(fullBoxWithTimescale, 28)

    fun u16(data: ByteArray, pos: Int): Int = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
    fun u24(data: ByteArray, pos: Int): Int = ((data[pos].toInt() and 0xFF) shl 16) or ((data[pos + 1].toInt() and 0xFF) shl 8) or (data[pos + 2].toInt() and 0xFF)
    fun u32(data: ByteArray, pos: Int): Long =
        ((data[pos].toLong() and 0xFF) shl 24) or ((data[pos + 1].toLong() and 0xFF) shl 16) or ((data[pos + 2].toLong() and 0xFF) shl 8) or (data[pos + 3].toLong() and 0xFF)
    fun i32(data: ByteArray, pos: Int): Int = u32(data, pos).toInt()
    fun u64(data: ByteArray, pos: Int): Long = (u32(data, pos) shl 32) or u32(data, pos + 4)

    fun putU32(data: ByteArray, pos: Int, value: Long) {
        data[pos] = (value ushr 24).toByte(); data[pos + 1] = (value ushr 16).toByte()
        data[pos + 2] = (value ushr 8).toByte(); data[pos + 3] = value.toByte()
    }
    fun putU64(data: ByteArray, pos: Int, value: Long) {
        putU32(data, pos, value ushr 32)
        putU32(data, pos + 4, value and 0xFFFFFFFFL)
    }

    fun u32(value: Long): ByteArray = ByteArray(4).also { putU32(it, 0, value) }
    fun u32(value: Int): ByteArray = u32(value.toLong() and 0xFFFFFFFFL)
    fun u64(value: Long): ByteArray = ByteArray(8).also { putU64(it, 0, value) }

    fun u32Array(values: IntList): ByteArray {
        val out = ByteArray(values.size * 4)
        for (i in 0 until values.size) putU32(out, i * 4, values[i].toLong() and 0xFFFFFFFFL)
        return out
    }

    fun box(type: String, payload: ByteArray): ByteArray = u32(8L + payload.size) + type.toByteArray(Charsets.US_ASCII) + payload
    fun fullBox(type: String, version: Int, flags: Int, payload: ByteArray): ByteArray = box(type, u32(((version shl 24) or flags).toLong() and 0xFFFFFFFFL) + payload)
}
