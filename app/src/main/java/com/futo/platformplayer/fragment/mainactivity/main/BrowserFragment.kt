package com.futo.platformplayer.fragment.mainactivity.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.views.adapters.SubscriptionAdapter

class BrowserFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = false;
    override val hasBottomBar: Boolean get() = true;

    private var _root: LinearLayout? = null;
    private var _webview: WebView? = null;
    private val _webviewWithoutHandling = object: WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            return false;
        }
    };


    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_browser, container, false);
        _root = view.findViewById<LinearLayout>(R.id.root);
        _webview = view.findViewById<WebView?>(R.id.webview).apply {
            this.webViewClient = _webviewWithoutHandling;
            this.settings.javaScriptEnabled = true;
            CookieManager.getInstance().setAcceptCookie(true);
            this.settings.domStorageEnabled = true;
        };
        return view;
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack)

        if(parameter is WebView) {
            _root?.removeView(_webview);
            _root?.addView(parameter);
            _webview = parameter;
        }
        else if(parameter is String) {
            _webview?.webViewClient = _webviewWithoutHandling;
            _webview?.loadUrl(parameter);
        }
        else if(parameter is NavigateOptions) {
            if(parameter.urlHandlers != null && parameter.urlHandlers.isNotEmpty())
                _webview?.webViewClient = object: WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val schema = request?.url?.scheme;
                        if(schema != null && parameter.urlHandlers.containsKey(schema)) {
                            parameter.urlHandlers[schema]?.invoke(request);
                            return true;
                        }
                        return false;
                    }
                };
            else
                _webview?.webViewClient = _webviewWithoutHandling;
            _webview?.loadUrl(parameter.url);
        }
        else
            _webview?.loadUrl("about:blank");
    }

    override fun onHide() {
        super.onHide()
        _webview?.loadUrl("about:blank");
    }

    override fun onBackPressed(): Boolean {
        return false;
    }

    companion object {
        fun newInstance() = BrowserFragment().apply {}
    }

    class NavigateOptions(
        val url: String,
        val urlHandlers: Map<String, (WebResourceRequest)->Unit>? = null
    )
}