package com.futo.platformplayer

import com.futo.platformplayer.casting.dlna.FlatMp4Muxer
import com.futo.platformplayer.casting.dlna.RangeReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlatMp4MuxerTests {
    private class BytesRangeReader(private val data: ByteArray) : RangeReader {
        var requests = 0
        override fun read(offset: Long, length: Int): ByteArray {
            requests++
            return data.copyOfRange(offset.toInt(), offset.toInt() + length)
        }
        override fun copyTo(offset: Long, length: Long, output: OutputStream) {
            requests++
            output.write(data, offset.toInt(), length.toInt())
        }
        override fun readAll(): ByteArray = data
        override fun readUpTo(offset: Long, maxLength: Int): ByteArray =
            data.copyOfRange(minOf(offset.toInt(), data.size), minOf(offset.toInt() + maxLength, data.size))
    }

    private class FileRangeReader(path: String) : RangeReader {
        private val file = RandomAccessFile(path, "r")
        override fun read(offset: Long, length: Int): ByteArray = synchronized(file) {
            ByteArray(length).also { file.seek(offset); file.readFully(it) }
        }
        override fun copyTo(offset: Long, length: Long, output: OutputStream) {
            output.write(read(offset, length.toInt()))
        }
        override fun readAll(): ByteArray = read(0, file.length().toInt())
        override fun readUpTo(offset: Long, maxLength: Int): ByteArray = read(offset, minOf(maxLength.toLong(), file.length() - offset).toInt())
    }

    private fun resource(name: String): ByteArray = javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes()

    //Fixture box layout (generated with ffmpeg, see commit message): video init 0-776, sidx 777-888; audio init 0-727, sidx 728-803.
    private fun fixtureMuxer(): Triple<FlatMp4Muxer, ByteArray, ByteArray> {
        val video = resource("dlna/video.mp4")
        val audio = resource("dlna/audio.m4a")
        val muxer = FlatMp4Muxer.create(
            FlatMp4Muxer.Source(BytesRangeReader(video), 0, 776, 777, 888),
            FlatMp4Muxer.Source(BytesRangeReader(audio), 0, 727, 728, 803)
        )
        return Triple(muxer, video, audio)
    }

    private fun boxes(data: ByteArray, start: Int, end: Int): List<Triple<String, Int, Int>> {
        val result = ArrayList<Triple<String, Int, Int>>()
        var pos = start
        while (pos + 8 <= end) {
            val size = ((data[pos].toInt() and 0xFF) shl 24) or ((data[pos + 1].toInt() and 0xFF) shl 16) or ((data[pos + 2].toInt() and 0xFF) shl 8) or (data[pos + 3].toInt() and 0xFF)
            result.add(Triple(String(data, pos + 4, 4), pos, size))
            pos += size
        }
        return result
    }

    private fun u32(data: ByteArray, pos: Int): Int =
        ((data[pos].toInt() and 0xFF) shl 24) or ((data[pos + 1].toInt() and 0xFF) shl 16) or ((data[pos + 2].toInt() and 0xFF) shl 8) or (data[pos + 3].toInt() and 0xFF)

    private fun sampleCounts(output: ByteArray, moov: Triple<String, Int, Int>): List<Int> {
        val counts = ArrayList<Int>()
        fun walk(start: Int, end: Int) {
            for ((type, pos, size) in boxes(output, start, end)) {
                when (type) {
                    "trak", "mdia", "minf", "stbl" -> walk(pos + 8, pos + size)
                    "stsz" -> counts.add(u32(output, pos + 16))
                }
            }
        }
        walk(moov.second + 8, moov.second + moov.third)
        return counts
    }

    private fun mdatPayloads(data: ByteArray): Long =
        boxes(data, 0, data.size).filter { it.first == "mdat" }.sumOf { it.third.toLong() - 8 }

    @Test
    fun producesFlatFileWithBothTracks() {
        val (muxer, video, audio) = fixtureMuxer()
        val output = ByteArrayOutputStream().also { muxer.writeTo(it, 0, muxer.size - 1) }.toByteArray()

        assertEquals(muxer.size, output.size.toLong())
        val top = boxes(output, 0, output.size)
        assertEquals(listOf("ftyp", "moov", "mdat"), top.map { it.first })
        assertEquals(output.size, top.last().second + top.last().third)

        val moov = top[1]
        assertTrue(boxes(output, moov.second + 8, moov.second + moov.third).none { it.first == "mvex" })
        assertEquals(listOf(144, 260), sampleCounts(output, moov))

        //Sample data is copied verbatim, so the flat mdat holds exactly the sources' mdat payloads.
        assertEquals(mdatPayloads(video) + mdatPayloads(audio), top.last().third.toLong() - 8)
        assertEquals(6.0, muxer.durationSeconds, 0.1)
    }

    @Test
    fun rangeReadsMatchFullOutput() {
        val (muxer, _, _) = fixtureMuxer()
        val full = ByteArrayOutputStream().also { muxer.writeTo(it, 0, muxer.size - 1) }.toByteArray()
        val random = Random(1234)
        repeat(200) {
            val start = random.nextLong(muxer.size)
            val end = minOf(muxer.size - 1, start + random.nextLong(30000))
            val part = ByteArrayOutputStream().also { muxer.writeTo(it, start, end) }.toByteArray()
            assertContentEquals(full.copyOfRange(start.toInt(), end.toInt() + 1), part, "range $start-$end")
        }
    }

    @Test
    fun buildsAudioOnlyFileFromHlsSegments() {
        //fMP4 HLS as served by SoundCloud: init.mp4 plus segments that start with a styp box, the last one tiny.
        val init = FlatMp4Muxer.SegmentSource.Segment(BytesRangeReader(resource("dlna/hls/init.mp4")))
        val segments = (0..3).map { FlatMp4Muxer.SegmentSource.Segment(BytesRangeReader(resource("dlna/hls/data00$it.m4s"))) }
        val muxer = FlatMp4Muxer.create(null, FlatMp4Muxer.SegmentSource(init, segments))
        val output = ByteArrayOutputStream().also { muxer.writeTo(it, 0, muxer.size - 1) }.toByteArray()

        assertEquals("audio/mp4", muxer.contentType)
        val top = boxes(output, 0, output.size)
        assertEquals(listOf("ftyp", "moov", "mdat"), top.map { it.first })
        assertEquals("M4A ", String(output, 8, 4))
        assertEquals(listOf(260), sampleCounts(output, top[1]))
        assertEquals(6.0, muxer.durationSeconds, 0.1)
    }

    /**
     * Manual check against real streams: set DLNA_MUX_VIDEO, DLNA_MUX_AUDIO (with *_RANGES as "initEnd,indexStart,indexEnd")
     * and DLNA_MUX_OUT, then verify the output with ffprobe/ffmpeg.
     */
    @Test
    fun writesLocalFilesWhenConfigured() {
        val videoPath = System.getenv("DLNA_MUX_VIDEO") ?: return
        val audioPath = System.getenv("DLNA_MUX_AUDIO") ?: return
        val out = System.getenv("DLNA_MUX_OUT") ?: return
        val v = System.getenv("DLNA_MUX_VIDEO_RANGES")!!.split(",").map { it.toLong() }
        val a = System.getenv("DLNA_MUX_AUDIO_RANGES")!!.split(",").map { it.toLong() }
        val muxer = FlatMp4Muxer.create(
            FlatMp4Muxer.Source(FileRangeReader(videoPath), 0, v[0], v[1], v[2]),
            FlatMp4Muxer.Source(FileRangeReader(audioPath), 0, a[0], a[1], a[2])
        )
        File(out).outputStream().buffered().use { muxer.writeTo(it, 0, muxer.size - 1) }
    }
}
