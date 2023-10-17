package com.futo.platformplayer.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import com.futo.platformplayer.api.media.platforms.js.SourcePluginAuthConfig
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.others.CaptchaWebViewClient
import com.futo.platformplayer.others.LoginWebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.Exception
import java.util.UUID

class CaptchaActivity : AppCompatActivity() {
    private lateinit var _webView: WebView;
    private lateinit var _buttonClose: Button;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_captcha);
        setNavigationBarColorAndIcons();

        _buttonClose = findViewById(R.id.button_close);
        _buttonClose.setOnClickListener { finish(); };

        _webView = findViewById(R.id.web_view);
        _webView.settings.javaScriptEnabled = true;
        CookieManager.getInstance().setAcceptCookie(true);

        val url = if (intent.hasExtra("url"))
            intent.getStringExtra("url");
        else null;

        if (url == null) {
            throw Exception("URL is missing");
        }

        val body = if (intent.hasExtra("body"))
            intent.getStringExtra("body");
        else null;

        if (body == null) {
            throw Exception("Body is missing");
        }

        _webView.settings.useWideViewPort = true;
        _webView.settings.loadWithOverviewMode = true;

        val webViewClient = CaptchaWebViewClient();
        webViewClient.onCaptchaFinished.subscribe { googleAbuseCookie ->
            Logger.i(TAG, "Abuse cookie found: $googleAbuseCookie");
            _callback?.let {
                _callback = null;
                it.invoke(googleAbuseCookie);
            }
            finish();
        };
        _webView.settings.domStorageEnabled = true;
        _webView.webViewClient = webViewClient;
        _webView.loadDataWithBaseURL(url, body, "text/html", "utf-8", null);
        //_webView.loadUrl(url);
    }

    override fun finish() {
        lifecycleScope.launch(Dispatchers.Main) {
            _webView.loadUrl("about:blank");
        }
        _callback?.let {
            _callback = null;
            it.invoke(null);
        }
        super.finish();
    }

    companion object {
        private val TAG = "CaptchaActivity";
        private var _callback: ((String?) -> Unit)? = null;

        private fun getCaptchaIntent(context: Context, url: String, body: String): Intent {
            val intent = Intent(context, CaptchaActivity::class.java);
            intent.putExtra("url", url);
            intent.putExtra("body", body);
            return intent;
        }

        fun showCaptcha(context: Context, url: String, body: String, callback: ((String?) -> Unit)? = null) {
            val cookieManager = CookieManager.getInstance();
            val cookieString = cookieManager.getCookie("https://youtube.com")
            val cookieMap = cookieString.split(";")
                .map { it.trim() }
                .map { it.split("=", limit = 2) }
                .filter { it.size == 2 }
                .associate { it[0] to it[1] };

            if (cookieMap.containsKey("GOOGLE_ABUSE_EXEMPTION")) {
                callback?.invoke("GOOGLE_ABUSE_EXEMPTION=" + cookieMap["GOOGLE_ABUSE_EXEMPTION"]);
                return;
            }

            _callback = callback;
            context.startActivity(getCaptchaIntent(context, url, body));
        }
    }
}