package com.futo.platformplayer.api.http.server.handlers

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.futo.platformplayer.api.http.server.HttpContext
import com.futo.platformplayer.api.http.server.HttpHeaders
import com.futo.platformplayer.logging.Logger
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class HttpContentUriHandler(
    method: String,
    path: String,
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val explicitContentType: String? = null
) : HttpHandler(method, path) {

    override fun handle(httpContext: HttpContext) {
        val resolver = contentResolver
        val requestHeaders = httpContext.headers
        val responseHeaders = this.headers.clone()

        val meta = try {
            queryMetadata(resolver, uri)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to query metadata for $uri", e)
            httpContext.respondCode(404, responseHeaders)
            return
        }

        val contentType = explicitContentType
            ?: resolver.getType(uri)
            ?: "application/octet-stream"
        responseHeaders["Content-Type"] = contentType

        meta.lastModifiedMillis?.let { lastModified ->
            responseHeaders["Last-Modified"] = httpDateFormat.format(Date(lastModified))

            val ifModifiedSinceHeader = requestHeaders["If-Modified-Since"]
            if (ifModifiedSinceHeader != null) {
                val ifModifiedSince = try {
                    httpDateFormat.parse(ifModifiedSinceHeader)
                } catch (_: Exception) {
                    null
                }

                if (ifModifiedSince != null && lastModified <= ifModifiedSince.time) {
                    httpContext.respondCode(304, responseHeaders)
                    return
                }
            }
        }

        val safeName = (meta.displayName ?: "content.bin").replace("\"", "\\\"")
        responseHeaders["Content-Disposition"] = "attachment; filename=\"$safeName\""

        val length = meta.size
        if (length == null) {
            Logger.i(TAG, "Streaming $uri with unknown length; Range not supported")
            responseHeaders.remove("Content-Length")
            responseHeaders.remove("Content-Range")
            responseHeaders.remove("Accept-Ranges")

            stream(
                httpContext = httpContext,
                resolver = resolver,
                uri = uri,
                statusCode = 200,
                headers = responseHeaders,
                start = null,
                length = null
            )
            return
        }

        responseHeaders["Accept-Ranges"] = "bytes"

        val rangeHeader = requestHeaders["Range"]
        if (rangeHeader.isNullOrBlank()) {
            responseHeaders["Content-Length"] = length.toString()
            Logger.i(TAG, "Sending full content for $uri, length=$length")

            stream(
                httpContext = httpContext,
                resolver = resolver,
                uri = uri,
                statusCode = 200,
                headers = responseHeaders,
                start = 0L,
                length = length
            )
            return
        }

        val range = parseRange(rangeHeader, length)
        if (range == null) {
            Logger.w(TAG, "Invalid Range '$rangeHeader' for $uri (length=$length)")
            responseHeaders["Content-Range"] = "bytes */$length"
            httpContext.respondCode(416, responseHeaders)
            return
        }

        val start = range.first
        val endInclusive = range.last
        val bytesToSend = endInclusive - start + 1

        responseHeaders["Content-Range"] = "bytes $start-$endInclusive/$length"
        responseHeaders["Content-Length"] = bytesToSend.toString()
        Logger.i(TAG, "Sending range $start-$endInclusive (length=$bytesToSend) of $length for $uri")

        stream(
            httpContext = httpContext,
            resolver = resolver,
            uri = uri,
            statusCode = 206,
            headers = responseHeaders,
            start = start,
            length = bytesToSend
        )
    }

    data class ContentMeta(
        val displayName: String?,
        val size: Long?,
        val lastModifiedMillis: Long?
    )

    private fun queryMetadata(resolver: ContentResolver, uri: Uri): ContentMeta {
        var displayName: String? = null
        var size: Long? = null
        var lastModifiedMillis: Long? = null

        val projection = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_ADDED
        )

        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex)
                }

                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    val s = cursor.getLong(sizeIndex)
                    if (s >= 0) size = s   // -1 means unknown
                }

                val dateModifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                if (dateModifiedIndex != -1 && !cursor.isNull(dateModifiedIndex)) {
                    val seconds = cursor.getLong(dateModifiedIndex)
                    if (seconds > 0) {
                        lastModifiedMillis = seconds * 1000L
                    }
                }

                if (lastModifiedMillis == null) {
                    val dateAddedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                    if (dateAddedIndex != -1 && !cursor.isNull(dateAddedIndex)) {
                        val seconds = cursor.getLong(dateAddedIndex)
                        if (seconds > 0) {
                            lastModifiedMillis = seconds * 1000L
                        }
                    }
                }
            }
        }

        if (size == null) {
            try {
                resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    val assetLen = afd.length
                    if (assetLen >= 0) {
                        size = assetLen
                    }
                }
            } catch (_: Exception) { }
        }

        return ContentMeta(displayName = displayName, size = size, lastModifiedMillis = lastModifiedMillis)
    }

    private fun parseRange(header: String, totalLength: Long): LongRange? {
        if (totalLength <= 0L) return null

        val prefix = "bytes="
        if (!header.startsWith(prefix, ignoreCase = true)) return null

        val spec = header.substring(prefix.length).trim()
        if (spec.isEmpty()) return null

        if (spec.contains(",")) return null

        val dashIndex = spec.indexOf('-')
        if (dashIndex < 0) return null

        val startPart = spec.substring(0, dashIndex).trim()
        val endPart = spec.substring(dashIndex + 1).trim()

        return when {
            startPart.isNotEmpty() -> {
                val start = startPart.toLongOrNull() ?: return null
                if (start < 0 || start >= totalLength) return null

                val end = if (endPart.isNotEmpty()) {
                    val rawEnd = endPart.toLongOrNull() ?: return null
                    if (rawEnd < start) return null
                    rawEnd.coerceAtMost(totalLength - 1)
                } else {
                    totalLength - 1
                }

                start..end
            }

            endPart.isNotEmpty() -> {
                val suffixLen = endPart.toLongOrNull() ?: return null
                if (suffixLen <= 0L) return null

                if (suffixLen >= totalLength) {
                    0L..(totalLength - 1)
                } else {
                    val start = totalLength - suffixLen
                    val end = totalLength - 1
                    start..end
                }
            }

            else -> null
        }
    }

    private fun stream(httpContext: HttpContext, resolver: ContentResolver, uri: Uri, statusCode: Int, headers: HttpHeaders, start: Long?, length: Long?) {
        try {
            val input = resolver.openInputStream(uri)
            if (input == null) {
                Logger.w(TAG, "Content not found: $uri")
                httpContext.respondCode(404, headers)
                return
            }

            input.use { inputStream ->
                httpContext.respond(statusCode, headers) { outputStream ->
                    try {
                        val offset = start ?: 0L
                        if (offset > 0L) {
                            skipFully(inputStream, offset)
                        }
                        copyStream(inputStream, outputStream, length)
                        outputStream.flush()
                    } catch (e: Exception) {
                        Logger.e(TAG, "Error while streaming $uri (start=$start, length=$length)", e)
                    }
                }
            }
        } catch (e: FileNotFoundException) {
            Logger.w(TAG, "Content not found: $uri", e)
            httpContext.respondCode(404, headers)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to open stream for $uri", e)
            httpContext.respondCode(500, headers)
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream, limit: Long?) {
        val buffer = ByteArray(8192)
        if (limit == null) {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
            }
        } else {
            var remaining = limit
            while (remaining > 0L) {
                val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                val read = input.read(buffer, 0, toRead)
                if (read < 0) break
                output.write(buffer, 0, read)
                remaining -= read.toLong()
            }
        }
    }

    private fun skipFully(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped <= 0L) {
                val b = input.read()
                if (b == -1) break
                remaining -= 1L
            } else {
                remaining -= skipped
            }
        }
    }

    companion object {
        private const val TAG = "HttpContentUriHandler"

        private val httpDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
    }
}
