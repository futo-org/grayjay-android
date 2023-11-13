package com.futo.platformplayer.api.http.server

import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.api.http.server.exceptions.EmptyRequestException
import com.futo.platformplayer.api.http.server.exceptions.KeepAliveTimeoutException
import com.futo.platformplayer.api.media.Serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.OutputStream
import java.io.StringWriter
import java.net.SocketTimeoutException

class HttpContext : AutoCloseable {
    private val _stream: BufferedReader;
    private var _responseStream: OutputStream? = null;
    
    var id: String? = null;
    
    var head: String = "";
    var headers: HttpHeaders = HttpHeaders();

    var method: String = "";
    var path: String = "";
    var query = mutableMapOf<String, String>();

    var contentType: String? = null;
    var contentLength: Long = 0;

    var keepAlive: Boolean = false;
    var keepAliveTimeout: Int = 0;
    var keepAliveMax: Int = 0;

    var _totalRead: Long = 0;

    var statusCode: Int = -1;

    private val _responseHeaders: HttpHeaders = HttpHeaders();


    constructor(stream: BufferedReader, responseStream: OutputStream? = null, requestId: String? = null, timeout: Int? = null) {
        _stream = stream;
        _responseStream = responseStream;
        this.id = requestId;

        try {
            head = stream.readLine() ?: throw EmptyRequestException("No head found");
        }
        catch(ex: SocketTimeoutException) {
            if((timeout ?: 0) > 0)
                throw KeepAliveTimeoutException("Keep-Alive timedout", ex);
            throw ex;
        }

        val methodEndIndex = head.indexOf(' ');
        val urlEndIndex = head.indexOf(' ', methodEndIndex + 1);
        if (methodEndIndex == -1 || urlEndIndex == -1) {
            Logger.w(TAG, "Skipped request, wrong format.");
            throw IllegalStateException("Invalid request");
        }

        method = head.substring(0, methodEndIndex);
        path = head.substring(methodEndIndex + 1, urlEndIndex);

        if (path.contains("?")) {
            val queryPartIndex = path.indexOf("?");
            val queryParts = path.substring(queryPartIndex + 1).split("&");
            path = path.substring(0, queryPartIndex);

            for(queryPart in queryParts) {
                val eqIndex = queryPart.indexOf("=");
                if(eqIndex > 0)
                    query.put(queryPart.substring(0, eqIndex), queryPart.substring(eqIndex + 1));
                else
                    query.put(queryPart, "");
            }
        }

        while (true) {
            val line = stream.readLine();
            val headerEndIndex = line.indexOf(":");
            if (headerEndIndex == -1)
                break;

            val headerKey = line.substring(0, headerEndIndex).lowercase()
            val headerValue = line.substring(headerEndIndex + 1).trim();
            headers[headerKey] = headerValue;

            when(headerKey) {
                "content-length" -> contentLength = headerValue.toLong();
                "content-type" -> contentType = headerValue;
                "connection" -> keepAlive = headerValue.lowercase() == "keep-alive";
                "keep-alive" -> {
                    val keepAliveParams = headerValue.split(",");
                    for(keepAliveParam in keepAliveParams) {
                        val eqIndex = keepAliveParam.indexOf("=");
                        if(eqIndex > 0){
                            when(keepAliveParam.substring(0, eqIndex)) {
                                "timeout" -> keepAliveTimeout = keepAliveParam.substring(eqIndex+1).toInt();
                                "max" -> keepAliveTimeout = keepAliveParam.substring(eqIndex+1).toInt();
                            }
                        }
                    }
                }
            }
            if(line.isNullOrEmpty())
                break;
        }
    }

    fun getHttpHeaderString(): String {
        val writer = StringWriter();
        writer.write(head + "\r\n");
        for(header in headers) {
            writer.write("${header.key}: ${header.value}\r\n");
        }
        writer.write("\r\n");
        return writer.toString();
    }

    fun getHeader(header: String) : String? {
        return headers[header.lowercase()];
    }
    fun setResponseHeaders(vararg respHeaders: Pair<String, String>) {
        for(header in respHeaders)
            _responseHeaders.put(header.first, header.second);
    }
    fun setResponseHeaders(respHeaders: HttpHeaders) {
        for(header in respHeaders)
            _responseHeaders.put(header.key, header.value);
    }

    inline fun <reified T> respondJson(status: Int, body: T) {
        respondCode(status, Json.encodeToString(body), "application/json");
    }
    fun respondCode(status: Int, body: String = "", contentType: String = "text/plain") {
        respondCode(status, HttpHeaders(Pair("Content-Type", contentType)), body);
    }
    fun respondCode(status: Int, headers: HttpHeaders, body: String? = null) {
        val bytes = body?.toByteArray(Charsets.UTF_8);
        if(body != null && headers.get("content-length").isNullOrEmpty())
            headers.put("content-length", bytes!!.size.toString());
        respond(status, headers) { responseStream ->
            if(body != null) {
                responseStream.write(bytes!!);
            }
        }
    }
    fun respond(status: Int, headers: HttpHeaders, writing: (OutputStream)->Unit) {
        val responseStream = _responseStream ?: throw IllegalStateException("No response stream set");

        val headersToRespond = headers.toMutableMap();

        for(preHeader in _responseHeaders)
            if(!headersToRespond.containsKey(preHeader.key))
                headersToRespond.put(preHeader.key, preHeader.value);

        if(keepAlive) {
            headersToRespond.put("connection", "keep-alive");
            headersToRespond.put("keep-alive", "timeout=5, max=1000");
        }

        val responseHeader = HttpResponse(status, headers);

        responseStream.write(responseHeader.getHttpHeaderBytes());

        if(method != "HEAD") {
            writing(responseStream);
            responseStream.flush();
        }
        statusCode = status;
    }

    fun readContentBytes(buffer: CharArray, length: Int) : Int {
        val reading = Math.min(length, (contentLength - _totalRead).toInt());
        val read = _stream.read(buffer, 0, reading);
        _totalRead += read;

        //TODO: Fix this properly
        if(contentLength - _totalRead < 400 && read < length) {
            _totalRead = contentLength;
        }
        return read;
    }
    fun readContentString() : String{
        val writer = StringWriter();
        var read = 0;
        val buffer = CharArray(4096);
        do {
            read = readContentBytes(buffer, buffer.size);
            writer.write(buffer, 0, read);
        } while(read > 0);// && _stream.ready());
        //if(!_stream.ready())
        //    _totalRead = contentLength;
        return writer.toString();
    }
    inline fun <reified T> readContentJson() : T {
        return Serializer.json.decodeFromString(readContentString());
    }
    fun skipBody() {
        if(contentLength > 0)
            _stream.skip(contentLength - _totalRead);
    }

    override fun close() {
        if(!keepAlive) {
            _stream?.close();
            _responseStream?.close();
        }
    }

    companion object {
        private val TAG = "HttpRequest";
        private val statusCodeMap = mapOf(
            100 to "Continue",
            101 to "Switching Protocols",
            102 to "Processing (WebDAV)",
            200 to "OK",
            201 to "Created",
            202 to "Accepted",
            203 to "Non-Authoritative Information",
            204 to "No Content",
            205 to "Reset Content",
            206 to "Partial Content",
            207 to "Multi-Status (WebDAV)",
            208 to "Already Reported (WebDAV)",
            226 to "IM Used",
            300 to "Multiple Choices",
            301 to "Moved Permanently",
            302 to "Found",
            303 to "See Other",
            304 to "Not Modified",
            305 to "Use Proxy",
            306 to "(Unused)",
            307 to "Temporary Redirect",
            308 to "Permanent Redirect (experimental)",
            400 to "Bad Request",
            401 to "Unauthorized",
            402 to "Payment Required",
            403 to "Forbidden",
            404 to "Not Found",
            405 to "Method Not Allowed",
            406 to "Not Acceptable",
            407 to "Proxy Authentication Required",
            408 to "Request Timeout",
            409 to "Conflict",
            410 to "Gone",
            411 to "Length Required",
            412 to "Precondition Failed",
            413 to "Request Entity Too Large",
            414 to "Request-URI Too Long",
            415 to "Unsupported Media Type",
            416 to "Requested Range Not Satisfiable",
            417 to "Expectation Failed",
            418 to "I'm a teapot (RFC 2324)",
            420 to "Enhance Your Calm (Twitter)",
            422 to "Unprocessable Entity (WebDAV)",
            423 to "Locked (WebDAV)",
            424 to "Failed Dependency (WebDAV)",
            425 to "Reserved for WebDAV",
            426 to "Upgrade Required",
            428 to "Precondition Required",
            429 to "Too Many Requests",
            431 to "Request Header Fields Too Large",
            444 to "No Response (Nginx)",
            449 to "Retry With (Microsoft)",
            450 to "Blocked by Windows Parental Controls (Microsoft)",
            451 to "Unavailable For Legal Reasons",
            499 to "Client Closed Request (Nginx)",
            500 to "Internal Server Error",
            501 to "Not Implemented",
            502 to "Bad Gateway",
            503 to "Service Unavailable",
            504 to "Gateway Timeout",
            505 to "HTTP Version Not Supported",
            506 to "Variant Also Negotiates (Experimental)",
            507 to "Insufficient Storage (WebDAV)",
            508 to "Loop Detected (WebDAV)",
            509 to "Bandwidth Limit Exceeded (Apache)",
            510 to "Not Extended",
            511 to "Network Authentication Required",
            598 to "Network read timeout error",
            599 to "Network connect timeout error",
        );
    }
}