package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.platforms.js.SourceAuth
import com.futo.platformplayer.api.media.platforms.js.SourcePluginAuthConfig
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.others.LoginWebViewClient
import com.futo.platformplayer.states.StateApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.text.matches


class LoginFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var view: FragView? = null;


    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val newView = FragView(this);
        view = newView;
        return newView;
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        view?.onShown(parameter ?: throw IllegalArgumentException("No parameter for login"));
    }

    override fun onDestroyMainView() {
        view = null;
        super.onDestroyMainView();
    }

    companion object {
        fun newInstance() = LoginFragment().apply {}

        private var _callback: ((SourceAuth?) -> Unit)? = null;
        fun showLogin(config: SourcePluginConfig, callback: ((SourceAuth?) -> Unit)? = null) {
            if(_callback != null) _callback?.invoke(null);
            _callback = callback;
            StateApp.instance.activity?.navigate<LoginFragment>(config, true);
        }
    }


    class FragView: ConstraintLayout {
        val fragment: LoginFragment;

        private val _webView: WebView;
        private val _textUrl: TextView;
        private val _buttonClose: ImageButton;

        constructor(fragment: LoginFragment) : super(fragment.requireContext()) {
            inflate(context, R.layout.activity_login, this);
            this.fragment = fragment;

            _textUrl = findViewById(R.id.text_url);
            _buttonClose = findViewById(R.id.button_close);
            _buttonClose.setOnClickListener {
                UIDialogs.toast("Login cancelled", false);
                fragment.close(true);
            }


            _webView = findViewById(R.id.web_view);
            _webView.settings.javaScriptEnabled = true;
            CookieManager.getInstance().setAcceptCookie(true);
        }

        fun onShown(parameter: Any) {


            val config = parameter as? SourcePluginConfig;

            val authConfig = if(config != null)
                config.authentication ?: throw IllegalStateException("Plugin has no authentication support");
            else if(parameter is SourcePluginAuthConfig)
                parameter
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
                fragment.lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        fragment.close(true);
                    }catch (ex: Throwable) {
                        Logger.e(TAG, "Failed to close login", ex);
                    }
                }
            };
            var isFirstLoad = true;
            val loginWarnings = authConfig.loginWarnings?.toMutableList() ?: mutableListOf<SourcePluginAuthConfig.Warning>();
            val uiMods = authConfig.uiMods?.toMutableList() ?: mutableListOf<SourcePluginAuthConfig.UIMod>();
            var currentScale = 100;
            var currentDesktop = false;
            webViewClient.onPageLoaded.subscribe { view, url ->
                _textUrl.setText(url ?: "");

                if(loginWarnings.size > 0 && url != null) {
                    synchronized(loginWarnings) {
                        val warning = loginWarnings.find { url.matches(it.getRegex()) };
                        if(warning != null) {
                            if(warning.once == true)
                                loginWarnings.remove(warning);
                            UIDialogs.showDialog(context, R.drawable.ic_warning_yellow, warning.text ?: "", warning.details ?: "", null, 0,
                                UIDialogs.Action("Understood", {
                                }, UIDialogs.ActionStyle.PRIMARY));
                        }
                    }
                }

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

            _webView.webViewClient = webViewClient;
            _webView.loadUrl(authConfig.loginUrl);
        }

        companion object {
            private val TAG = "LoginFragment";
            private val REGEX_LOGIN_BUTTON = Regex("[a-zA-Z\\-\\.#:_ ]*");
        }
    }
}