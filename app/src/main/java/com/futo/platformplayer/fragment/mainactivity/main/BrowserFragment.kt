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
import android.widget.Spinner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.views.adapters.SubscriptionAdapter

class BrowserFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = false;
    override val hasBottomBar: Boolean get() = true;

    private var _webview: WebView? = null;

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_browser, container, false);
        _webview = view.findViewById<WebView?>(R.id.webview).apply {
            this.webViewClient = object: WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false;
                }
            };
            this.settings.javaScriptEnabled = true;
            CookieManager.getInstance().setAcceptCookie(true);
            this.settings.domStorageEnabled = true;
        };
        return view;
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack)

        if(parameter is String)
            _webview?.loadUrl(parameter);
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
}