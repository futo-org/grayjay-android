package com.futo.platformplayer.activities

import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.*
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.views.sources.SourceHeaderView
import com.futo.platformplayer.views.sources.SourceInfoView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

class AddSourceActivity : AppCompatActivity() {
    private val TAG = "AddSourceActivity";

    private lateinit var _buttonBack: ImageButton;

    private lateinit var _sourceHeader: SourceHeaderView;

    private lateinit var _sourcePermissions: LinearLayout;
    private lateinit var _sourceWarnings: LinearLayout;

    private lateinit var _container: ScrollView;
    private lateinit var _loader: ImageView;

    private lateinit var _buttonCancel: TextView;
    private lateinit var _buttonInstall: LinearLayout;

    private var _isLoading: Boolean = false;

    private val _client = ManagedHttpClient();

    private var _config: SourcePluginConfig? = null;
    private var _script: String? = null;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);

        if(!FragmentedStorage.isInitialized)
            FragmentedStorage.initialize(filesDir);
        if(StateApp.instance.scopeOrNull == null)
            StateApp.instance.setGlobalContext(this, lifecycleScope);

        if(!StatePlatform.instance.hasClients)
            lifecycleScope.launch {
                StatePlatform.instance.updateAvailableClients(this@AddSourceActivity, false);
            }

        setContentView(R.layout.activity_add_source);
        setNavigationBarColorAndIcons();

        _buttonBack = findViewById(R.id.button_back);

        _sourceHeader = findViewById(R.id.source_header);

        _sourcePermissions = findViewById(R.id.source_permissions);
        _sourceWarnings = findViewById(R.id.source_warnings);

        _container = findViewById(R.id.configContainer);
        _loader = findViewById(R.id.loader);

        _buttonCancel = findViewById(R.id.button_cancel);
        _buttonInstall = findViewById(R.id.button_install);

        _buttonBack.setOnClickListener {
            finish();
        };
        _buttonCancel.setOnClickListener {
            finish();
        }
        _buttonInstall.setOnClickListener {
            _config?.let {
                install(_config!!, _script!!);
            };
        };

        setLoading(true);

        onNewIntent(intent);
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        var url = intent?.dataString;

        if(url == null)
            UIDialogs.showDialog(this, R.drawable.ic_error, getString(R.string.no_valid_url_provided), null, null,
                0, UIDialogs.Action(getString(R.string.ok), { finish() }, UIDialogs.ActionStyle.PRIMARY));
        else {
            if(url.startsWith("vfuto://"))
                url = "https://" + url.substring("vfuto://".length);
            loadConfigUrl(url);
        }
    }

    fun clear() {
        _sourceHeader.clear();
        _sourcePermissions.removeAllViews();
        _sourceWarnings.removeAllViews();
    }

    fun loadConfigUrl(url: String) {
        setLoading(true);

        lifecycleScope.launch(Dispatchers.IO) {
            val config: SourcePluginConfig;
            try {
                val configResp = _client.get(url);
                if(!configResp.isOk)
                    throw IllegalStateException("Failed request with ${configResp.code}");
                val configJson = configResp.body?.string();
                if(configJson.isNullOrEmpty())
                    throw IllegalStateException("No response");

                config = SourcePluginConfig.fromJson(configJson, url);
            } catch(ex: SerializationException) {
                Logger.e(TAG, "Failed decode config", ex);
                withContext(Dispatchers.Main) {
                    UIDialogs.showDialog(this@AddSourceActivity, R.drawable.ic_error,
                        getString(R.string.invalid_config_format), null, null,
                        0, UIDialogs.Action("Ok", { finish() }, UIDialogs.ActionStyle.PRIMARY));
                };
                return@launch;
            } catch(ex: Exception) {
                Logger.e(TAG, "Failed fetch config", ex);
                withContext(Dispatchers.Main) {
                    UIDialogs.showGeneralErrorDialog(this@AddSourceActivity, getString(R.string.failed_to_fetch_configuration), ex);
                };
                return@launch;
            }

            val script: String?
            try {
                val scriptResp = _client.get(config.absoluteScriptUrl);
                if (!scriptResp.isOk)
                    throw IllegalStateException("script not available [${scriptResp.code}]");
                script = scriptResp.body?.string();
                if (script.isNullOrEmpty())
                    throw IllegalStateException("script empty");
            } catch (ex: Exception) {
                Logger.e(TAG, "Failed fetch script", ex);
                withContext(Dispatchers.Main) {
                    UIDialogs.showGeneralErrorDialog(this@AddSourceActivity, getString(R.string.failed_to_fetch_script), ex);
                };
                return@launch;
            }

            withContext(Dispatchers.Main) {
                loadConfig(config, script);
            }
        };
    }

    private fun loadConfig(config: SourcePluginConfig, script: String) {
        _config = config;
        _script = script;

        _sourceHeader.loadConfig(config, script);
        _sourcePermissions.removeAllViews();
        _sourceWarnings.removeAllViews();

        if(!config.allowUrls.isEmpty())
            _sourcePermissions.addView(
                SourceInfoView(this,
                R.drawable.ic_language,
                getString(R.string.url_access),
                getString(R.string.the_plugin_will_have_access_to_the_following_domains),
                config.allowUrls, true)
            )

        if(config.allowEval)
            _sourcePermissions.addView(
                SourceInfoView(this,
                R.drawable.ic_code,
                getString(R.string.eval_access),
                getString(R.string.the_plugin_will_have_access_to_eval_capability_remote_injection),
                config.allowUrls, true)
            )

        val pastelRed = resources.getColor(R.color.pastel_red);

        for(warning in config.getWarnings(script))
            _sourceWarnings.addView(
                SourceInfoView(this,
                R.drawable.ic_security_pred,
                warning.first,
                warning.second)
                .withDescriptionColor(pastelRed));

        setLoading(false);
    }

    fun install(config: SourcePluginConfig, script: String) {
        StatePlugins.instance.installPlugin(this, lifecycleScope, config, script) {
            if(it)
                backToSources();
        }
    }

    fun backToSources() {
        this@AddSourceActivity.startActivity(MainActivity.getTabIntent(this, "Sources"));
        finish();
    }


    fun setLoading(loading: Boolean) {
        _isLoading = loading;
        if(loading) {
            _container.visibility = View.GONE;
            _loader.visibility = View.VISIBLE;
            (_loader.drawable as Animatable?)?.start()
        }
        else {
            _container.visibility = View.VISIBLE;
            _loader.visibility = View.GONE;
            (_loader.drawable as Animatable?)?.stop()
        }
    }

    override fun onResume() {
        super.onResume();
        if(_isLoading)
            (_loader.drawable as Animatable?)?.start()
    }
    override fun onPause() {
        super.onPause()
        (_loader.drawable as Animatable?)?.start()
    }
}