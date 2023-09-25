package com.futo.platformplayer.api.http.server

import java.io.InputStream
import java.io.StringWriter

class HttpResponse : AutoCloseable {
    private var _stream: InputStream? = null;

    var head: String = "";
    var headers: Map<String, String>;

    var status: Int = 0;


    constructor(status: Int, headers: Map<String, String>) {
        head = "HTTP/1.1 ${status} ${statusCodeMap.get(status)}";
        this.status = status;
        this.headers = headers;
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
    fun getHttpHeaderBytes(): ByteArray {
        return getHttpHeaderString().toByteArray(Charsets.UTF_8);
    }


    override fun close() {
        _stream?.close();
    }

    companion object {
        private val REGEX_HEAD = Regex("(\\S+) (\\S+) (\\S+)");


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