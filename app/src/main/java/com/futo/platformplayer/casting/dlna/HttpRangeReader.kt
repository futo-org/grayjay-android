package com.futo.platformplayer.casting.dlna

import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.models.modifier.IRequestModifier
import com.futo.platformplayer.logging.Logger
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class HttpRangeReader(
    private val client: ManagedHttpClient,
    private val url: String,
    private val requestModifier: IRequestModifier?
) : RangeReader {
    override fun read(offset: Long, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val out = ByteArrayOutputStream(length)
        copyTo(offset, length.toLong(), out)
        return out.toByteArray()
    }

    override fun readUpTo(offset: Long, maxLength: Int): ByteArray {
        if (maxLength <= 0) return ByteArray(0)
        val out = ByteArrayOutputStream(maxLength)
        request(offset, maxLength.toLong(), out, exact = false)
        return out.toByteArray()
    }

    override fun copyTo(offset: Long, length: Long, output: OutputStream) = request(offset, length, output, exact = true)

    private fun request(offset: Long, length: Long, output: OutputStream, exact: Boolean) {
        if (length <= 0) return
        client.get(url, mutableMapOf("Range" to "bytes=$offset-${offset + length - 1}"), requestModifier).use { response ->
            if (!exact && response.code == 416) return
            val body = response.body ?: throw IOException("Range request for $offset+$length returned no body")
            val contentLength = body.contentLength()

            //Some request modifiers move the range into the URL (googlevideo "&range="), which answers 200 with only the slice.
            val isSlice = when (response.code) {
                206 -> true
                200 -> contentLength == length || offset == 0L || (!exact && contentLength in 0..length)
                else -> false
            }
            if (!isSlice) {
                Logger.w(TAG, "Unexpected range response: HTTP ${response.code}, content-length=$contentLength, content-range=${response.getHeaderFlat("content-range")}, requested $offset+$length")
                throw IOException("Range request for $offset+$length failed with HTTP ${response.code}")
            }

            copyUpTo(body.byteStream(), output, length, exact)
        }
    }

    override fun readAll(): ByteArray {
        client.get(url, mutableMapOf(), requestModifier).use { response ->
            if (!response.isOk) throw IOException("Request failed with HTTP ${response.code}")
            return response.body?.bytes() ?: throw IOException("Request returned no body")
        }
    }

    private fun copyUpTo(input: InputStream, output: OutputStream, length: Long, exact: Boolean) {
        val buffer = ByteArray(64 * 1024)
        var remaining = length
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) {
                if (exact) throw IOException("Source ended $remaining bytes early")
                return
            }
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    companion object {
        private const val TAG = "HttpRangeReader"
    }
}
