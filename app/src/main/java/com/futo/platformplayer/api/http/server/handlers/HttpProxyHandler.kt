package com.futo.platformplayer.api.http.server.handlers

import android.net.Uri
import android.util.Log
import com.futo.platformplayer.api.http.server.HttpContext
import com.futo.platformplayer.api.http.server.HttpHeaders
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.models.modifier.IRequest
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.parsers.HttpResponseParser
import com.futo.platformplayer.readLine
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

class HttpProxyHandler(method: String, path: String, val targetUrl: String, private val useTcp: Boolean = false): HttpHandler(method, path) {
    var content: String? = null;
    var contentType: String? = null;

    private val _ignoreRequestHeaders = mutableListOf<String>();
    private val _injectRequestHeader = mutableListOf<Pair<String, String>>();

    private val _ignoreResponseHeaders = mutableListOf<String>();

    private var _injectHost = false;
    private var _injectReferer = false;

    private val _client = ManagedHttpClient();
    private var _requestModifier: ((String, Map<String, String>) -> IRequest)? = null;

    override fun handle(context: HttpContext) {
        if (useTcp) {
            handleWithTcp(context)
        } else {
            handleWithOkHttp(context)
        }
    }

    private fun handleWithOkHttp(context: HttpContext) {
        val proxyHeaders = HashMap<String, String>();
        for (header in context.headers.filter { !_ignoreRequestHeaders.contains(it.key.lowercase()) })
            proxyHeaders[header.key] = header.value;
        for (injectHeader in _injectRequestHeader)
            proxyHeaders[injectHeader.first] = injectHeader.second;

        val req = _requestModifier?.invoke(targetUrl, proxyHeaders)
        var url = targetUrl
        if (req != null) {
            req.url?.let {
                url = it
            }
            req.headers.let {
                proxyHeaders.clear()
                proxyHeaders.putAll(it)
            }
        }

        val parsed = Uri.parse(url);
        if(_injectHost)
            proxyHeaders.put("Host", parsed.host!!);
        if(_injectReferer)
            proxyHeaders.put("Referer", url);

        val useMethod = if (method == "inherit") context.method else method;
        Logger.i(TAG, "handleWithOkHttp Proxied Request ${useMethod}: ${url}");
        Logger.i(TAG, "handleWithOkHttp Headers:" + proxyHeaders.map { "${it.key}: ${it.value}" }.joinToString("\n"));

        val resp = when (useMethod) {
            "GET" -> _client.get(url, proxyHeaders);
            "POST" -> _client.post(url, content ?: "", proxyHeaders);
            "HEAD" -> _client.head(url, proxyHeaders)
            else -> _client.requestMethod(useMethod, url, proxyHeaders);
        };

        Logger.i(TAG, "Proxied Response [${resp.code}]");
        val headersFiltered = HttpHeaders(resp.getHeadersFlat().filter { !_ignoreResponseHeaders.contains(it.key.lowercase()) });
        for(newHeader in headers)
            headersFiltered.put(newHeader.key, newHeader.value);

        if(resp.body == null)
            context.respondCode(resp.code, headersFiltered);
        else {
            resp.body.byteStream().use { inputStream ->
                context.respond(resp.code, headersFiltered) { responseStream ->
                    val buffer = ByteArray(8192);

                    var read: Int;
                    while (inputStream.read(buffer).also { read = it } >= 0) {
                        responseStream.write(buffer, 0, read);
                    }
                };
            }
        }
    }

    private fun handleWithTcp(context: HttpContext) {
        if (content != null)
            throw NotImplementedError("Content body is not supported")

        val proxyHeaders = HashMap<String, String>();
        for (header in context.headers.filter { !_ignoreRequestHeaders.contains(it.key.lowercase()) })
            proxyHeaders[header.key] = header.value;
        for (injectHeader in _injectRequestHeader)
            proxyHeaders[injectHeader.first] = injectHeader.second;

        val req = _requestModifier?.invoke(targetUrl, proxyHeaders)
        var url = targetUrl
        if (req != null) {
            req.url?.let {
                url = it
            }
            req.headers.let {
                proxyHeaders.clear()
                proxyHeaders.putAll(it)
            }
        }

        val parsed = Uri.parse(url);
        if(_injectHost)
            proxyHeaders.put("Host", parsed.host!!);
        if(_injectReferer)
            proxyHeaders.put("Referer", url);

        val useMethod = if (method == "inherit") context.method else method;
        Logger.i(TAG, "handleWithTcp Proxied Request ${useMethod}: ${parsed}");
        Logger.i(TAG, "handleWithTcp Headers:" + proxyHeaders.map { "${it.key}: ${it.value}" }.joinToString("\n"));

        makeTcpRequest(proxyHeaders, useMethod, parsed, context)
    }

    private fun makeTcpRequest(proxyHeaders: HashMap<String, String>, useMethod: String, parsed: Uri, context: HttpContext) {
        val requestBuilder = StringBuilder()
        requestBuilder.append("$useMethod $parsed HTTP/1.1\r\n")
        proxyHeaders.forEach { (key, value) -> requestBuilder.append("$key: $value\r\n") }
        requestBuilder.append("\r\n")

        val port = if (parsed.port == -1) {
            when (parsed.scheme) {
                "https" -> 443
                "http" -> 80
                else -> throw Exception("Unhandled scheme")
            }
        } else {
            parsed.port
        }

        val socket = if (parsed.scheme == "https") {
            val sslSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            sslSocketFactory.createSocket(parsed.host, port)
        } else {
            Socket(parsed.host, port)
        }

        socket.use { s ->
            s.getOutputStream().write(requestBuilder.toString().encodeToByteArray())

            val inputStream = s.getInputStream()
            val resp = HttpResponseParser(inputStream)
            if (resp.statusCode == 302) {
                val location = resp.location!!
                Logger.i(TAG, "handleWithTcp Proxied ${resp.statusCode} following redirect to $location");
                makeTcpRequest(proxyHeaders, useMethod, Uri.parse(location)!!, context)
            } else {
                val isChunked = resp.transferEncoding.equals("chunked", ignoreCase = true)
                val contentLength = resp.contentLength.toInt()

                val headersFiltered = HttpHeaders(resp.headers.filter { !_ignoreResponseHeaders.contains(it.key.lowercase()) });
                for (newHeader in headers)
                    headersFiltered.put(newHeader.key, newHeader.value);

                context.respond(resp.statusCode, headersFiltered) { responseStream ->
                    if (isChunked) {
                        Logger.i(TAG, "handleWithTcp handleChunkedTransfer");
                        handleChunkedTransfer(inputStream, responseStream)
                    } else if (contentLength > 0) {
                        Logger.i(TAG, "handleWithTcp transferFixedLengthContent $contentLength");
                        transferFixedLengthContent(inputStream, responseStream, contentLength)
                    } else if (contentLength == -1) {
                        Logger.i(TAG, "handleWithTcp transferUntilEndOfStream");
                        transferUntilEndOfStream(inputStream, responseStream)
                    } else {
                        Logger.i(TAG, "handleWithTcp no content");
                    }
                }
            }
        }
    }

    private fun handleChunkedTransfer(inputStream: InputStream, responseStream: OutputStream) {
        var line: String?
        val buffer = ByteArray(8192)

        while (inputStream.readLine().also { line = it } != null) {
            val size = line!!.trim().toInt(16)

            responseStream.write(line!!.encodeToByteArray())
            responseStream.write("\r\n".encodeToByteArray())

            if (size == 0) {
                inputStream.skip(2)
                responseStream.write("\r\n".encodeToByteArray())
                break
            }

            var totalRead = 0
            while (totalRead < size) {
                val read = inputStream.read(buffer, 0, minOf(buffer.size, size - totalRead))
                if (read == -1) break
                responseStream.write(buffer, 0, read)
                totalRead += read
            }

            inputStream.skip(2)
            responseStream.write("\r\n".encodeToByteArray())
            responseStream.flush()
        }
    }

    private fun transferFixedLengthContent(inputStream: InputStream, responseStream: OutputStream, contentLength: Int) {
        val buffer = ByteArray(8192)
        var totalRead = 0
        while (totalRead < contentLength) {
            val read = inputStream.read(buffer, 0, minOf(buffer.size, contentLength - totalRead))
            if (read == -1) break
            responseStream.write(buffer, 0, read)
            totalRead += read
        }

        responseStream.flush()
    }

    private fun transferUntilEndOfStream(inputStream: InputStream, responseStream: OutputStream) {
        val buffer = ByteArray(8192)
        var read: Int
        while (inputStream.read(buffer).also { read = it } >= 0) {
            responseStream.write(buffer, 0, read)
        }

        responseStream.flush()
    }

    fun withContent(body: String) : HttpProxyHandler {
        this.content = body;
        return this;
    }

    fun withRequestHeader(header: String, value: String) : HttpProxyHandler {
        _injectRequestHeader.add(Pair(header, value));
        return this;
    }
    fun withIgnoredRequestHeaders(ignored: List<String>) : HttpProxyHandler {
        _ignoreRequestHeaders.addAll(ignored.map { it.lowercase() });
        return this;
    }
    fun withIgnoredResponseHeaders(ignored: List<String>) : HttpProxyHandler {
        _ignoreResponseHeaders.addAll(ignored.map { it.lowercase() });
        return this;
    }
    fun withInjectedHost() : HttpProxyHandler {
        _injectHost = true;
        _ignoreRequestHeaders.add("host");
        return this;
    }
    fun withInjectedReferer() : HttpProxyHandler {
        _injectReferer = true;
        _ignoreRequestHeaders.add("referer");
        return this;
    }
    fun withRequestModifier(modifier: (String, Map<String, String>) -> IRequest) : HttpProxyHandler {
        _requestModifier = modifier;
        return this;
    }

    companion object {
        private const val TAG = "HttpProxyHandler"
    }
}