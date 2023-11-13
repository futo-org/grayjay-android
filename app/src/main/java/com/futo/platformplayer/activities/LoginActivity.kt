package com.futo.platformplayer.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import com.futo.platformplayer.api.media.platforms.js.SourcePluginAuthConfig
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.others.LoginWebViewClient
import com.futo.platformplayer.states.StateApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LoginActivity : AppCompatActivity() {
    private lateinit var _webView: WebView;
    private lateinit var _textUrl: TextView;
    private lateinit var _buttonClose: ImageButton;

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setNavigationBarColorAndIcons();

        _textUrl = findViewById(R.id.text_url);
        _buttonClose = findViewById(R.id.button_close);
        _buttonClose.setOnClickListener {
            finish();
        }


        _webView = findViewById(R.id.web_view);
        _webView.settings.javaScriptEnabled = true;
        CookieManager.getInstance().setAcceptCookie(true);

        val config = if(intent.hasExtra("plugin"))
            Json.decodeFromString<SourcePluginConfig>(intent.getStringExtra("plugin")!!);
        else null;

        val authConfig = if(config != null)
                config.authentication ?: throw IllegalStateException("Plugin has no authentication support");
            else if(intent.hasExtra("auth"))
                Json.decodeFromString<SourcePluginAuthConfig>(intent.getStringExtra("auth")!!);
            else throw IllegalStateException("No valid configuration?");
        //TODO: Backwards compat removal?

        _webView.settings.userAgentString = authConfig.userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36";
        _webView.settings.useWideViewPort = true;
        _webView.settings.loadWithOverviewMode = true;

        val webViewClient = if(config != null) LoginWebViewClient(config) else LoginWebViewClient(authConfig);

        webViewClient.onLogin.subscribe { auth ->
            _callback?.let {
                _callback = null;
                it.invoke(auth);
            }
            finish();
        };
        var isFirstLoad = true;
        webViewClient.onPageLoaded.subscribe { view, url ->
            _textUrl.setText(url ?: "");

            if(!isFirstLoad)
                return@subscribe;
            isFirstLoad = false;

            if(!authConfig.loginButton.isNullOrEmpty() && authConfig.loginButton.matches(REGEX_LOGIN_BUTTON)) {
                Logger.i(TAG, "Clicking login button [${authConfig.loginButton}]");
                //TODO: Find most reliable way to wait for page js to finish
                view?.evaluateJavascript("setTimeout(()=> document.querySelector(\"${authConfig.loginButton}\")?.click(), 1000)", {});
            }
        }
        _webView.settings.domStorageEnabled = true;

        /*
        _webView.webChromeClient = object: WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Logger.w(TAG, "Login Console: " + consoleMessage?.message());
                return super.onConsoleMessage(consoleMessage);
            }
        }*/
        _webView.webViewClient = webViewClient;
        _webView.loadUrl(authConfig.loginUrl);
    }

    override fun finish() {
        lifecycleScope.launch(Dispatchers.Main) {
            _webView?.loadUrl("about:blank");
        }
        _callback?.let {
            _callback = null;
            it.invoke(null);
        }
        super.finish();
    }

    companion object {
        private val TAG = "LoginActivity";
        private val REGEX_LOGIN_BUTTON = Regex("[a-zA-Z\\-\\.#_ ]*");

        private var _callback: ((SourceAuth?) -> Unit)? = null;

        fun getLoginIntent(context: Context, authConfig: SourcePluginAuthConfig): Intent {
            val intent = Intent(context, LoginActivity::class.java);
            intent.putExtra("auth", Json.encodeToString(authConfig));
            return intent;
        }
        fun getLoginIntent(context: Context, config: SourcePluginConfig): Intent {
            val intent = Intent(context, LoginActivity::class.java);
            intent.putExtra("plugin", Json.encodeToString(config));
            return intent;
        }

        fun showLogin(context: Context, authConfig: SourcePluginAuthConfig, callback: ((SourceAuth?) -> Unit)? = null) {
            if(_callback != null) _callback?.invoke(null);
            _callback = callback;
            context.startActivity(getLoginIntent(context, authConfig));
        }
        fun showLogin(context: Context, config: SourcePluginConfig, callback: ((SourceAuth?) -> Unit)? = null) {
            if(_callback != null) _callback?.invoke(null);
            _callback = callback;
            context.startActivity(getLoginIntent(context, config));
        }
    }
}