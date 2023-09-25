package com.futo.platformplayer.api.http.server.handlers

import android.net.Uri
import com.futo.platformplayer.api.http.server.HttpContext
import com.futo.platformplayer.api.http.server.HttpHeaders
import com.futo.platformplayer.api.http.ManagedHttpClient

class HttpProxyHandler(method: String, path: String, val targetUrl: String): HttpHandler(method, path) {
    var content: String? = null;
    var contentType: String? = null;

    private val _ignoreRequestHeaders = mutableListOf<String>();
    private val _injectRequestHeader = mutableListOf<Pair<String, String>>();

    private val _ignoreResponseHeaders = mutableListOf<String>();

    private var _injectHost = false;
    private var _injectReferer = false;


    private val _client = ManagedHttpClient();

    override fun handle(context: HttpContext) {
        val proxyHeaders = HashMap<String, String>();
        for (header in context.headers.filter { !_ignoreRequestHeaders.contains(it.key.lowercase()) })
            proxyHeaders[header.key] = header.value;
        for (injectHeader in _injectRequestHeader)
            proxyHeaders[injectHeader.first] = injectHeader.second;

        val parsed = Uri.parse(targetUrl);
        if(_injectHost)
            proxyHeaders.put("Host", parsed.host!!);
        if(_injectReferer)
            proxyHeaders.put("Referer", targetUrl);

        val useMethod = if (method == "inherit") context.method else method;
        //Logger.i(TAG, "Proxied Request ${useMethod}: ${targetUrl}");
        //Logger.i(TAG, "Headers:" + proxyHeaders.map { "${it.key}: ${it.value}" }.joinToString("\n"));

        val resp = when (useMethod) {
            "GET" -> _client.get(targetUrl, proxyHeaders);
            "POST" -> _client.post(targetUrl, content ?: "", proxyHeaders);
            "HEAD" -> _client.head(targetUrl, proxyHeaders)
            else -> _client.requestMethod(useMethod, targetUrl, proxyHeaders);
        };

        //Logger.i(TAG, "Proxied Response [${resp.code}]");
        val headersFiltered = HttpHeaders(resp.getHeadersFlat().filter { !_ignoreRequestHeaders.contains(it.key.lowercase()) });
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
}