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
import com.futo.platformplayer.api.media.platforms.js.SourceCaptchaData
import com.futo.platformplayer.api.media.platforms.js.SourcePluginAuthConfig
import com.futo.platformplayer.api.media.platforms.js.SourcePluginCaptchaConfig
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.others.CaptchaWebViewClient
import com.futo.platformplayer.others.LoginWebViewClient
import com.futo.platformplayer.states.StateApp
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

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_captcha);
        setNavigationBarColorAndIcons();

        _buttonClose = findViewById(R.id.button_close);
        _buttonClose.setOnClickListener { finish(); };

        _webView = findViewById(R.id.web_view);
        _webView.settings.javaScriptEnabled = true;
        CookieManager.getInstance().setAcceptCookie(true);


        val config = if(intent.hasExtra("plugin"))
            Json.decodeFromString<SourcePluginConfig>(intent.getStringExtra("plugin")!!);
        else null;

        val captchaConfig = if(config != null)
            config.captcha ?: throw IllegalStateException("Plugin has no captcha support");
        else if(intent.hasExtra("captcha"))
            Json.decodeFromString<SourcePluginCaptchaConfig>(intent.getStringExtra("captcha")!!);
        else throw IllegalStateException("No valid configuration?");
        //TODO: Backwards compat removal?

        val extraUrl = if (intent.hasExtra("url"))
            intent.getStringExtra("url");
        else null;

        val extraBody = if (intent.hasExtra("body"))
            intent.getStringExtra("body");
        else null;

        _webView.settings.userAgentString = captchaConfig.userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36";
        _webView.settings.useWideViewPort = true;
        _webView.settings.loadWithOverviewMode = true;

        val webViewClient = if(config != null) CaptchaWebViewClient(config) else CaptchaWebViewClient(captchaConfig);
        webViewClient.onCaptchaFinished.subscribe { captcha ->
            _callback?.let {
                _callback = null;
                it.invoke(captcha);
            }
            finish();
        };
        _webView.settings.domStorageEnabled = true;
        _webView.webViewClient = webViewClient;

        if(captchaConfig.captchaUrl != null)
            _webView.loadUrl(captchaConfig.captchaUrl);
        else if(extraUrl != null && extraBody != null)
            _webView.loadDataWithBaseURL(extraUrl, extraBody, "text/html", "utf-8", null);
        else if(extraUrl != null)
            _webView.loadUrl(extraUrl);
        else throw IllegalStateException("No valid captcha info provided");
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
        private var _callback: ((SourceCaptchaData?) -> Unit)? = null;

        private fun getCaptchaIntent(context: Context, config: SourcePluginConfig, url: String? = null, body: String? = null): Intent {
            val intent = Intent(context, CaptchaActivity::class.java);
            if(url != null)
                intent.putExtra("url", url);
            if(body != null)
                intent.putExtra("body", body);
            intent.putExtra("plugin", Json.encodeToString(config));
            return intent;
        }

        fun showCaptcha(context: Context, config: SourcePluginConfig, url: String? = null, body: String? = null, callback: ((SourceCaptchaData?) -> Unit)? = null) {
            _callback = callback;
            context.startActivity(getCaptchaIntent(context, config, url, body));
        }
    }
}