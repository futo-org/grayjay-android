package com.futo.platformplayer.views.overlays

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView
import android.widget.LinearLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.logging.Logger

class WebviewOverlay : LinearLayout {
    val onClose = Event0();

    private val _topbar: OverlayTopbar;
    private val _webview: WebView;


    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.overlay_webview, this)
        _topbar = findViewById(R.id.topbar);
        _webview = findViewById(R.id.webview);
        if (!isInEditMode){
            _webview.settings.javaScriptEnabled = true;
        }

        _topbar.onClose.subscribe(this, onClose::emit);
    }

    fun goto(url: String) {
        Logger.i("WebviewOverlay", "Loading [${url}]");
        _topbar.setInfo(url, "");
        _webview.loadUrl(url);
    }

    fun cleanup() {
        _topbar.onClose.remove(this);
    }
}