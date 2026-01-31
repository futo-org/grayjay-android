package com.futo.platformplayer.engine.packages

import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.collection.emptyLongSet
import com.caoccao.javet.annotations.V8Function
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
import kotlinx.coroutines.withTimeout

class PackageBrowser: V8Package {
    override val name: String get() = "Browser";
    override val variableName: String = "browser";

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

    constructor(v8Plugin: V8Plugin): super(v8Plugin) {

    }
    @V8Function
    fun initialize() {
        if(_browser == null){
            StateApp.instance.scope.launch(Dispatchers.Main) {
                _browser = WebView(StateApp.instance.contextOrNull ?: return@launch);
                _browser?.settings?.javaScriptEnabled = true;
                _browser?.settings?.blockNetworkImage = false;
                _browser?.settings?.blockNetworkLoads = false;
                _browser?.settings?.allowContentAccess = false;
                _browser?.settings?.allowFileAccess = false;
                //_browser?.settings?.useWideViewPort = true;
                //_browser?.settings?.loadWithOverviewMode = true;
                _browser?.webViewClient = object : WebViewClient() {
                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        super.onPageCommitVisible(view, url)
                        _readySemaphore?.release();
                        _readySemaphore = null;
                        Logger.i("PackageBrowser", "Browser loaded");
                    }
                }
                _browser?.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        if(consoleMessage?.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            val msg = "Browser Error:${consoleMessage?.message()} [${consoleMessage?.lineNumber()}]" ?: ""
                            Logger.e("PackageBrowser", msg);
                            if(_plugin.config is SourcePluginConfig && _plugin.config.id == StateDeveloper.DEV_ID)
                                StateDeveloper.instance.logDevException(StateDeveloper.instance.currentDevID ?: "", msg)
                        }
                        else {
                            val msg = "Browser Log:" + consoleMessage?.message() ?: "";
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
        _browser?.destroy();
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
            browser.loadUrl(url);
        }
    }

    @V8Function
    fun run(js: String, callbackId: String? = null, callback: V8ValueFunction? = null) {
        waitTillLoaded();
        val funcClone = callback?.toClone<V8ValueFunction>()
        if(callbackId != null && callback != null) {
            synchronized(_callbacks) {
                _callbacks.put(callbackId, {
                    funcClone?.callVoid(null, arrayOf(it));
                });
            }
        }
        StateApp.instance.scope.launch(Dispatchers.Main) {
            try {
                Logger.i("PackageBrowser", "Browser running JS with callback [${callbackId}]\n${(if(js.length > 200) (js.substring(0, 200) + "...") else js)})");
                browser.evaluateJavascript(js, object : ValueCallback<String> {
                    override fun onReceiveValue(value: String?) {
                        Logger.i("PackageBrowser", "Browser run finished");
                    }
                })
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
                            funcClone?.callVoid(null, arrayOf(value));
                        }
                    }
                })
            }
            catch(ex: Throwable) {
                Logger.e("PackageBrowser", "Failed to invoke browser", ex);
            }
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