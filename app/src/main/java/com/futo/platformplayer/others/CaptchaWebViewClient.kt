package com.futo.platformplayer.others

import android.webkit.*
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.logging.Logger

class CaptchaWebViewClient : WebViewClient {
    val onCaptchaFinished = Event1<String>();
    val onPageLoaded = Event2<WebView?, String?>()

    constructor() : super() {}

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url);
        Logger.i(TAG, "onPageFinished url = ${url}")
        onPageLoaded.emit(view, url);
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        if(request == null)
            return super.shouldInterceptRequest(view, request as WebResourceRequest?);

        Logger.i(TAG, "shouldInterceptRequest url = ${request.url}")
        if (request.url.isHierarchical) {
            val googleAbuse = request.url.getQueryParameter("google_abuse");
            if (googleAbuse != null) {
                onCaptchaFinished.emit(googleAbuse);
            }
        }

        return super.shouldInterceptRequest(view, request);
    }

    companion object {
        private val TAG = "CaptchaWebViewClient";
    }
}