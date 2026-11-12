package com.futo.platformplayer.sabr.media3

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.futo.platformplayer.sabr.SabrException
import com.futo.platformplayer.sabr.SabrFormatKey
import com.futo.platformplayer.sabr.SabrSegment
import com.futo.platformplayer.sabr.SabrSession
import com.futo.platformplayer.sabr.SabrTrackBuffer

class SabrSegmentRef(val formatKey: SabrFormatKey, val sequenceNumber: Int, val isInit: Boolean) {
    fun toUri(): Uri = Uri.parse("sabr://${formatKey.itag}/${if (isInit) "init" else sequenceNumber.toString()}")
}

@UnstableApi
class SabrDataSource(private val session: SabrSession) : BaseDataSource(true) {

    class Factory(private val session: SabrSession) : DataSource.Factory {
        override fun createDataSource(): DataSource = SabrDataSource(session)
    }

    private var dataSpec: DataSpec? = null
    private var ref: SabrSegmentRef? = null
    private var buffer: SabrTrackBuffer? = null
    private var segment: SabrSegment? = null
    private var position = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        val ref = dataSpec.customData as? SabrSegmentRef
            ?: throw SabrException("SabrDataSource opened without a segment reference")

        transferInitializing(dataSpec)

        val buffer = session.bufferFor(ref.formatKey)
        val segment = resolve(buffer, ref)
            ?: throw SabrException("Segment ${ref.sequenceNumber} of ${ref.formatKey.itag} is no longer buffered")

        this.dataSpec = dataSpec
        this.ref = ref
        this.buffer = buffer
        this.segment = segment
        this.position = dataSpec.position.toInt()
        this.opened = true

        transferStarted(dataSpec)

        return if (segment.isComplete) (segment.size - position).toLong() else C.LENGTH_UNSET.toLong()
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val segment = this.segment ?: throw SabrException("SabrDataSource read before open")
        val buffer = this.buffer!!
        val ref = this.ref!!

        val deadline = System.currentTimeMillis() + READ_TIMEOUT_MS
        while (true) {
            val read = segment.read(position, target, offset, length)
            if (read > 0) {
                position += read
                bytesTransferred(read)
                return read
            }
            if (segment.isComplete) return C.RESULT_END_OF_INPUT

            session.fatalError?.let { throw sabrLoadException(it) }

            if (resolve(buffer, ref) !== segment)
                throw SabrException("Segment ${ref.sequenceNumber} was discarded before it completed")

            session.wakePump()
            if (System.currentTimeMillis() >= deadline)
                throw SabrException("Timed out waiting for segment ${ref.sequenceNumber} of ${ref.formatKey.itag}")
            buffer.awaitBytes(segment, position, READ_POLL_MS)
        }
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        dataSpec = null
        ref = null
        buffer = null
        segment = null
        position = 0
    }

    private fun resolve(buffer: SabrTrackBuffer, ref: SabrSegmentRef): SabrSegment? =
        if (ref.isInit) buffer.initSegment else buffer.get(ref.sequenceNumber)

    companion object {
        private const val READ_TIMEOUT_MS = 30_000L
        private const val READ_POLL_MS = 250L
    }
}
