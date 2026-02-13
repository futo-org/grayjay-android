package com.futo.platformplayer.engine.packages

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
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
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

class PackageBrowser: V8Package {
    override val name: String get() = "Browser";
    override val variableName: String = "browser";

    private val _json = Json { };

    @Transient
    private val _pageLoadScriptsFallback = ConcurrentHashMap<String, String>()

    @Transient
    private var _readySemaphore: Semaphore? = null;
    @Transient
    private val _callbacks = mutableMapOf<String, (String?)->Unit>();
    @Transient
    private val _interop = JSInterop(this);
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
        if(_browser == null){
            StateApp.instance.scope.launch(Dispatchers.Main) {
                _browser = WebView(StateApp.instance.contextOrNull ?: return@launch);
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

                        if (!request.isForMainFrame) return null
                        if (!request.method.equals("GET", ignoreCase = true)) return null

                        val url = request.url?.toString() ?: return null
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

                                val injected = injectIntoHead(html, scripts.joinToString("\n"))
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
                        _readySemaphore?.release();
                        _readySemaphore = null;
                        Logger.i("PackageBrowser", "Browser loaded");
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return false;
                    }
                }
                _browser?.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        if(consoleMessage?.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            val msg = "Browser Error:${consoleMessage.message()} [${consoleMessage.lineNumber()}]"
                            Logger.e("PackageBrowser", msg);
                            if(_plugin.config is SourcePluginConfig && _plugin.config.id == StateDeveloper.DEV_ID)
                                StateDeveloper.instance.logDevException(StateDeveloper.instance.currentDevID ?: "", msg)
                        }
                        else {
                            val msg = ("Browser Log:" + consoleMessage?.message());
                            Logger.e("PackageBrowser", msg);
                            if(_plugin.config is SourcePluginConfig && _plugin.config.id == StateDeveloper.DEV_ID)
                                StateDeveloper.instance.logDevInfo(StateDeveloper.instance.currentDevID ?: "", msg);
                        }
                        return super.onConsoleMessage(consoleMessage)
                    }
                }
                _browser?.addJavascriptInterface(_interop, "__GJ");
            }
            return;
        }
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
        Logger.i("PackageBrowser", "Browser loading url [${url}]");
        _readySemaphore = Semaphore(1, 1);
        StateApp.instance.scope.launch(Dispatchers.Main) {
            try {
                browser.loadUrl(url);
            } catch(ex: Throwable) {}
        }
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
            _pageLoadScriptsFallback[id] = js
        }

        Logger.i("PackageBrowser", "addScriptOnLoad() registered (id=$id)")
        return id
    }

    private fun charsetFromContentType(ct: String): Charset? {
        val m = Regex("(?i)charset=([\\w\\-]+)").find(ct) ?: return null
        val name = m.groupValues.getOrNull(1)?.trim().orEmpty()
        return runCatching { Charset.forName(name) }.getOrNull()
    }

    private fun injectIntoHead(html: String, js: String): String {
        val tag = "<script>\n$js\n</script>\n"

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

    class JSInterop(private val pack: PackageBrowser) {

        @JavascriptInterface
        fun callback(id: String, result: String) {
            Logger.i("PackageBrowser", "Browser Callback [${id}]: ${result}");
            val callback = synchronized(pack._callbacks) { pack._callbacks.remove(id); };
            if(callback != null) {
                StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                    callback.invoke(result);
                }
            }
        }

        @JavascriptInterface
        fun log(msg: String) {
            Logger.i("PackageBrowser", "Log: " + msg);
        }

    }
}