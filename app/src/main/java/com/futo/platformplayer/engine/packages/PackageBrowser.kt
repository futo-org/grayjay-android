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
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

class PackageBrowser: V8Package {
    override val name: String get() = "Browser";
    override val variableName: String = "browser";

    @Transient
    private val _readySemaphore = java.util.concurrent.Semaphore(1);
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
                _browser?.settings?.blockNetworkImage = true;
                _browser?.settings?.blockNetworkLoads = true;
                _browser?.settings?.allowContentAccess = false;
                _browser?.settings?.allowFileAccess = false;
                _browser?.webViewClient = object : WebViewClient() {
                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        super.onPageCommitVisible(view, url)
                        _readySemaphore.release();
                        Logger.i("PackageBrowser", "Browser loaded");
                    }
                }
                _browser?.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        if(consoleMessage?.messageLevel() == ConsoleMessage.MessageLevel.ERROR)
                            Logger.e("PackageBrowser", "Console Error:${consoleMessage?.message()} [${consoleMessage?.lineNumber()}]" ?: "");
                        else
                            Logger.i("PackageBrowser", "Console Log:" + consoleMessage?.message() ?: "");
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
        _browser = null;
    }

    @V8Function
    fun getCurrentUrl(): String? {
        return browser.url;
    }
    @V8Function
    fun waitTillLoaded() {
        if(!_readySemaphore.tryAcquire()) {
            Logger.i("PackageBrowser", "Waiting for browser to be ready");
            _readySemaphore.acquire();
        }
        _readySemaphore.release();
        Logger.i("PackageBrowser", "Browser is ready");
    }

    @V8Function
    fun load(url: String, html: String? = null) {
        if(html != null)
            Logger.i("PackageBrowser", "Browser loading html with base url [${url}]");
        else
            Logger.i("PackageBrowser", "Browser loading url [${url}]");
        _readySemaphore.acquire();
        StateApp.instance.scope.launch(Dispatchers.Main) {
            if (html == null)
                browser.loadUrl(url);
            else
                browser.loadDataWithBaseURL(url, html, "text/html", "utf-8", null);
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
                        funcClone?.callVoid(null, arrayOf(value));
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
            if(callback != null)
                callback.invoke(result);
        }

        @JavascriptInterface
        fun log(msg: String) {
            Logger.i("PackageBrowser", "Log: " + msg);
        }

    }
}