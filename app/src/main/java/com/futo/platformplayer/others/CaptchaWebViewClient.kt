package com.futo.platformplayer.others

import android.webkit.*
import com.futo.platformplayer.api.media.Serializer
import com.futo.platformplayer.api.media.platforms.js.SourceCaptchaData
import com.futo.platformplayer.api.media.platforms.js.SourcePluginAuthConfig
import com.futo.platformplayer.api.media.platforms.js.SourcePluginCaptchaConfig
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.logging.Logger
import kotlinx.serialization.encodeToString

class CaptchaWebViewClient : WebViewClient {
    val onCaptchaFinished = Event1<SourceCaptchaData?>();
    val onPageLoaded = Event2<WebView?, String?>()

    private val _pluginConfig: SourcePluginConfig?;
    private val _captchaConfig: SourcePluginCaptchaConfig;

    private var _didNotify = false;
    private val _extractor: WebViewRequirementExtractor;

    constructor(config: SourcePluginConfig) : super() {
        _pluginConfig = config;
        _captchaConfig = config.captcha!!;
        _extractor = WebViewRequirementExtractor(
            config.allowUrls,
            null,
            null,
            config.captcha!!.cookiesToFind,
            config.captcha!!.completionUrl,
            config.captcha!!.cookiesExclOthers
        );
        Logger.i(TAG, "Captcha [${config.name}]" +
                "\nRequired Cookies: ${Serializer.json.encodeToString(config.captcha!!.cookiesToFind)}",);
    }
    constructor(captcha: SourcePluginCaptchaConfig) : super() {
        _pluginConfig = null;
        _captchaConfig = captcha;
        _extractor = WebViewRequirementExtractor(
            null,
            null,
            null,
            captcha.cookiesToFind,
            captcha.completionUrl,
            captcha.cookiesExclOthers
        );
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url);
        Logger.i(TAG, "onPageFinished url = ${url}")
        onPageLoaded.emit(view, url);
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        if(request == null)
            return super.shouldInterceptRequest(view, request as WebResourceRequest?);

        val extracted = _extractor.handleRequest(view, request);
        if(extracted != null && !_didNotify) {
            _didNotify = true;
            onCaptchaFinished.emit(SourceCaptchaData(
               extracted.cookies,
               extracted.headers
            ));
        }

        return super.shouldInterceptRequest(view, request);
    }

    companion object {
        private val TAG = "CaptchaWebViewClient";
    }
}