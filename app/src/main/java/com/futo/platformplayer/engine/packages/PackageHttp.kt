package com.futo.platformplayer.engine.packages

import com.caoccao.javet.annotations.V8Convert
import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.annotations.V8Property
import com.caoccao.javet.enums.V8ConversionMode
import com.caoccao.javet.enums.V8ProxyMode
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.primitive.V8ValueString
import com.caoccao.javet.values.reference.V8ValueArrayBuffer
import com.caoccao.javet.values.reference.V8ValueObject
import com.caoccao.javet.values.reference.V8ValueSharedArrayBuffer
import com.caoccao.javet.values.reference.V8ValueTypedArray
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.platforms.js.internal.JSHttpClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.engine.internal.IV8Convertable
import com.futo.platformplayer.engine.internal.V8BindObject
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import kotlin.concurrent.thread
import kotlin.streams.asSequence

class PackageHttp: V8Package {
    @Transient
    private val _config: IV8PluginConfig;
    @Transient
    private val _client: ManagedHttpClient
    @Transient
    private val _clientAuth: ManagedHttpClient
    @Transient
    private val _packageClient: PackageHttpClient;
    @Transient
    private val _packageClientAuth: PackageHttpClient


    override val name: String get() = "Http";
    override val variableName: String get() = "http";

    private var _batchPoolLock: Any = Any();
    private var _batchPool: ForkJoinPool? = null;


    constructor(plugin: V8Plugin, config: IV8PluginConfig): super(plugin) {
        _config = config;
        _client = plugin.httpClient;
        _clientAuth = plugin.httpClientAuth;
        _packageClient = PackageHttpClient(this, _client);
        _packageClientAuth = PackageHttpClient(this, _clientAuth);
    }


    /*
    Automatically adjusting threadpool dedicated per PackageHttp for batch requests.
     */
    private fun <T, R> autoParallelPool(data: List<T>, parallelism: Int, handle: (T)->R): List<Pair<R?, Throwable?>> {
        synchronized(_batchPoolLock) {
            val threadsToUse = if (parallelism <= 0) data.size else Math.min(parallelism, data.size);
            if(_batchPool == null)
                _batchPool = ForkJoinPool(threadsToUse);
            var pool = _batchPool ?: return listOf();
            if(pool.poolSize < threadsToUse) { //Resize pool
                pool.shutdown();
                _batchPool = ForkJoinPool(threadsToUse);
                pool = _batchPool ?: return listOf();
            }

            val resultTasks = mutableListOf<ForkJoinTask<Pair<R?, Throwable?>>>();
            for(item in data){
                resultTasks.add(pool.submit<Pair<R?, Throwable?>> {
                      try {
                          return@submit Pair<R?, Throwable?>(handle(item), null);
                      }
                      catch(ex: Throwable) {
                          return@submit Pair<R?, Throwable?>(null, ex);
                      }
                });
            }
            return resultTasks.map { it.join() };
        }
    }

    @V8Function
    fun newClient(withAuth: Boolean): PackageHttpClient {
        val httpClient = if(withAuth) _clientAuth.clone() else _client.clone();
        if(httpClient is JSHttpClient)
            _plugin.registerHttpClient(httpClient);
        val client = PackageHttpClient(this, httpClient);

        return client;
    }
    @V8Function
    fun getDefaultClient(withAuth: Boolean): PackageHttpClient {
        return if(withAuth) _packageClientAuth else _packageClient;
    }

    @V8Function
    fun batch(): BatchBuilder {
        return BatchBuilder(this);
    }

    @V8Function
    fun request(method: String, url: String, headers: MutableMap<String, String> = HashMap(), useAuth: Boolean = false, bytesResult: Boolean = false) : IBridgeHttpResponse {
        return if(useAuth)
            _packageClientAuth.request(method, url, headers, if(bytesResult) ReturnType.BYTES else ReturnType.STRING)
        else
            _packageClient.request(method, url, headers, if(bytesResult) ReturnType.BYTES else ReturnType.STRING);
    }

    @V8Function
    fun requestWithBody(method: String, url: String, body:String, headers: MutableMap<String, String> = HashMap(), useAuth: Boolean = false, bytesResult: Boolean = false) : IBridgeHttpResponse {
        return if(useAuth)
            _packageClientAuth.requestWithBody(method, url, body, headers, if(bytesResult) ReturnType.BYTES else ReturnType.STRING)
        else
            _packageClient.requestWithBody(method, url, body, headers, if(bytesResult) ReturnType.BYTES else ReturnType.STRING);
    }
    @V8Function
    fun GET(url: String, headers: MutableMap<String, String> = HashMap(), useAuth: Boolean = false, useByteResponse: Boolean = false) : IBridgeHttpResponse {
        return if(useAuth)
            _packageClientAuth.GET(url, headers, if(useByteResponse) ReturnType.BYTES else ReturnType.STRING)
        else
            _packageClient.GET(url, headers, if(useByteResponse) ReturnType.BYTES else ReturnType.STRING);
    }
    @V8Function
    fun POST(url: String, body: Any, headers: MutableMap<String, String> = HashMap(), useAuth: Boolean = false, useByteResponse: Boolean = false) : IBridgeHttpResponse {

        val client = if(useAuth) _packageClientAuth else _packageClient;

        if(body is V8ValueString)
            return client.POST(url, body.value, headers, if(useByteResponse) ReturnType.BYTES else ReturnType.STRING);
        else if(body is String)
            return client.POST(url, body, headers, if(useByteResponse) ReturnType.BYTES else ReturnType.STRING);
        else if(body is V8ValueTypedArray)
            return client.POST(url, body.toBytes(), headers, if(useByteResponse) ReturnType.BYTES else ReturnType.STRING);
        else if(body is ByteArray)
            return client.POST(url, body, headers, if(useByteResponse) ReturnType.BYTES else ReturnType.STRING);
        else if(body is ArrayList<*>) //Avoid this case, used purely for testing
            return client.POST(url, body.map { (it as Double).toInt().toByte() }.toByteArray(), headers, if(useByteResponse) ReturnType.BYTES else ReturnType.STRING);
        else
            throw NotImplementedError("Body type " + body?.javaClass?.name?.toString() + " not implemented for POST");
    }

    @V8Function
    fun socket(url: String, headers: Map<String, String>? = null, useAuth: Boolean = false): SocketResult {
        return if(useAuth)
            _packageClientAuth.socket(url, headers)
        else
            _packageClient.socket(url, headers);
    }

    private fun <T> logExceptions(handle: ()->T): T {
        try {
            return handle();
        }
        catch(ex: Exception) {
            Logger.e("Plugin[${_config.name}]", ex.message, ex);
            throw ex;
        }
    }

    interface IBridgeHttpResponse {
        val url: String;
        val code: Int;
        val headers: Map<String, List<String>>?;
    }

    @kotlinx.serialization.Serializable
    class BridgeHttpStringResponse(
        override val url: String,
        override val code: Int, val
        body: String?,
        override val headers: Map<String, List<String>>? = null) : IV8Convertable, IBridgeHttpResponse {

        val isOk = code >= 200 && code < 300;

        override fun toV8(runtime: V8Runtime): V8Value? {
            val obj = runtime.createV8ValueObject();
            obj.set("url", url);
            obj.set("code", code);
            obj.set("body", body);
            obj.set("headers", headers);
            obj.set("isOk", isOk);
            return obj;
        }
    }
    @kotlinx.serialization.Serializable
    class BridgeHttpBytesResponse: IV8Convertable, IBridgeHttpResponse {
        override val url: String;
        override val code: Int;
        val body: ByteArray?;
        override val headers: Map<String, List<String>>?;

        val isOk: Boolean;

        constructor(url: String, code: Int, body: ByteArray? = null, headers: Map<String, List<String>>? = null) {
            this.url = url;
            this.code = code;
            this.body = body;
            this.headers = headers;
            this.isOk = code >= 200 && code < 300;
        }

        override fun toV8(runtime: V8Runtime): V8Value? {
            val obj = runtime.createV8ValueObject();
            obj.set("url", url);
            obj.set("code", code);
            if(body != null) {
                obj.set("body", body);
            }
            obj.set("headers", headers);
            obj.set("isOk", isOk);
            return obj;
        }
    }

    //TODO: This object is currently re-wrapped each modification, this is due to an issue passing the same object back and forth, should be fixed in future.
    @V8Convert(mode = V8ConversionMode.AllowOnly, proxyMode = V8ProxyMode.Class)
    class BatchBuilder(private val _package: PackageHttp, existingRequests: MutableList<Pair<PackageHttpClient, RequestDescriptor>> = mutableListOf()): V8BindObject() {
        @Transient
        private val _reqs = existingRequests;

        @V8Function
        fun request(method: String, url: String, headers: MutableMap<String, String> = HashMap(), useAuth: Boolean = false) : BatchBuilder {
            return clientRequest(_package.getDefaultClient(useAuth), method, url, headers);
        }
        @V8Function
        fun requestWithBody(method: String, url: String, body:String, headers: MutableMap<String, String> = HashMap(), useAuth: Boolean = false) : BatchBuilder {
            return clientRequestWithBody(_package.getDefaultClient(useAuth), method, url, body, headers);
        }
        @V8Function
        fun GET(url: String, headers: MutableMap<String, String> = HashMap(), useAuth: Boolean = false) : BatchBuilder
            = clientGET(_package.getDefaultClient(useAuth), url, headers);
        @V8Function
        fun POST(url: String, body: String, headers: MutableMap<String, String> = HashMap(), useAuth: Boolean = false) : BatchBuilder
            = clientPOST(_package.getDefaultClient(useAuth), url, body, headers);

        @V8Function
        fun DUMMY(): BatchBuilder {
            _reqs.add(Pair(_package.getDefaultClient(false), RequestDescriptor("DUMMY", "", mutableMapOf())));
            return BatchBuilder(_package, _reqs);
        }

        //Client-specific

        @V8Function
        fun clientRequest(client: PackageHttpClient, method: String, url: String, headers: MutableMap<String, String> = HashMap()) : BatchBuilder {
            _reqs.add(Pair(client, RequestDescriptor(method, url, headers)));
            return BatchBuilder(_package, _reqs);
        }
        @V8Function
        fun clientRequestWithBody(client: PackageHttpClient, method: String, url: String, body:String, headers: MutableMap<String, String> = HashMap()) : BatchBuilder {
            _reqs.add(Pair(client, RequestDescriptor(method, url, headers, body)));
            return BatchBuilder(_package, _reqs);
        }
        @V8Function
        fun clientGET(client: PackageHttpClient, url: String, headers: MutableMap<String, String> = HashMap()) : BatchBuilder
                = clientRequest(client, "GET", url, headers);
        @V8Function
        fun clientPOST(client: PackageHttpClient, url: String, body: String, headers: MutableMap<String, String> = HashMap()) : BatchBuilder
                = clientRequestWithBody(client, "POST", url, body, headers);


        //Finalizer
        @V8Function
        fun execute(): List<IBridgeHttpResponse?> {
            return _package.autoParallelPool(_reqs, -1) {
                if(it.second.method == "DUMMY")
                    return@autoParallelPool null;
                if(it.second.body != null)
                    return@autoParallelPool it.first.requestWithBody(it.second.method, it.second.url, it.second.body!!, it.second.headers, it.second.respType);
                else
                    return@autoParallelPool it.first.request(it.second.method, it.second.url, it.second.headers, it.second.respType);
            }.map {
                if(it.second != null)
                    throw it.second!!;
                else
                    return@map it.first;
            }.toList();
        }
    }



    @V8Convert(mode = V8ConversionMode.AllowOnly, proxyMode = V8ProxyMode.Class)
    class PackageHttpClient : V8BindObject {

        @Transient
        private val _package: PackageHttp;
        @Transient
        private val _client: ManagedHttpClient;

        val parentConfig: IV8PluginConfig get() = _package._config;

        @Transient
        private val _defaultHeaders = mutableMapOf<String, String>();
        @Transient
        private val _clientId: String?;

        @V8Property
        fun clientId(): String? {
            return _clientId;
        }


        constructor(pack: PackageHttp, baseClient: ManagedHttpClient): super() {
            _package = pack;
            _client = baseClient;
            _clientId = if(_client is JSHttpClient) _client.clientId else null;
        }

        @V8Function
        fun setDefaultHeaders(defaultHeaders: Map<String, String>) {
            for(pair in defaultHeaders)
                _defaultHeaders[pair.key] = pair.value;
        }
        @V8Function
        fun setDoApplyCookies(apply: Boolean) {
            if(_client is JSHttpClient)
                _client.doApplyCookies = apply;
        }
        @V8Function
        fun setDoUpdateCookies(update: Boolean) {
            if(_client is JSHttpClient)
                _client.doUpdateCookies = update;
        }
        @V8Function
        fun setDoAllowNewCookies(allow: Boolean) {
            if(_client is JSHttpClient)
                _client.doAllowNewCookies = allow;
        }
        @V8Function
        fun setTimeout(timeoutMs: Int) {
            if(_client is JSHttpClient) {
                _client.setTimeout(timeoutMs.toLong());
            }
        }

        @V8Function
        fun request(method: String, url: String, headers: MutableMap<String, String> = HashMap(), returnType: ReturnType) : IBridgeHttpResponse {
            applyDefaultHeaders(headers);
            return logExceptions {
                return@logExceptions catchHttp {
                    val client = _client;
                    //logRequest(method, url, headers, null);
                    val resp = client.requestMethod(method, url, headers);
                    //logResponse(method, url, resp.code, resp.headers, responseBody);
                    return@catchHttp when(returnType) {
                        ReturnType.STRING -> BridgeHttpStringResponse(resp.url, resp.code, resp.body?.string(), sanitizeResponseHeaders(resp.headers,
                            _client !is JSHttpClient || _client.isLoggedIn || _package._config !is SourcePluginConfig || !_package._config.allowAllHttpHeaderAccess));
                        ReturnType.BYTES -> BridgeHttpBytesResponse(resp.url, resp.code, resp.body?.bytes(), sanitizeResponseHeaders(resp.headers,
                            _client !is JSHttpClient || _client.isLoggedIn || _package._config !is SourcePluginConfig || !_package._config.allowAllHttpHeaderAccess));
                        else -> throw NotImplementedError("Return type " + returnType.toString() + " not implemented");
                    }
                }
            };
        }
        @V8Function
        fun requestWithBody(method: String, url: String, body:String, headers: MutableMap<String, String> = HashMap(), returnType: ReturnType) : IBridgeHttpResponse {
            applyDefaultHeaders(headers);
            return logExceptions {
                catchHttp {
                    val client = _client;
                    //logRequest(method, url, headers, body);
                    val resp = client.requestMethod(method, url, body, headers);
                    //logResponse(method, url, resp.code, resp.headers, responseBody);

                    return@catchHttp when(returnType) {
                        ReturnType.STRING -> BridgeHttpStringResponse(resp.url, resp.code, resp.body?.string(), sanitizeResponseHeaders(resp.headers,
                            _client !is JSHttpClient || _client.isLoggedIn || _package._config !is SourcePluginConfig || !_package._config.allowAllHttpHeaderAccess));
                        ReturnType.BYTES -> BridgeHttpBytesResponse(resp.url, resp.code, resp.body?.bytes(), sanitizeResponseHeaders(resp.headers,
                            _client !is JSHttpClient || _client.isLoggedIn || _package._config !is SourcePluginConfig || !_package._config.allowAllHttpHeaderAccess));
                        else -> throw NotImplementedError("Return type " + returnType.toString() + " not implemented");
                    }
                }
            };
        }

        @V8Function
        fun GET(url: String, headers: MutableMap<String, String> = HashMap(), returnType: ReturnType = ReturnType.STRING) : IBridgeHttpResponse {
            applyDefaultHeaders(headers);
            return logExceptions {
                catchHttp {
                    val client = _client;
                    //logRequest("GET", url, headers, null);
                    val resp = client.get(url, headers);
                    //val responseBody = resp.body?.string();
                    //logResponse("GET", url, resp.code, resp.headers, responseBody);


                    return@catchHttp when(returnType) {
                        ReturnType.STRING -> BridgeHttpStringResponse(resp.url, resp.code, resp.body?.string(), sanitizeResponseHeaders(resp.headers,
                            _client !is JSHttpClient || _client.isLoggedIn || _package._config !is SourcePluginConfig || !_package._config.allowAllHttpHeaderAccess));
                        ReturnType.BYTES -> BridgeHttpBytesResponse(resp.url, resp.code, resp.body?.bytes(), sanitizeResponseHeaders(resp.headers,
                            _client !is JSHttpClient || _client.isLoggedIn || _package._config !is SourcePluginConfig || !_package._config.allowAllHttpHeaderAccess));
                        else -> throw NotImplementedError("Return type " + returnType.toString() + " not implemented");
                    }
                }
            };
        }
        @V8Function
        fun POST(url: String, body: String, headers: MutableMap<String, String> = HashMap(), returnType: ReturnType = ReturnType.STRING) : IBridgeHttpResponse {
            applyDefaultHeaders(headers);
            return logExceptions {
                catchHttp {
                    val client = _client;
                    //logRequest("POST", url, headers, body);
                    val resp = client.post(url, body, headers);
                    //val responseBody = resp.body?.string();
                    //logResponse("POST", url, resp.code, resp.headers, responseBody);


                    return@catchHttp when(returnType) {
                        ReturnType.STRING -> BridgeHttpStringResponse(resp.url, resp.code, resp.body?.string(), sanitizeResponseHeaders(resp.headers,
                            _client !is JSHttpClient || _client.isLoggedIn || _package._config !is SourcePluginConfig || !_package._config.allowAllHttpHeaderAccess));
                        ReturnType.BYTES -> BridgeHttpBytesResponse(resp.url, resp.code, resp.body?.bytes(), sanitizeResponseHeaders(resp.headers,
                            _client !is JSHttpClient || _client.isLoggedIn || _package._config !is SourcePluginConfig || !_package._config.allowAllHttpHeaderAccess));
                        else -> throw NotImplementedError("Return type " + returnType.toString() + " not implemented");
                    }
                }
            };
        }
        @V8Function
        fun POST(url: String, body: ByteArray, headers: MutableMap<String, String> = HashMap(), returnType: ReturnType = ReturnType.STRING) : IBridgeHttpResponse {
            applyDefaultHeaders(headers);
            return logExceptions {
                catchHttp {
                    val client = _client;
                    //logRequest("POST", url, headers, body);
                    val resp = client.post(url, body, headers);
                    //val responseBody = resp.body?.string();
                    //logResponse("POST", url, resp.code, resp.headers, responseBody);


                    return@catchHttp when(returnType) {
                        ReturnType.STRING -> BridgeHttpStringResponse(resp.url, resp.code, resp.body?.string(), sanitizeResponseHeaders(resp.headers,
                            _client !is JSHttpClient || _client.isLoggedIn || _package._config !is SourcePluginConfig || !_package._config.allowAllHttpHeaderAccess));
                        ReturnType.BYTES -> BridgeHttpBytesResponse(resp.url, resp.code, resp.body?.bytes(), sanitizeResponseHeaders(resp.headers,
                            _client !is JSHttpClient || _client.isLoggedIn || _package._config !is SourcePluginConfig || !_package._config.allowAllHttpHeaderAccess));
                        else -> throw NotImplementedError("Return type " + returnType.toString() + " not implemented");
                    }
                }
            };
        }

        @V8Function
        fun socket(url: String, headers: Map<String, String>? = null): SocketResult {
            val socketHeaders = headers?.toMutableMap() ?: HashMap();
            applyDefaultHeaders(socketHeaders);
            return SocketResult(this, _client, url, socketHeaders);
        }

        private fun applyDefaultHeaders(headerMap: MutableMap<String, String>) {
            synchronized(_defaultHeaders) {
                for(toApply in _defaultHeaders)
                    if(!headerMap.containsKey(toApply.key))
                        headerMap[toApply.key] = toApply.value;
            }
        }

        private fun sanitizeResponseHeaders(headers: Map<String, List<String>>?, onlyWhitelisted: Boolean = false): Map<String, List<String>> {
            val result = mutableMapOf<String, List<String>>()
            if(onlyWhitelisted)
                headers?.forEach { (header, values) ->
                    val lowerCaseHeader = header.lowercase()
                    if (WHITELISTED_RESPONSE_HEADERS.contains(lowerCaseHeader)) {
                        result[lowerCaseHeader] = values
                    }
                }
            else {
                headers?.forEach { (header, values) ->
                    val lowerCaseHeader = header.lowercase()
                    if(lowerCaseHeader == "set-cookie" && !values.any { it.lowercase().contains("httponly") })
                        result[lowerCaseHeader] = values;
                    else
                        result[lowerCaseHeader] = values;
                }
            }
            return result
        }

        private fun logRequest(method: String, url: String, headers: Map<String, String> = HashMap(), body: String?) {
            Logger.v(TAG) {
                val stringBuilder = StringBuilder();
                stringBuilder.appendLine("HTTP request (useAuth = )");
                stringBuilder.appendLine("$method $url");

                for (pair in headers) {
                    stringBuilder.appendLine("${pair.key}: ${pair.value}");
                }

                if (body != null) {
                    stringBuilder.appendLine();
                    stringBuilder.appendLine(body);
                }

                return@v stringBuilder.toString();
            };
        }

        /*private fun logResponse(method: String, url: String, responseCode: Int? = null, responseHeaders: Map<String, List<String>> = HashMap(), responseBody: String? = null) {
            Logger.v(TAG) {
                val stringBuilder = StringBuilder();
                if (responseCode != null) {
                    stringBuilder.appendLine("HTTP response (${responseCode})");
                    stringBuilder.appendLine("$method $url");

                    for (pair in responseHeaders) {
                        if (pair.key.equals("authorization", ignoreCase = true) || pair.key.equals("set-cookie", ignoreCase = true)) {
                            stringBuilder.appendLine("${pair.key}: @CENSOREDVALUE@");
                        } else {
                            stringBuilder.appendLine("${pair.key}: ${pair.value.joinToString("; ")}");
                        }
                    }

                    if (responseBody != null) {
                        stringBuilder.appendLine();
                        stringBuilder.appendLine(responseBody);
                    }
                } else {
                    stringBuilder.appendLine("No response");
                }

                return@v stringBuilder.toString();
            };
        }*/

        fun <T> logExceptions(handle: ()->T): T {
            try {
                return handle();
            }
            catch(ex: Exception) {
                Logger.e("Plugin[${_package._config.name}]", ex.message, ex);
                throw ex;
            }
        }

        private fun catchHttp(handle: ()->IBridgeHttpResponse): IBridgeHttpResponse {
            try{
                return handle();
            }
            //Forward timeouts
            catch(ex: SocketTimeoutException) {
                return BridgeHttpStringResponse("", 408, null);
            }
        }
    }

    @V8Convert(mode = V8ConversionMode.AllowOnly, proxyMode = V8ProxyMode.Class)
    class SocketResult: V8BindObject {
        private var _isOpen = false;
        private var _socket: ManagedHttpClient.Socket? = null;

        private var _listeners: V8ValueObject? = null;

        private val _packageClient: PackageHttpClient;
        private val _client: ManagedHttpClient;
        private val _url: String;
        private val _headers: Map<String, String>;

        constructor(pack: PackageHttpClient, client: ManagedHttpClient, url: String, headers: Map<String,String>) {
            _packageClient = pack;
            _client = client;
            _url = url;
            _headers = headers;
        }

        @V8Property
        fun isOpen(): Boolean = _isOpen; //TODO

        @V8Function
        fun connect(socketObj: V8ValueObject) {
            val hasOpen = socketObj.has("open");
            val hasMessage = socketObj.has("message");
            val hasClosing = socketObj.has("closing");
            val hasClosed = socketObj.has("closed");
            val hasFailure = socketObj.has("failure");

            socketObj.setWeak(); //We have to manage this lifecycle
            _listeners = socketObj;

            _socket = _packageClient.logExceptions {
                val client = _client;
                return@logExceptions client.socket(_url, _headers.toMutableMap(), object: ManagedHttpClient.SocketListener {
                    override fun open() {
                        Logger.i(TAG, "Websocket opened: " + _url);
                        _isOpen = true;
                        if(hasOpen) {
                            try {
                                _listeners?.invokeVoid("open", arrayOf<Any>());
                            }
                            catch(ex: Throwable){
                                Logger.e(TAG, "Socket for [${_packageClient.parentConfig.name}] open failed: " + ex.message, ex);
                            }
                        }
                    }
                    override fun message(msg: String) {
                        if(hasMessage) {
                            try {
                                _listeners?.invokeVoid("message", msg);
                            }
                            catch(ex: Throwable) {}
                        }
                    }
                    override fun closing(code: Int, reason: String) {
                        if(hasClosing)
                        {
                            try {
                                _listeners?.invokeVoid("closing", code, reason);
                            }
                            catch(ex: Throwable){
                                Logger.e(TAG, "Socket for [${_packageClient.parentConfig.name}] closing failed: " + ex.message, ex);
                            }
                        }
                    }
                    override fun closed(code: Int, reason: String) {
                        _isOpen = false;
                        if(hasClosed) {
                            try {
                                _listeners?.invokeVoid("closed", code, reason);
                            }
                            catch(ex: Throwable){
                                Logger.e(TAG, "Socket for [${_packageClient.parentConfig.name}] closed failed: " + ex.message, ex);
                            }
                        }
                    }
                    override fun failure(exception: Throwable) {
                        _isOpen = false;
                        Logger.e(TAG, "Websocket failure: ${exception.message} (${_url})", exception);
                        if(hasFailure) {
                            try {
                                _listeners?.invokeVoid("failure", exception.message);
                            }
                            catch(ex: Throwable){
                                Logger.e(TAG, "Socket for [${_packageClient.parentConfig.name}] closed failed: " + ex.message, ex);
                            }
                        }
                    }
                });
            };
        }

        @V8Function
        fun send(msg: String) {
            _socket?.send(msg);
        }

        @V8Function
        fun close() {
            _socket?.close(1000, "");
        }
        @V8Function
        fun close(code: Int?, reason: String?) {
            _socket?.close(code ?: 1000, reason ?: "");
            _listeners?.close()
        }
    }

    data class RequestDescriptor(
        val method: String,
        val url: String,
        val headers: MutableMap<String, String>,
        val body: String? = null,
        val contentType: String? = null,
        val respType: ReturnType = ReturnType.STRING
    )

    private fun catchHttp(handle: ()->BridgeHttpStringResponse): BridgeHttpStringResponse {
        try{
            return handle();
        }
        //Forward timeouts
        catch(ex: SocketTimeoutException) {
            return BridgeHttpStringResponse("", 408, null);
        }
    }


    enum class ReturnType(val value: Int) {
        STRING(0),
        BYTES(1);
    }

    companion object {
        private const val TAG = "PackageHttp";
        private val WHITELISTED_RESPONSE_HEADERS = listOf("content-type", "date", "content-length", "last-modified", "etag", "cache-control", "content-encoding", "content-disposition", "connection")
    }
}