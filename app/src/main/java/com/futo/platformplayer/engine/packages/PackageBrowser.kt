package com.futo.platformplayer.engine.packages

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.utils.JavetResourceUtils
import com.caoccao.javet.values.reference.V8ValueFunction
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.fragment.mainactivity.main.BrowserFragment
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateDeveloper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import androidx.core.net.toUri

class PackageBrowser: V8Package {
    val useAddDocumentStartJavaScript = true

    override val name: String get() = "Browser";
    override val variableName: String = "browser";

    @Volatile private var _loadToken: String? = null
    @Volatile private var _expectedMainUrl: String? = null

    private val _json = Json { };

    @Transient
    private val _pageLoadScriptRefs = ConcurrentHashMap<String, ScriptHandler>()

    @Transient
    private val _pageLoadScriptsFallback = ConcurrentHashMap<String, String>()

    @Transient
    private var _readySemaphore: Semaphore? = null;
    @Transient
    private val _callbacks = mutableMapOf<String, (String?)->Unit>();
    @Transient
    private var _browser: WebView? = null;
    private val browser: WebView get() {
        if(_browser == null)
            throw IllegalStateException("Browser not initialized");
        return _browser!!;
    }

    @Volatile
    private var _userAgent: String = ""
    private val http = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    constructor(v8Plugin: V8Plugin): super(v8Plugin) {

    }
    @V8Function
    fun initialize() {
        if (_browser != null) return

        onMainBlocking {
            _browser = WebView(StateApp.instance.contextOrNull ?: return@onMainBlocking);
            _userAgent = _browser?.settings?.userAgentString.orEmpty()
            _browser?.settings?.javaScriptEnabled = true;
            _browser?.settings?.blockNetworkImage = false;
            _browser?.settings?.blockNetworkLoads = false;
            _browser?.settings?.allowContentAccess = false;
            _browser?.settings?.allowFileAccess = false;
            //_browser?.settings?.useWideViewPort = true;
            //_browser?.settings?.loadWithOverviewMode = true;
            _browser?.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    if (view == null || request == null) return null

                    if (useAddDocumentStartJavaScript && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return null
                    if (!request.isForMainFrame) return null
                    if (!request.method.equals("GET", ignoreCase = true)) return null

                    val url = request.url?.toString() ?: return null
                    Log.i("PackageBrowser", "shouldInterceptRequest: " + url)
                    val scheme = request.url?.scheme ?: return null
                    if (scheme != "http" && scheme != "https") return null

                    val scripts = _pageLoadScriptsFallback.values.toList()
                    if (scripts.isEmpty()) return null

                    return try {
                        val cookie = request.requestHeaders["Cookie"] ?: runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
                        val ua = request.requestHeaders["User-Agent"] ?: _userAgent

                        val okReq = Request.Builder()
                            .url(url)
                            .get()
                            .header("User-Agent", ua)
                            .apply { if (!cookie.isNullOrEmpty()) header("Cookie", cookie) }
                            .build()

                        http.newCall(okReq).execute().use { resp ->
                            val code = resp.code
                            val reason = resp.message.ifBlank { "OK" }
                            if (code in 300..399) return null

                            val contentType = resp.header("Content-Type") ?: ""
                            val isHtml =
                                contentType.startsWith("text/html", ignoreCase = true) ||
                                        contentType.startsWith("application/xhtml+xml", ignoreCase = true)

                            if (!isHtml) return null

                            val bodyBytes = resp.body.bytes()
                            val charset = charsetFromContentType(contentType) ?: Charsets.UTF_8
                            val html = bodyBytes.toString(charset)

                            val cspHeader = resp.header("Content-Security-Policy")
                                ?: resp.header("Content-Security-Policy-Report-Only")

                            val nonce = extractNonceFromCsp(cspHeader) ?: extractNonceFromHtml(html)

                            val injected = injectIntoHead(html, scripts.joinToString("\n"), nonce)
                            val outBytes = injected.toByteArray(charset)
                            val headers = resp.headers.toMultimap()
                                .mapValues { it.value.joinToString(",") }
                                .toMutableMap()

                            headers.remove("Content-Length")
                            val cookieMgr = CookieManager.getInstance()
                            resp.headers.values("Set-Cookie").forEach { sc ->
                                try { cookieMgr.setCookie(url, sc) } catch (_: Throwable) {}
                            }
                            try { cookieMgr.flush() } catch (_: Throwable) {}

                            WebResourceResponse("text/html", charset.name(), code, reason, headers, ByteArrayInputStream(outBytes))
                        }
                    } catch (_: Throwable) {
                        null
                    }
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    Logger.i("PackageBrowser", "Browser loaded (commit visible): $url")
                    releaseReadyIfCurrent(url)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Logger.i("PackageBrowser", "Browser loaded (finished): $url")
                    releaseReadyIfCurrent(url)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false;
                }
            }
            _browser?.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    try {
                        val raw = consoleMessage?.message().orEmpty()

                        val normalized = raw.trim().let { s ->
                            if (s.length >= 2 && s.first() == '"' && s.last() == '"') {
                                s.substring(1, s.length - 1)
                            } else s
                        }

                        if (normalized.startsWith(CONSOLE_BRIDGE_PREFIX)) {
                            val payload = normalized.substring(CONSOLE_BRIDGE_PREFIX.length)
                            if (handleConsoleBridgeMessage(payload)) return true
                        }

                        if (consoleMessage?.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            val emsg =
                                "Browser Error:${consoleMessage.message()} [${consoleMessage.lineNumber()}]"
                            Logger.e("PackageBrowser", emsg)
                            if (_plugin.config is SourcePluginConfig && _plugin.config.id == StateDeveloper.DEV_ID)
                                StateDeveloper.instance.logDevException(
                                    StateDeveloper.instance.currentDevID ?: "", emsg
                                )
                        } else {
                            val imsg = "Browser Log:${consoleMessage?.message()}"
                            Logger.i("PackageBrowser", imsg)
                            if (_plugin.config is SourcePluginConfig && _plugin.config.id == StateDeveloper.DEV_ID)
                                StateDeveloper.instance.logDevInfo(
                                    StateDeveloper.instance.currentDevID ?: "", imsg
                                )
                        }

                        return super.onConsoleMessage(consoleMessage)
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to handle onConsoleMessage", e)
                    }
                }
            }
        }

        val bootstrap = """
            (() => {
              try {
                if (window.__GJ) return;
            
                const PREFIX = ${CONSOLE_BRIDGE_PREFIX.quoteForJs()};
                const emit = (obj) => {
                  try {
                    console.info(PREFIX + JSON.stringify(obj));
                  } catch (_) {}
                };
            
                Object.defineProperty(window, "__GJ", {
                  value: {
                    callback: (id, result) => {
                      try {
                        const r = (typeof result === "string")
                          ? result
                          : (() => { try { return JSON.stringify(result); } catch (_) { return String(result); } })();
                        emit({ t: "cb", id: String(id), result: r });
                      } catch (_) {}
                    },
                    log: (msg) => {
                      try { emit({ t: "log", msg: String(msg) }); } catch (_) {}
                    }
                  },
                  enumerable: false,
                  configurable: false,
                  writable: false
                });
              } catch (_) {}
            })();
            """.trimIndent()

        addScriptOnLoad(bootstrap)
    }
    @V8Function
    fun deinitialize() {
        StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
            _browser?.destroy();
        }
        _browser = null;
    }

    @V8Function
    fun getCurrentUrl(): String? {
        return browser.url;
    }
    @V8Function
    fun waitTillLoaded(timeout: Int = 1000): Boolean {
        val acquired = _readySemaphore?.let {
            if(!it.tryAcquire()) {
                Logger.i("PackageBrowser", "Waiting for browser to be ready");
                if(!runBlocking {
                    try {
                        return@runBlocking withTimeout(timeout.toLong(), {
                            it.acquire()
                            return@withTimeout true;
                        });
                    }
                    catch(ex: TimeoutCancellationException) {
                        return@runBlocking false;
                    }
                }) return@let false;
            }
            it.release();
            return@let true;
        } ?: true;
        if(acquired)
            Logger.i("PackageBrowser", "Browser is ready");
        else
            Logger.i("PackageBrowser", "Browser failed wait ready");
        return acquired;
    }

    @V8Function
    fun load(url: String) {
        Logger.i("PackageBrowser", "Browser loading url [$url]")
        val token = UUID.randomUUID().toString()
        _loadToken = token
        _expectedMainUrl = url
        _readySemaphore = Semaphore(1, acquiredPermits = 1)

        StateApp.instance.scope.launch(Dispatchers.Main) {
            try { browser.loadUrl(url) }
            catch (t: Throwable) { Logger.e("PackageBrowser", "loadUrl failed", t) }
        }
    }

    private fun releaseReadyIfCurrent(url: String?) {
        if (url == null) return
        val expected = _expectedMainUrl
        if (url.trimEnd('/') != expected?.trimEnd('/')) return

        _readySemaphore?.release()
        _readySemaphore = null
        _expectedMainUrl = null
    }

    @V8Function
    fun run(js: String, callbackId: String? = null, callback: V8ValueFunction? = null) {
        waitTillLoaded();
        val funcClone = callback?.toClone<V8ValueFunction>()
        if(callbackId != null && callback != null) {
            synchronized(_callbacks) {
                _callbacks.put(callbackId, {
                    _plugin.busy {
                        funcClone?.callVoid(null, arrayOf(it));
                    }
                    if (!_plugin.isStopped)
                        JavetResourceUtils.safeClose(funcClone);
                });
            }
        }
        StateApp.instance.scope.launch(Dispatchers.Main) {
            try {
                try {
                    Logger.i("PackageBrowser", "Browser running JS with callback [${callbackId}]\n${(if(js.length > 200) (js.substring(0, 200) + "...") else js)})");
                    browser.evaluateJavascript(js, object : ValueCallback<String> {
                        override fun onReceiveValue(value: String?) {
                            Logger.i("PackageBrowser", "Browser run finished");
                        }
                    })
                }
                catch(ex: Throwable) {
                    Logger.e("PackageBrowser", "Browser running failed: " + ex.message, ex);
                }
            }
            catch(ex: Throwable) {
                Logger.e("PackageBrowser", "Failed to invoke browser", ex);
            }
        }
    }
    @V8Function
    fun runWithReturn(js: String, callback: V8ValueFunction? = null) {
        waitTillLoaded();
        val funcClone = callback?.toClone<V8ValueFunction>()
        StateApp.instance.scope.launch(Dispatchers.Main) {
            try {
                Logger.i("PackageBrowser", "Browser running JS with callback [sync]\n${(if(js.length > 200) (js.substring(0, 200) + "...") else js)})");
                browser.evaluateJavascript(js, object : ValueCallback<String> {
                    override fun onReceiveValue(value: String?) {
                        Logger.i("PackageBrowser", "Browser run returned: " + (value ?: ""));
                        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                            Logger.i("PackageBrowser", "Invoking V8 with result (${funcClone != null})");
                            try {
                                _plugin.busy {
                                    if (value != null) {
                                        val json = _json.decodeFromString<String>(value);
                                        funcClone?.callVoid(null, arrayOf(json));
                                    } else
                                        funcClone?.callVoid(null, arrayOf((null as String?)));
                                }
                                if (!_plugin.isStopped)
                                    JavetResourceUtils.safeClose(funcClone);
                            }
                            catch(ex: Throwable) {
                                Logger.e("PackageBrowser", "Browser Failed to callback: " + ex.message, ex);

                            }
                        }
                    }
                })
            }
            catch(ex: Throwable) {
                Logger.e("PackageBrowser", "Browser Failed to invoke browser", ex);
            }
        }
    }

    @V8Function
    fun addScriptOnLoad(js: String): String {
        require(js.isNotBlank()) { "Script must be non-empty." }

        val id = UUID.randomUUID().toString()
        onMainBlocking {
            if (useAddDocumentStartJavaScript && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                val ref = WebViewCompat.addDocumentStartJavaScript(browser, js, setOf("*"))
                _pageLoadScriptRefs[id] = ref
            } else {
                _pageLoadScriptsFallback[id] = js
            }
        }

        Logger.i("PackageBrowser", "addScriptOnLoad() registered (id=$id)")
        return id
    }

    @SuppressLint("RequiresFeature")
    @V8Function
    fun removeScriptOnLoad(identifier: String): Boolean {
        if (identifier.isBlank()) return false

        val ref = _pageLoadScriptRefs.remove(identifier)
        val removedFallback = _pageLoadScriptsFallback.remove(identifier) != null

        if (ref != null) {
            onMainBlocking {
                try { ref.remove() } catch (_: Throwable) {}
            }
            Logger.i("PackageBrowser", "removeScriptOnLoad() removed (id=$identifier)")
            return true
        }

        if (removedFallback) {
            Logger.i("PackageBrowser", "removeScriptOnLoad() removed fallback (id=$identifier)")
            return true
        }

        return false
    }

    @SuppressLint("RequiresFeature")
    @V8Function
    fun clearScriptsOnLoad() {
        val refs = _pageLoadScriptRefs.values.toList()
        _pageLoadScriptRefs.clear()
        _pageLoadScriptsFallback.clear()

        onMainBlocking {
            for (r in refs) {
                try { r.remove() } catch (_: Throwable) {}
            }
        }

        Logger.i("PackageBrowser", "clearScriptsOnLoad() cleared")
    }

    private fun charsetFromContentType(ct: String): Charset? {
        val m = Regex("(?i)charset=([\\w\\-]+)").find(ct) ?: return null
        val name = m.groupValues.getOrNull(1)?.trim().orEmpty()
        return runCatching { Charset.forName(name) }.getOrNull()
    }

    private fun injectIntoHead(html: String, js: String, nonce: String?): String {
        val nonceAttr = nonce?.let { " nonce=\"${escapeHtmlAttr(it)}\"" } ?: ""
        val tag = "<script$nonceAttr>\n$js\n</script>\n"

        val head = Regex("(?i)<head[^>]*>").find(html)
        if (head != null) {
            val i = head.range.last + 1
            return buildString(html.length + tag.length + 8) {
                append(html, 0, i)
                append('\n')
                append(tag)
                append(html, i, html.length)
            }
        }
        return tag + html
    }

    private fun <T> onMainBlocking(block: () -> T): T {
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else runBlocking {
            withContext(Dispatchers.Main) { block() }
        }
    }

    private fun extractNonceFromCsp(csp: String?): String? {
        if (csp.isNullOrBlank()) return null
        val m = Regex("(?i)'nonce-([^'\\s;]+)'").find(csp) ?: return null
        return m.groupValues[1]
    }

    private fun extractNonceFromHtml(html: String): String? {
        val m = Regex("(?i)<script[^>]*\\snonce\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>").find(html)
        return m?.groupValues?.get(1)
    }

    private fun escapeHtmlAttr(s: String): String =
        s.replace("&", "&amp;").replace("\"", "&quot;")

    @Serializable
    private data class ConsoleBridgeMsg(
        val t: String,
        val id: String? = null,
        val result: String? = null,
        val msg: String? = null
    )


    private fun handleConsoleBridgeMessage(payload: String): Boolean {
        Logger.i("PackageBrowser", "handleConsoleBridgeMessage: " + payload)

        val parsed = runCatching { _json.decodeFromString<ConsoleBridgeMsg>(payload) }.getOrNull()
            ?: return false

        when (parsed.t) {
            "cb" -> {
                val id = parsed.id ?: return true
                val res = parsed.result

                val cb = synchronized(_callbacks) { _callbacks.remove(id) } ?: return true
                StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                    try {
                        cb.invoke(res)
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to invoke callback asynchronously", e)
                    }
                }
                return true
            }
            "log" -> {
                val text = parsed.msg.orEmpty()
                Logger.i("PackageBrowser", "Browser Log: $text")
                if (_plugin.config is SourcePluginConfig && _plugin.config.id == StateDeveloper.DEV_ID) {
                    StateDeveloper.instance.logDevInfo(StateDeveloper.instance.currentDevID ?: "", text)
                }
                return true
            }
            else -> return true
        }
    }

    private companion object {
        private const val CONSOLE_BRIDGE_PREFIX = "__GJ__:"
        private const val TAG = "PackageBrowser"

        private fun String.quoteForJs(): String {
            val s = this
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            return "\"$s\""
        }
    }
}