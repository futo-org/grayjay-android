package com.futo.platformplayer.engine.packages

import com.caoccao.javet.annotations.V8Convert
import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.annotations.V8Property
import com.caoccao.javet.enums.V8ConversionMode
import com.caoccao.javet.enums.V8ProxyMode
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.primitive.V8ValueString
import com.caoccao.javet.values.reference.V8ValueTypedArray
import com.curlbind.Libcurl
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.engine.internal.IV8Convertable
import com.futo.platformplayer.engine.internal.V8BindObject
import com.futo.platformplayer.logging.Logger
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import kotlin.math.min

class PackageHttpImp : V8Package {
    @Transient
    internal val _config: IV8PluginConfig

    @Transient
    private val _packageClient: PackageHttpClient

    @Transient
    private val _packageClientAuth: PackageHttpClient

    override val name: String get() = "HttpImp"
    override val variableName: String get() = "httpimp"

    private var _batchPoolLock: Any = Any()
    private var _batchPool: ForkJoinPool? = null

    private val _clients = mutableMapOf<String, PackageHttpClient>()

    constructor(plugin: V8Plugin, config: IV8PluginConfig) : super(plugin) {
        _config = config
        _packageClient = PackageHttpClient(this, withAuth = false)
        _packageClientAuth = PackageHttpClient(this, withAuth = true)
    }

    fun cleanup() {
        Logger.w(TAG, "PackageHttpImp Cleaning up")
    }

    @V8Function
    fun socket(
        url: String,
        headers: MutableMap<String, String>? = null,
        useAuth: Boolean = false
    ): Any {
        throw NotImplementedError("WebSocket is not supported by the curl-impersonate HTTP implementation.")
    }

    private fun <T, R> autoParallelPool(
        data: List<T>,
        parallelism: Int,
        handle: (T) -> R
    ): List<Pair<R?, Throwable?>> {
        synchronized(_batchPoolLock) {
            val threadsToUse = if (parallelism <= 0) data.size else min(parallelism, data.size)
            if (_batchPool == null) {
                _batchPool = ForkJoinPool(threadsToUse)
            }
            var pool = _batchPool ?: return listOf()
            if (pool.poolSize < threadsToUse) {
                pool.shutdown()
                _batchPool = ForkJoinPool(threadsToUse)
                pool = _batchPool ?: return listOf()
            }

            val resultTasks = mutableListOf<ForkJoinTask<Pair<R?, Throwable?>>>()
            for (item in data) {
                resultTasks.add(
                    pool.submit<Pair<R?, Throwable?>> {
                        try {
                            Pair(handle(item), null)
                        } catch (ex: Throwable) {
                            Pair<R?, Throwable?>(null, ex)
                        }
                    }
                )
            }
            return resultTasks.map { it.join() }
        }
    }

    @V8Function
    fun newClient(withAuth: Boolean): PackageHttpClient {
        val client = PackageHttpClient(this, withAuth)
        client.clientId()?.let { _clients[it] = client }
        return client
    }

    @V8Function
    fun getDefaultClient(withAuth: Boolean): PackageHttpClient {
        return if (withAuth) _packageClientAuth else _packageClient
    }

    fun getClient(id: String?): PackageHttpClient {
        if (id == null) throw IllegalArgumentException("Http client $id doesn't exist")
        if (_packageClient.clientId() == id) return _packageClient
        if (_packageClientAuth.clientId() == id) return _packageClientAuth
        return _clients[id] ?: throw IllegalArgumentException("Http client $id doesn't exist")
    }

    @V8Function
    fun batch(): BatchBuilder {
        return BatchBuilder(this)
    }

    @V8Function
    fun request(
        method: String,
        url: String,
        headers: MutableMap<String, String> = HashMap(),
        useAuth: Boolean = false,
        bytesResult: Boolean = false
    ): IBridgeHttpResponse {
        return request(method, url, headers, useAuth, bytesResult, null)
    }

    @V8Function
    fun request(
        method: String,
        url: String,
        headers: MutableMap<String, String>,
        useAuth: Boolean,
        bytesResult: Boolean,
        options: MutableMap<String, Any?>?
    ): IBridgeHttpResponse {
        val client = if (useAuth) _packageClientAuth else _packageClient
        val parsed = parseRequestOptions(options)
        val returnType = if (bytesResult) ReturnType.BYTES else ReturnType.STRING
        return client.requestInternal(
            method,
            url,
            headers,
            returnType,
            parsed.impersonateTarget,
            parsed.useBuiltInHeaders,
            parsed.timeoutMs
        )
    }

    @V8Function
    fun requestWithBody(
        method: String,
        url: String,
        body: Any,
        headers: MutableMap<String, String> = HashMap(),
        useAuth: Boolean = false,
        bytesResult: Boolean = false
    ): IBridgeHttpResponse {
        return requestWithBody(method, url, body, headers, useAuth, bytesResult, null)
    }

    @V8Function
    fun requestWithBody(
        method: String,
        url: String,
        body: Any,
        headers: MutableMap<String, String>,
        useAuth: Boolean,
        bytesResult: Boolean,
        options: MutableMap<String, Any?>?
    ): IBridgeHttpResponse {
        val client = if (useAuth) _packageClientAuth else _packageClient
        val parsed = parseRequestOptions(options)
        val returnType = if (bytesResult) ReturnType.BYTES else ReturnType.STRING

        return when (body) {
            is V8ValueString ->
                client.requestWithBodyInternal(
                    method,
                    url,
                    body.value,
                    headers,
                    returnType,
                    parsed.impersonateTarget,
                    parsed.useBuiltInHeaders,
                    parsed.timeoutMs
                )
            is String ->
                client.requestWithBodyInternal(
                    method,
                    url,
                    body,
                    headers,
                    returnType,
                    parsed.impersonateTarget,
                    parsed.useBuiltInHeaders,
                    parsed.timeoutMs
                )
            is V8ValueTypedArray ->
                client.requestWithBodyInternal(
                    method,
                    url,
                    body.toBytes(),
                    headers,
                    returnType,
                    parsed.impersonateTarget,
                    parsed.useBuiltInHeaders,
                    parsed.timeoutMs
                )
            is ByteArray ->
                client.requestWithBodyInternal(
                    method,
                    url,
                    body,
                    headers,
                    returnType,
                    parsed.impersonateTarget,
                    parsed.useBuiltInHeaders,
                    parsed.timeoutMs
                )
            is ArrayList<*> ->
                client.requestWithBodyInternal(
                    method,
                    url,
                    body.map { (it as Double).toInt().toByte() }.toByteArray(),
                    headers,
                    returnType,
                    parsed.impersonateTarget,
                    parsed.useBuiltInHeaders,
                    parsed.timeoutMs
                )
            else -> throw NotImplementedError(
                "Body type ${body?.javaClass?.name} not implemented for requestWithBody"
            )
        }
    }

    @V8Function
    fun GET(
        url: String,
        headers: MutableMap<String, String> = HashMap(),
        useAuth: Boolean = false,
        useByteResponse: Boolean = false
    ): IBridgeHttpResponse {
        return GET(url, headers, useAuth, useByteResponse, null)
    }

    @V8Function
    fun GET(
        url: String,
        headers: MutableMap<String, String>,
        useAuth: Boolean,
        useByteResponse: Boolean,
        options: MutableMap<String, Any?>?
    ): IBridgeHttpResponse {
        val client = if (useAuth) _packageClientAuth else _packageClient
        val parsed = parseRequestOptions(options)
        return client.GETInternal(
            url,
            headers,
            if (useByteResponse) ReturnType.BYTES else ReturnType.STRING,
            parsed.impersonateTarget,
            parsed.useBuiltInHeaders,
            parsed.timeoutMs
        )
    }

    @V8Function
    fun POST(
        url: String,
        body: Any,
        headers: MutableMap<String, String> = HashMap(),
        useAuth: Boolean = false,
        useByteResponse: Boolean = false
    ): IBridgeHttpResponse {
        return POST(url, body, headers, useAuth, useByteResponse, null)
    }

    @V8Function
    fun POST(
        url: String,
        body: Any,
        headers: MutableMap<String, String>,
        useAuth: Boolean,
        useByteResponse: Boolean,
        options: MutableMap<String, Any?>?
    ): IBridgeHttpResponse {
        val client = if (useAuth) _packageClientAuth else _packageClient
        val parsed = parseRequestOptions(options)
        val returnType = if (useByteResponse) ReturnType.BYTES else ReturnType.STRING

        return when (body) {
            is V8ValueString ->
                client.POSTInternal(
                    url,
                    body.value,
                    headers,
                    returnType,
                    parsed.impersonateTarget,
                    parsed.useBuiltInHeaders,
                    parsed.timeoutMs
                )
            is String ->
                client.POSTInternal(
                    url,
                    body,
                    headers,
                    returnType,
                    parsed.impersonateTarget,
                    parsed.useBuiltInHeaders,
                    parsed.timeoutMs
                )
            is V8ValueTypedArray ->
                client.POSTInternal(
                    url,
                    body.toBytes(),
                    headers,
                    returnType,
                    parsed.impersonateTarget,
                    parsed.useBuiltInHeaders,
                    parsed.timeoutMs
                )
            is ByteArray ->
                client.POSTInternal(
                    url,
                    body,
                    headers,
                    returnType,
                    parsed.impersonateTarget,
                    parsed.useBuiltInHeaders,
                    parsed.timeoutMs
                )
            is ArrayList<*> ->
                client.POSTInternal(
                    url,
                    body.map { (it as Double).toInt().toByte() }.toByteArray(),
                    headers,
                    returnType,
                    parsed.impersonateTarget,
                    parsed.useBuiltInHeaders,
                    parsed.timeoutMs
                )
            else -> throw NotImplementedError(
                "Body type ${body?.javaClass?.name} not implemented for POST"
            )
        }
    }

    private fun <T> logExceptions(handle: () -> T): T {
        try {
            return handle()
        } catch (ex: Exception) {
            Logger.e("Plugin[${_config.name}]", ex.message, ex)
            throw ex
        }
    }

    interface IBridgeHttpResponse {
        val url: String
        val code: Int
        val headers: Map<String, List<String>>?
    }

    @kotlinx.serialization.Serializable
    class BridgeHttpStringResponse(
        override val url: String,
        override val code: Int,
        val body: String?,
        override val headers: Map<String, List<String>>? = null
    ) : IV8Convertable, IBridgeHttpResponse {
        val isOk = code in 200..299

        override fun toV8(runtime: V8Runtime): V8Value? {
            val obj = runtime.createV8ValueObject()
            obj.set("url", url)
            obj.set("code", code)
            obj.set("body", body)
            obj.set("headers", headers)
            obj.set("isOk", isOk)
            return obj
        }
    }

    @kotlinx.serialization.Serializable
    class BridgeHttpBytesResponse(
        override val url: String,
        override val code: Int,
        val body: ByteArray? = null,
        override val headers: Map<String, List<String>>? = null
    ) : IV8Convertable, IBridgeHttpResponse {
        val isOk: Boolean = code in 200..299

        override fun toV8(runtime: V8Runtime): V8Value? {
            val obj = runtime.createV8ValueObject()
            obj.set("url", url)
            obj.set("code", code)
            if (body != null) {
                obj.set("body", body)
            }
            obj.set("headers", headers)
            obj.set("isOk", isOk)
            return obj
        }
    }

    @V8Convert(mode = V8ConversionMode.AllowOnly, proxyMode = V8ProxyMode.Class)
    class BatchBuilder(
        @Transient private val _package: PackageHttpImp,
        existingRequests: MutableList<Pair<PackageHttpClient, RequestDescriptor>> = mutableListOf()
    ) : V8BindObject() {
        @Transient
        private val _reqs = existingRequests

        @V8Function
        fun request(
            method: String,
            url: String,
            headers: MutableMap<String, String> = HashMap(),
            useAuth: Boolean = false
        ): BatchBuilder {
            return request(method, url, headers, useAuth, null)
        }

        @V8Function
        fun request(
            method: String,
            url: String,
            headers: MutableMap<String, String>,
            useAuth: Boolean,
            options: MutableMap<String, Any?>?
        ): BatchBuilder {
            val clientId = _package.getDefaultClient(useAuth).clientId()
            return clientRequest(clientId, method, url, headers, options)
        }

        @V8Function
        fun requestWithBody(
            method: String,
            url: String,
            body: String,
            headers: MutableMap<String, String> = HashMap(),
            useAuth: Boolean = false
        ): BatchBuilder {
            return requestWithBody(method, url, body, headers, useAuth, null)
        }

        @V8Function
        fun requestWithBody(
            method: String,
            url: String,
            body: String,
            headers: MutableMap<String, String>,
            useAuth: Boolean,
            options: MutableMap<String, Any?>?
        ): BatchBuilder {
            val clientId = _package.getDefaultClient(useAuth).clientId()
            return clientRequestWithBody(clientId, method, url, body, headers, options)
        }

        @V8Function
        fun GET(
            url: String,
            headers: MutableMap<String, String> = HashMap(),
            useAuth: Boolean = false
        ): BatchBuilder =
            GET(url, headers, useAuth, null)

        @V8Function
        fun GET(
            url: String,
            headers: MutableMap<String, String>,
            useAuth: Boolean,
            options: MutableMap<String, Any?>?
        ): BatchBuilder =
            clientGET(_package.getDefaultClient(useAuth).clientId(), url, headers, options)

        @V8Function
        fun POST(
            url: String,
            body: String,
            headers: MutableMap<String, String> = HashMap(),
            useAuth: Boolean = false
        ): BatchBuilder =
            POST(url, body, headers, useAuth, null)

        @V8Function
        fun POST(
            url: String,
            body: String,
            headers: MutableMap<String, String>,
            useAuth: Boolean,
            options: MutableMap<String, Any?>?
        ): BatchBuilder =
            clientPOST(_package.getDefaultClient(useAuth).clientId(), url, body, headers, options)

        @V8Function
        fun DUMMY(): BatchBuilder {
            _reqs.add(
                Pair(
                    _package.getDefaultClient(false),
                    RequestDescriptor("DUMMY", "", mutableMapOf())
                )
            )
            return BatchBuilder(_package, _reqs)
        }

        @V8Function
        fun clientRequest(
            clientId: String?,
            method: String,
            url: String,
            headers: MutableMap<String, String> = HashMap()
        ): BatchBuilder {
            return clientRequest(clientId, method, url, headers, null)
        }

        @V8Function
        fun clientRequest(
            clientId: String?,
            method: String,
            url: String,
            headers: MutableMap<String, String>,
            options: MutableMap<String, Any?>?
        ): BatchBuilder {
            val opts = PackageHttpImp.parseRequestOptions(options)
            val respType =
                if ((options?.get("useByteResponses") as? Boolean) == true) ReturnType.BYTES else ReturnType.STRING

            _reqs.add(
                Pair(
                    _package.getClient(clientId),
                    RequestDescriptor(
                        method = method,
                        url = url,
                        headers = headers,
                        body = null,
                        contentType = null,
                        respType = respType,
                        impersonateTarget = opts.impersonateTarget,
                        useBuiltInHeaders = opts.useBuiltInHeaders,
                        timeoutMs = opts.timeoutMs
                    )
                )
            )
            return BatchBuilder(_package, _reqs)
        }

        @V8Function
        fun clientRequestWithBody(
            clientId: String?,
            method: String,
            url: String,
            body: String,
            headers: MutableMap<String, String> = HashMap()
        ): BatchBuilder {
            return clientRequestWithBody(clientId, method, url, body, headers, null)
        }

        @V8Function
        fun clientRequestWithBody(
            clientId: String?,
            method: String,
            url: String,
            body: String,
            headers: MutableMap<String, String>,
            options: MutableMap<String, Any?>?
        ): BatchBuilder {
            val opts = PackageHttpImp.parseRequestOptions(options)
            val respType =
                if ((options?.get("useByteResponses") as? Boolean) == true) ReturnType.BYTES else ReturnType.STRING

            _reqs.add(
                Pair(
                    _package.getClient(clientId),
                    RequestDescriptor(
                        method = method,
                        url = url,
                        headers = headers,
                        body = body,
                        contentType = null,
                        respType = respType,
                        impersonateTarget = opts.impersonateTarget,
                        useBuiltInHeaders = opts.useBuiltInHeaders,
                        timeoutMs = opts.timeoutMs
                    )
                )
            )
            return BatchBuilder(_package, _reqs)
        }

        @V8Function
        fun clientGET(
            clientId: String?,
            url: String,
            headers: MutableMap<String, String> = HashMap()
        ): BatchBuilder =
            clientRequest(clientId, "GET", url, headers)

        @V8Function
        fun clientGET(
            clientId: String?,
            url: String,
            headers: MutableMap<String, String>,
            options: MutableMap<String, Any?>?
        ): BatchBuilder =
            clientRequest(clientId, "GET", url, headers, options)

        @V8Function
        fun clientPOST(
            clientId: String?,
            url: String,
            body: String,
            headers: MutableMap<String, String> = HashMap()
        ): BatchBuilder =
            clientRequestWithBody(clientId, "POST", url, body, headers)

        @V8Function
        fun clientPOST(
            clientId: String?,
            url: String,
            body: String,
            headers: MutableMap<String, String>,
            options: MutableMap<String, Any?>?
        ): BatchBuilder =
            clientRequestWithBody(clientId, "POST", url, body, headers, options)

        @V8Function
        fun execute(): List<IBridgeHttpResponse?> {
            return _package.autoParallelPool(_reqs, -1) { pair ->
                val client = pair.first
                val descriptor = pair.second

                if (descriptor.method == "DUMMY") {
                    return@autoParallelPool null
                }

                if (descriptor.body != null) {
                    client.requestWithBodyInternal(
                        descriptor.method,
                        descriptor.url,
                        descriptor.body,
                        descriptor.headers,
                        descriptor.respType,
                        descriptor.impersonateTarget,
                        descriptor.useBuiltInHeaders,
                        descriptor.timeoutMs
                    )
                } else {
                    client.requestInternal(
                        descriptor.method,
                        descriptor.url,
                        descriptor.headers,
                        descriptor.respType,
                        descriptor.impersonateTarget,
                        descriptor.useBuiltInHeaders,
                        descriptor.timeoutMs
                    )
                }
            }.map {
                if (it.second != null) throw it.second!!
                it.first
            }.toList()
        }
    }

    @V8Convert(mode = V8ConversionMode.AllowOnly, proxyMode = V8ProxyMode.Class)
    class PackageHttpClient : V8BindObject {
        @Transient
        private val _package: PackageHttpImp

        @Transient
        private val _withAuth: Boolean

        val parentConfig: IV8PluginConfig
            get() = _package._config

        @Transient
        private val _defaultHeaders = mutableMapOf<String, String>()

        @Transient
        private val _clientId: String = UUID.randomUUID().toString()

        @Volatile
        private var timeoutMs: Int = 30_000

        @Volatile
        private var sendCookies: Boolean = true

        @Volatile
        private var updateCookies: Boolean = true

        @Volatile
        private var allowNewCookies: Boolean = true

        @Volatile
        private var cookieJarPath: String? = null

        @Volatile
        private var impersonateTarget: String = "chrome136"

        @Volatile
        private var useBuiltInHeaders: Boolean = true

        @V8Property
        fun clientId(): String? = _clientId

        constructor(pack: PackageHttpImp, withAuth: Boolean) : super() {
            _package = pack
            _withAuth = withAuth
        }

        private fun ensureCookieJarPath(): String {
            val existing = cookieJarPath
            if (existing != null) return existing

            val tmp = System.getProperty("java.io.tmpdir") ?: "/data/local/tmp"
            val safeName = parentConfig.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val fileName =
                if (_withAuth) "imphttp.$safeName.auth.cookies.txt" else "imphttp.$safeName.cookies.txt"
            val path = if (tmp.endsWith("/")) tmp + fileName else "$tmp/$fileName"
            cookieJarPath = path
            return path
        }

        @V8Function
        fun setDefaultHeaders(defaultHeaders: Map<String, String>) {
            synchronized(_defaultHeaders) {
                for (pair in defaultHeaders) {
                    _defaultHeaders[pair.key] = pair.value
                }
            }
        }

        @V8Function
        fun setDoApplyCookies(apply: Boolean) {
            sendCookies = apply
        }

        @V8Function
        fun setDoUpdateCookies(update: Boolean) {
            updateCookies = update
        }

        @V8Function
        fun setDoAllowNewCookies(allow: Boolean) {
            allowNewCookies = allow
        }

        @V8Function
        fun setTimeout(timeoutMs: Int) {
            this.timeoutMs = timeoutMs
        }

        @V8Function
        fun setDefaultImpersonateTarget(target: String) {
            impersonateTarget = target
        }

        @V8Function
        fun setUseBuiltInHeaders(enable: Boolean) {
            useBuiltInHeaders = enable
        }

        @V8Function
        fun request(
            method: String,
            url: String,
            headers: MutableMap<String, String> = HashMap(),
            useBytes: Boolean = false
        ): IBridgeHttpResponse =
            request(method, url, headers, useBytes, null)

        @V8Function
        fun request(
            method: String,
            url: String,
            headers: MutableMap<String, String>,
            useBytes: Boolean,
            options: MutableMap<String, Any?>?
        ): IBridgeHttpResponse {
            val opts = PackageHttpImp.parseRequestOptions(options)
            val returnType = if (useBytes) ReturnType.BYTES else ReturnType.STRING
            return requestInternal(
                method,
                url,
                headers,
                returnType,
                opts.impersonateTarget,
                opts.useBuiltInHeaders,
                opts.timeoutMs
            )
        }

        fun requestInternal(
            method: String,
            url: String,
            headers: MutableMap<String, String> = HashMap(),
            returnType: ReturnType,
            impersonateTargetOverride: String? = null,
            useBuiltInHeadersOverride: Boolean? = null,
            timeoutMsOverride: Int? = null
        ): IBridgeHttpResponse {
            return executeRequest(
                method,
                url,
                headers,
                null,
                returnType,
                impersonateTargetOverride,
                useBuiltInHeadersOverride,
                timeoutMsOverride
            )
        }

        @V8Function
        fun requestWithBody(
            method: String,
            url: String,
            body: String,
            headers: MutableMap<String, String> = HashMap(),
            useBytes: Boolean = false
        ): IBridgeHttpResponse =
            requestWithBody(method, url, body, headers, useBytes, null)

        @V8Function
        fun requestWithBody(
            method: String,
            url: String,
            body: String,
            headers: MutableMap<String, String>,
            useBytes: Boolean,
            options: MutableMap<String, Any?>?
        ): IBridgeHttpResponse {
            val opts = PackageHttpImp.parseRequestOptions(options)
            val returnType = if (useBytes) ReturnType.BYTES else ReturnType.STRING
            return requestWithBodyInternal(
                method,
                url,
                body,
                headers,
                returnType,
                opts.impersonateTarget,
                opts.useBuiltInHeaders,
                opts.timeoutMs
            )
        }

        fun requestWithBodyInternal(
            method: String,
            url: String,
            body: String,
            headers: MutableMap<String, String> = HashMap(),
            returnType: ReturnType,
            impersonateTargetOverride: String? = null,
            useBuiltInHeadersOverride: Boolean? = null,
            timeoutMsOverride: Int? = null
        ): IBridgeHttpResponse {
            return executeRequest(
                method,
                url,
                headers,
                body.toByteArray(),
                returnType,
                impersonateTargetOverride,
                useBuiltInHeadersOverride,
                timeoutMsOverride
            )
        }

        fun requestWithBodyInternal(
            method: String,
            url: String,
            body: ByteArray,
            headers: MutableMap<String, String> = HashMap(),
            returnType: ReturnType,
            impersonateTargetOverride: String? = null,
            useBuiltInHeadersOverride: Boolean? = null,
            timeoutMsOverride: Int? = null
        ): IBridgeHttpResponse {
            return executeRequest(
                method,
                url,
                headers,
                body,
                returnType,
                impersonateTargetOverride,
                useBuiltInHeadersOverride,
                timeoutMsOverride
            )
        }

        @V8Function
        fun GET(
            url: String,
            headers: MutableMap<String, String> = HashMap(),
            useBytes: Boolean = false
        ): IBridgeHttpResponse =
            GET(url, headers, useBytes, null)

        @V8Function
        fun GET(
            url: String,
            headers: MutableMap<String, String>,
            useBytes: Boolean,
            options: MutableMap<String, Any?>?
        ): IBridgeHttpResponse {
            val opts = PackageHttpImp.parseRequestOptions(options)
            val returnType = if (useBytes) ReturnType.BYTES else ReturnType.STRING
            return GETInternal(
                url,
                headers,
                returnType,
                opts.impersonateTarget,
                opts.useBuiltInHeaders,
                opts.timeoutMs
            )
        }

        fun GETInternal(
            url: String,
            headers: MutableMap<String, String> = HashMap(),
            returnType: ReturnType = ReturnType.STRING,
            impersonateTargetOverride: String? = null,
            useBuiltInHeadersOverride: Boolean? = null,
            timeoutMsOverride: Int? = null
        ): IBridgeHttpResponse {
            return executeRequest(
                "GET",
                url,
                headers,
                null,
                returnType,
                impersonateTargetOverride,
                useBuiltInHeadersOverride,
                timeoutMsOverride
            )
        }

        @V8Function
        fun POST(
            url: String,
            body: Any,
            headers: MutableMap<String, String> = HashMap(),
            useBytes: Boolean = false
        ): IBridgeHttpResponse =
            POST(url, body, headers, useBytes, null)

        @V8Function
        fun POST(
            url: String,
            body: Any,
            headers: MutableMap<String, String>,
            useBytes: Boolean,
            options: MutableMap<String, Any?>?
        ): IBridgeHttpResponse {
            val opts = PackageHttpImp.parseRequestOptions(options)
            val returnType = if (useBytes) ReturnType.BYTES else ReturnType.STRING

            return when (body) {
                is V8ValueString ->
                    POSTInternal(
                        url,
                        body.value,
                        headers,
                        returnType,
                        opts.impersonateTarget,
                        opts.useBuiltInHeaders,
                        opts.timeoutMs
                    )
                is String ->
                    POSTInternal(
                        url,
                        body,
                        headers,
                        returnType,
                        opts.impersonateTarget,
                        opts.useBuiltInHeaders,
                        opts.timeoutMs
                    )
                is V8ValueTypedArray ->
                    POSTInternal(
                        url,
                        body.toBytes(),
                        headers,
                        returnType,
                        opts.impersonateTarget,
                        opts.useBuiltInHeaders,
                        opts.timeoutMs
                    )
                is ByteArray ->
                    POSTInternal(
                        url,
                        body,
                        headers,
                        returnType,
                        opts.impersonateTarget,
                        opts.useBuiltInHeaders,
                        opts.timeoutMs
                    )
                is ArrayList<*> ->
                    POSTInternal(
                        url,
                        body.map { (it as Double).toInt().toByte() }.toByteArray(),
                        headers,
                        returnType,
                        opts.impersonateTarget,
                        opts.useBuiltInHeaders,
                        opts.timeoutMs
                    )
                else -> throw NotImplementedError("Body type ${body?.javaClass?.name} not implemented for POST")
            }
        }

        fun POSTInternal(
            url: String,
            body: String,
            headers: MutableMap<String, String> = HashMap(),
            returnType: ReturnType = ReturnType.STRING,
            impersonateTargetOverride: String? = null,
            useBuiltInHeadersOverride: Boolean? = null,
            timeoutMsOverride: Int? = null
        ): IBridgeHttpResponse {
            return executeRequest(
                "POST",
                url,
                headers,
                body.toByteArray(),
                returnType,
                impersonateTargetOverride,
                useBuiltInHeadersOverride,
                timeoutMsOverride
            )
        }

        fun POSTInternal(
            url: String,
            body: ByteArray,
            headers: MutableMap<String, String> = HashMap(),
            returnType: ReturnType = ReturnType.STRING,
            impersonateTargetOverride: String? = null,
            useBuiltInHeadersOverride: Boolean? = null,
            timeoutMsOverride: Int? = null
        ): IBridgeHttpResponse {
            return executeRequest(
                "POST",
                url,
                headers,
                body,
                returnType,
                impersonateTargetOverride,
                useBuiltInHeadersOverride,
                timeoutMsOverride
            )
        }

        private fun performCurl(
            method: String,
            url: String,
            headers: Map<String, String>,
            bodyBytes: ByteArray?,
            impersonateTargetOverride: String? = null,
            useBuiltInHeadersOverride: Boolean? = null,
            timeoutMsOverride: Int? = null
        ): Libcurl.Response {
            val jar = ensureCookieJarPath()

            val finalImpersonateTarget = impersonateTargetOverride ?: this.impersonateTarget
            val finalUseBuiltInHeaders = useBuiltInHeadersOverride ?: this.useBuiltInHeaders
            val finalTimeoutMs = timeoutMsOverride ?: this.timeoutMs

            val req = Libcurl.Request(
                url = url,
                method = method,
                headers = headers,
                body = bodyBytes,
                impersonateTarget = finalImpersonateTarget,
                useBuiltInHeaders = finalUseBuiltInHeaders,
                timeoutMs = finalTimeoutMs,
                cookieJarPath = jar,
                sendCookies = sendCookies,
                persistCookies = updateCookies && allowNewCookies
            )
            return Libcurl.perform(req)
        }

        private fun executeRequest(
            method: String,
            url: String,
            headers: MutableMap<String, String>,
            bodyBytes: ByteArray?,
            returnType: ReturnType,
            impersonateTargetOverride: String? = null,
            useBuiltInHeadersOverride: Boolean? = null,
            timeoutMsOverride: Int? = null
        ): IBridgeHttpResponse {
            applyDefaultHeaders(headers)
            return logExceptions {
                catchHttp {
                    val resp = performCurl(
                        method,
                        url,
                        headers,
                        bodyBytes,
                        impersonateTargetOverride,
                        useBuiltInHeadersOverride,
                        timeoutMsOverride
                    )
                    responseToBridge(resp, returnType)
                }
            }
        }

        private fun responseToBridge(
            resp: Libcurl.Response,
            returnType: ReturnType
        ): IBridgeHttpResponse {
            val sanitizedHeaders = sanitizeResponseHeaders(resp.headers, shouldWhitelistHeaders())
            return when (returnType) {
                ReturnType.STRING -> {
                    val bodyStr = decodeBody(resp)
                    BridgeHttpStringResponse(resp.effectiveUrl, resp.status, bodyStr, sanitizedHeaders)
                }
                ReturnType.BYTES -> {
                    BridgeHttpBytesResponse(resp.effectiveUrl, resp.status, resp.bodyBytes, sanitizedHeaders)
                }
            }
        }

        private fun decodeBody(resp: Libcurl.Response): String {
            if (resp.bodyBytes.isEmpty()) return ""

            val contentTypeHeader = resp.headers.entries.firstOrNull {
                it.key.equals("content-type", ignoreCase = true)
            }?.value?.firstOrNull()

            val charset: Charset = contentTypeHeader
                ?.let { parseCharset(it) }
                ?: Charsets.UTF_8

            return String(resp.bodyBytes, charset)
        }

        private fun parseCharset(contentType: String): Charset? {
            val parts = contentType.split(";")
            for (part in parts) {
                val trimmed = part.trim()
                val lower = trimmed.lowercase()
                if (lower.startsWith("charset=")) {
                    val value = trimmed.substringAfter("=", "").trim().trim('"', '\'')
                    return try {
                        Charset.forName(value)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            return null
        }

        private fun shouldWhitelistHeaders(): Boolean {
            val cfg = parentConfig
            return !(cfg is SourcePluginConfig && cfg.allowAllHttpHeaderAccess)
        }

        private fun applyDefaultHeaders(headerMap: MutableMap<String, String>) {
            synchronized(_defaultHeaders) {
                for (toApply in _defaultHeaders) {
                    if (!headerMap.containsKey(toApply.key)) {
                        headerMap[toApply.key] = toApply.value
                    }
                }
            }
        }

        private fun sanitizeResponseHeaders(
            headers: Map<String, List<String>>?,
            onlyWhitelisted: Boolean = false
        ): Map<String, List<String>> {
            val result = mutableMapOf<String, List<String>>()
            if (onlyWhitelisted) {
                headers?.forEach { (header, values) ->
                    val lowerCaseHeader = header.lowercase()
                    if (WHITELISTED_RESPONSE_HEADERS.contains(lowerCaseHeader)) {
                        result[lowerCaseHeader] = values
                    }
                }
            } else {
                headers?.forEach { (header, values) ->
                    val lowerCaseHeader = header.lowercase()
                    if (lowerCaseHeader == "set-cookie" &&
                        !values.any { it.lowercase().contains("httponly") }
                    ) {
                        result[lowerCaseHeader] = values
                    } else {
                        result[lowerCaseHeader] = values
                    }
                }
            }
            return result
        }

        private fun logRequest(
            method: String,
            url: String,
            headers: Map<String, String> = HashMap(),
            body: String?
        ) {
            Logger.v(TAG) {
                val sb = StringBuilder()
                sb.appendLine("HTTP request (libcurl)")
                sb.appendLine("$method $url")
                for (pair in headers) {
                    sb.appendLine("${pair.key}: ${pair.value}")
                }
                if (body != null) {
                    sb.appendLine()
                    sb.appendLine(body)
                }
                sb.toString()
            }
        }

        fun <T> logExceptions(handle: () -> T): T {
            try {
                return handle()
            } catch (ex: Exception) {
                Logger.e("Plugin[${_package._config.name}]", ex.message, ex)
                throw ex
            }
        }

        private fun catchHttp(handle: () -> IBridgeHttpResponse): IBridgeHttpResponse {
            return try {
                handle()
            } catch (ex: SocketTimeoutException) {
                BridgeHttpStringResponse("", 408, null)
            }
        }
    }

    data class RequestDescriptor(
        val method: String,
        val url: String,
        val headers: MutableMap<String, String>,
        val body: String? = null,
        val contentType: String? = null,
        val respType: ReturnType = ReturnType.STRING,
        val impersonateTarget: String? = null,
        val useBuiltInHeaders: Boolean? = null,
        val timeoutMs: Int? = null
    )

    private fun catchHttp(handle: () -> BridgeHttpStringResponse): BridgeHttpStringResponse {
        return try {
            handle()
        } catch (ex: SocketTimeoutException) {
            BridgeHttpStringResponse("", 408, null)
        }
    }

    enum class ReturnType(val value: Int) {
        STRING(0),
        BYTES(1);
    }

    companion object {
        private const val TAG = "PackageHttpImp"
        private val WHITELISTED_RESPONSE_HEADERS = listOf(
            "content-type",
            "date",
            "content-length",
            "last-modified",
            "etag",
            "cache-control",
            "content-encoding",
            "content-disposition",
            "connection"
        )

        internal data class RequestOptions(
            val impersonateTarget: String? = null,
            val useBuiltInHeaders: Boolean? = null,
            val timeoutMs: Int? = null
        )

        internal fun parseRequestOptions(options: Map<String, Any?>?): RequestOptions {
            if (options == null) return RequestOptions()

            var impersonateTarget: String? = null
            var useBuiltInHeaders: Boolean? = null
            var timeoutMs: Int? = null

            options["impersonateTarget"]?.let { v ->
                val s = v as? String
                if (!s.isNullOrBlank()) {
                    impersonateTarget = s
                }
            }

            options["useBuiltInHeaders"]?.let { v ->
                if (v is Boolean) {
                    useBuiltInHeaders = v
                }
            }

            options["timeoutMs"]?.let { v ->
                val n: Long? = when (v) {
                    is Int -> v.toLong()
                    is Long -> v
                    is Double -> v.toLong()
                    is Float -> v.toLong()
                    else -> null
                }
                if (n != null && n > 0 && n <= Int.MAX_VALUE) {
                    timeoutMs = n.toInt()
                }
            }

            return RequestOptions(impersonateTarget, useBuiltInHeaders, timeoutMs)
        }
    }
}
