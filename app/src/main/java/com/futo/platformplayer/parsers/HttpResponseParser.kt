package com.futo.platformplayer.parsers

import com.futo.platformplayer.api.http.server.HttpHeaders
import com.futo.platformplayer.api.http.server.exceptions.EmptyRequestException
import com.futo.platformplayer.readHttpHeaderBytes
import java.io.ByteArrayInputStream
import java.io.InputStream

class HttpResponseParser : AutoCloseable {
    private val _inputStream: InputStream;

    var head: String = "";
    var headers: HttpHeaders = HttpHeaders();

    var contentType: String? = null;
    var transferEncoding: String? = null;
    var contentLength: Long = -1L;

    var statusCode: Int = -1;

    constructor(inputStream: InputStream) {
        _inputStream = inputStream;

        val headerBytes = inputStream.readHttpHeaderBytes()
        ByteArrayInputStream(headerBytes).use {
            val reader = it.bufferedReader(Charsets.UTF_8)
            head = reader.readLine() ?: throw EmptyRequestException("No head found");

            val statusLineParts = head.split(" ")
            if (statusLineParts.size < 3) {
                throw IllegalStateException("Invalid status line")
            }

            statusCode = statusLineParts[1].toInt()

            while (true) {
                val line = reader.readLine();
                val headerEndIndex = line.indexOf(":");
                if (headerEndIndex == -1)
                    break;

                val headerKey = line.substring(0, headerEndIndex).lowercase()
                val headerValue = line.substring(headerEndIndex + 1).trim();
                headers[headerKey] = headerValue;

                when(headerKey) {
                    "content-length" -> contentLength = headerValue.toLong();
                    "content-type" -> contentType = headerValue;
                    "transfer-encoding" -> transferEncoding = headerValue;
                }
                if(line.isNullOrEmpty())
                    break;
            }
        }
    }

    override fun close() {
        _inputStream.close();
    }

    companion object {
        private val TAG = "HttpResponse";
    }
}