package com.futo.platformplayer.api.http.server.handlers

import com.futo.platformplayer.api.http.server.HttpContext
import com.futo.platformplayer.api.http.server.HttpHeaders
import com.futo.platformplayer.logging.Logger
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPOutputStream

class HttpFileHandler(method: String, path: String, private val contentType: String, private val filePath: String): HttpHandler(method, path) {
    override fun handle(httpContext: HttpContext) {
        val requestHeaders = httpContext.headers;
        val responseHeaders = this.headers.clone();
        responseHeaders["Content-Type"] = contentType;

        val file = File(filePath);
        if (!file.exists()) {
            throw Exception("File does not exist.");
        }

        val lastModified = Files.getLastModifiedTime(file.toPath())
        responseHeaders["Last-Modified"] = httpDateFormat.format(Date(lastModified.toMillis()))

        val ifModifiedSince = requestHeaders["If-Modified-Since"]?.let { httpDateFormat.parse(it) }
        if (ifModifiedSince != null && lastModified.toMillis() <= ifModifiedSince.time) {
            httpContext.respondCode(304, headers)
            return
        }

        responseHeaders["Content-Disposition"] = "attachment; filename=\"${file.name.replace("\"", "\\\"")}\""

        val range = requestHeaders["Range"]
        val start: Long
        val end: Long
        if (range != null && range.startsWith("bytes=")) {
            val parts = range.substring(6).split("-")
            start = parts[0].toLong()
            end = parts.getOrNull(1)?.toLongOrNull() ?: (file.length() - 1)
            responseHeaders["Content-Range"] = "bytes $start-$end/${file.length()}"
        } else {
            start = 0
            end = file.length() - 1
        }

        var totalBytesSent = 0
        val contentLength = end - start + 1
        responseHeaders["Content-Length"] = contentLength.toString()
        Logger.i(TAG, "Sending $contentLength bytes (start: $start, end: $end)")

        file.inputStream().use { inputStream ->
            httpContext.respond(if (range != null) 206 else 200, responseHeaders) { responseStream ->
                try {
                    val buffer = ByteArray(8192)
                    inputStream.skip(start)
                    var current = start

                    val outputStream = responseStream
                    while (true) {
                        val expectedBytesRead = (end - current + 1).coerceAtMost(buffer.size.toLong());
                        val bytesRead = inputStream.read(buffer);
                        if (bytesRead < 0) {
                            Logger.i(TAG, "End of file reached")
                            break;
                        }

                        val bytesToSend = bytesRead.coerceAtMost(expectedBytesRead.toInt());
                        outputStream.write(buffer, 0, bytesToSend)

                        totalBytesSent += bytesToSend
                        Logger.v(TAG, "Sent bytes $current-${current + bytesToSend}, totalBytesSent=$totalBytesSent")

                        current += bytesToSend.toLong()
                        if (current >= end) {
                            Logger.i(TAG, "Expected amount of bytes sent")
                            break
                        }
                    }

                    Logger.i(TAG, "Finished sending file (segment)")
                    outputStream.flush()
                } catch (e: Exception) {
                    httpContext.respondCode(500, headers)
                }
            }
        }
    }

    companion object {
        private const val TAG = "HttpFileHandler"

        private val httpDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
    }
}