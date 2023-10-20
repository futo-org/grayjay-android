package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Intent
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.*
import com.futo.platformplayer.activities.AddSourceActivity
import com.futo.platformplayer.activities.LoginActivity
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.views.buttons.BigButton
import com.futo.platformplayer.views.buttons.BigButtonGroup
import com.futo.platformplayer.views.sources.SourceHeaderView
import com.futo.platformplayer.views.fields.FieldForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SourceDetailFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _view: SourceDetailView? = null;

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        _view?.onShown(parameter, isBack);
    }

    override fun onHide() {
        super.onHide();
        _view?.onHide();
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = SourceDetailView(this, inflater);
        _view = view;
        return view;
    }
    override fun onDestroyMainView() {
        super.onDestroyMainView();
        _view = null;
    }

    class SourceDetailView: LinearLayout {
        private val fragment: SourceDetailFragment;

        private val _sourceHeader: SourceHeaderView;
        private val _sourceButtons: LinearLayout;
        private val _layoutLoader: FrameLayout;
        private val _imageSpinner: ImageView;

        private val _settingsAppForm: FieldForm;
        private var _settingsAppChanged = false;

        private val _settingsForm: FieldForm;
        private var _settings: HashMap<String, String?>? = null;
        private var _settingsChanged = false;

        private var _config: SourcePluginConfig? = null;

        private var _loading = false;

        constructor(fragment: SourceDetailFragment, inflater: LayoutInflater) : super(inflater.context) {
            inflater.inflate(R.layout.fragment_source_detail, this);
            this.fragment = fragment;
            _sourceHeader = findViewById(R.id.source_header);
            _sourceButtons = findViewById(R.id.source_buttons);
            _settingsAppForm = findViewById(R.id.source_app_setings);
            _settingsForm = findViewById(R.id.source_settings);
            _layoutLoader = findViewById(R.id.layout_loader);
            _imageSpinner = findViewById(R.id.image_spinner);

            updateSourceViews();
        }

        fun onShown(parameter: Any?, isBack: Boolean) {
            if (parameter is SourcePluginConfig) {
                loadConfig(parameter);
                updateSourceViews();
            }

            setLoading(false);
        }

        fun onHide() {
            val id = _config?.id ?: return;

            if(_settingsChanged && _settings != null) {
                _settingsChanged = false;
                StatePlugins.instance.setPluginSettings(id, _settings!!);
                reloadSource(id);

                UIDialogs.toast("Plugin settings saved", false);
            }
            if(_settingsAppChanged) {
                _settingsAppForm.setObjectValues();
                StatePlugins.instance.savePlugin(id);
            }
        }


        private fun loadConfig(config: SourcePluginConfig?) {
            _config = config;
            if(config != null) {
                try {
                    val settings = config.settings;
                    val source = StatePlatform.instance.getClient(config.id) as JSClient;
                    val settingValues = source.settings;

                    fragment.lifecycleScope.launch(Dispatchers.Main) {

                        //Set any defaults
                        source.descriptor.appSettings.loadDefaults(source.descriptor.config);

                        //App settings
                        try {
                            _settingsAppForm.fromObject(source.descriptor.appSettings);
                            _settingsAppForm.onChanged.clear();
                            _settingsAppForm.onChanged.subscribe { field, value ->
                                _settingsAppChanged = true;
                            }
                        } catch (e: Throwable) {
                            Logger.e(TAG, "Failed to load app settings form from plugin settings", e)
                        }

                        //Plugin settings
                        try {
                            _settings = settingValues;
                            _settingsForm.fromPluginSettings(
                                settings, settingValues, "Plugin settings",
                                "These settings are defined by the plugin"
                            );
                            _settingsForm.onChanged.clear();
                            _settingsForm.onChanged.subscribe { field, value ->
                                _settingsChanged = true;
                            }
                        } catch (e: Throwable) {
                            Logger.e(TAG, "Failed to load settings form from plugin settings", e)
                        }
                    }
                }
                catch(ex: Throwable) {
                    Logger.e(TAG, "Failed to load source", ex);
                    UIDialogs.toast("Failed to loast source");
                }
            }
        }

        private fun setLoading(isLoading: Boolean) {
            fragment.lifecycleScope.launch(Dispatchers.Main) {
                try {
                    if (isLoading) {
                        _layoutLoader.visibility = View.VISIBLE;
                        (_imageSpinner.drawable as Animatable?)?.start();
                    } else {
                        _layoutLoader.visibility = View.GONE;
                        (_imageSpinner.drawable as Animatable?)?.stop();
                    }
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to setLoading.", e)
                }
            }

            _loading = isLoading;
        }

        private fun updateSourceViews() {
            val config = _config;

            if (config != null) {
                _sourceHeader.loadConfig(config, StatePlugins.instance.getScript(config.id));
            } else {
                _sourceHeader.clear();
            }

            //updateAllToggles();
            updateButtons();
        }

        private fun updateButtons() {
            val groups = mutableListOf<BigButtonGroup>();
            _sourceButtons.removeAllViews();

            val c = context ?: return;
            val config = _config ?: return;
            val source = StatePlatform.instance.getClient(config.id) as JSClient;
            val isEnabled = StatePlatform.instance.isClientEnabled(source);

            groups.add(
                BigButtonGroup(c, "Update",
                    BigButton(c, "Check for updates", "Checks for new versions of the source", R.drawable.ic_update) {
                        checkForUpdatesSource();
                    }
                )
            );

            if (source.isLoggedIn) {
                groups.add(
                    BigButtonGroup(c, "Authentication",
                        BigButton(c, "Logout", "Sign out of the platform", R.drawable.ic_logout) {
                            logoutSource();
                        }
                    )
                );

                val migrationButtons = mutableListOf<BigButton>();
                if (isEnabled && source.capabilities.hasGetUserSubscriptions) {
                    migrationButtons.add(
                        BigButton(c, "Import Subscriptions", "Import your subscriptions from this source", R.drawable.ic_subscriptions) {
                            Logger.i(TAG, "Import subscriptions clicked.");
                            importSubscriptionsSource();
                        }
                    );
                }

                if (isEnabled && source.capabilities.hasGetUserPlaylists && source.capabilities.hasGetPlaylist) {
                    val bigButton = BigButton(c, "Import Playlists", "Import your playlists from this source", R.drawable.ic_playlist) {
                        Logger.i(TAG, "Import playlists clicked.");
                        importPlaylistsSource();
                    };

                    bigButton.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics).toInt(), 0, 0);
                    };

                    migrationButtons.add(bigButton);
                }

                if (migrationButtons.size > 0) {
                    groups.add(BigButtonGroup(c, "Migration", *migrationButtons.toTypedArray()));
                }
            } else {
                if(config.authentication != null) {
                    groups.add(
                        BigButtonGroup(c, "Authentication",
                            BigButton(c, "Login", "Sign into the platform of this source", R.drawable.ic_login) {
                                loginSource();
                            }
                        )
                    );
                }
            }

            val clientIfExists = StatePlugins.instance.getPlugin(config.id);
            groups.add(
                BigButtonGroup(c, "Management",
                    BigButton(c, "Uninstall", "Removes the plugin from the app", R.drawable.ic_block) {
                        uninstallSource();
                    }.withBackground(R.drawable.background_big_button_red).apply {
                        this.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics).toInt(), 0, 0);
                        };
                    },
                    if(clientIfExists?.captchaEncrypted != null)
                        BigButton(c, "Delete Captcha", "Deletes stored captcha answer for this plugin", R.drawable.ic_block) {
                            clientIfExists?.updateCaptcha(null);
                        }.apply {
                            this.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                setMargins(0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics).toInt(), 0, 0);
                            };
                        }.withBackground(R.drawable.background_big_button_red)
                    else null
                )
            )

            for (group in groups) {
                _sourceButtons.addView(group);
            }
        }


        private fun loginSource() {
            val config = _config ?: return;

            if(config.authentication == null)
                return;

            LoginActivity.showLogin(StateApp.instance.context, config) {
                StatePlugins.instance.setPluginAuth(config.id, it);

                reloadSource(config.id);
            };
        }
        private fun logoutSource() {
            val config = _config ?: return;

            StatePlugins.instance.setPluginAuth(config.id, null);
            reloadSource(config.id);


            //TODO: Maybe add a dialog option..
            if(Settings.instance.plugins.clearCookiesOnLogout) {
                val cookieManager: CookieManager = CookieManager.getInstance();
                cookieManager.removeAllCookies(null);
            }
        }
        private fun importPlaylistsSource() {
            if (_loading) {
                return;
            }
            setLoading(true);

            try {
                val config = _config ?: return;
                val source = StatePlatform.instance.getClient(config.id);

                fragment.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val playlists = source.getUserPlaylists();
                        withContext(Dispatchers.Main) {
                            fragment.navigate<ImportPlaylistsFragment>(playlists);
                        }
                    } catch (e: Throwable) {
                        withContext(Dispatchers.Main) {
                            context?.let { UIDialogs.showGeneralErrorDialog(it, "Failed to retrieve playlists.", e) }
                        }
                    } finally {
                        setLoading(false);
                    }
                }
            } catch (e: Throwable) {
                setLoading(false);
            }
        }
        private fun importSubscriptionsSource() {
            if (_loading) {
                return;
            }

            setLoading(true);

            try {
                val config = _config ?: return;
                val source = StatePlatform.instance.getClient(config.id);

                Logger.i(TAG, "Getting user subscriptions.");
                fragment.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val subscriptions = source.getUserSubscriptions().distinct();
                        Logger.i(TAG, "${subscriptions.size} user subscriptions retrieved.");

                        withContext(Dispatchers.Main) {
                            fragment.navigate<ImportSubscriptionsFragment>(subscriptions);
                        }
                    } catch(e: Throwable) {
                        withContext(Dispatchers.Main) {
                            context?.let { UIDialogs.showGeneralErrorDialog(it, "Failed to retrieve subscriptions.", e) }
                        }
                    } finally {
                        setLoading(false);
                    }
                }
            } catch(e: Throwable) {
                setLoading(false);
            }
        }
        private fun uninstallSource() {
            val config = _config ?: return;
            val source = StatePlatform.instance.getClient(config.id);

            UIDialogs.showConfirmationDialog(context, "Are you sure you want to uninstall ${source.name}", {
                StatePlugins.instance.deletePlugin(source.id);

                fragment.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        StatePlatform.instance.updateAvailableClients(context);
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to update available clients.");
                    }

                    withContext(Dispatchers.Main) {
                        UIDialogs.toast(context, "Uninstalled ${source.name}");
                        fragment.closeSegment();
                    }
                }
            });
        }
        private fun checkForUpdatesSource() {
            val c = _config ?: return;
            val sourceUrl = c.sourceUrl ?: return;

            Logger.i(TAG, "Check for updates tapped.");
            fragment.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val client = ManagedHttpClient();
                    val response = client.get(sourceUrl);
                    Logger.i(TAG, "Downloading source config '$sourceUrl'.");

                    if (!response.isOk || response.body == null) {
                        Logger.w(TAG, "Failed to check for updates (sourceUrl=${sourceUrl}, response.isOk=${response.isOk}, response.body=${response.body}).");
                        withContext(Dispatchers.Main) { UIDialogs.toast("Failed to check for updates"); };
                        return@launch;
                    }

                    val configJson = response.body.string();
                    Logger.i(TAG, "Downloaded source config ($sourceUrl):\n${configJson}");

                    val config = SourcePluginConfig.fromJson(configJson);
                    if (config.version <= c.version) {
                        Logger.i(TAG, "Plugin is up to date.");
                        withContext(Dispatchers.Main) { UIDialogs.toast("Plugin is fully up to date"); };
                        return@launch;
                    }

                    Logger.i(TAG, "Update is available (config.version=${config.version}, source.config.version=${c.version}).");

                    val c = context ?: return@launch;
                    val intent = Intent(c, AddSourceActivity::class.java).apply {
                        data = Uri.parse(sourceUrl)
                    };

                    fragment.startActivity(intent);
                    Logger.i(TAG, "Started add source activity.");
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to check for updates.", e);
                    withContext(Dispatchers.Main) { UIDialogs.toast("Failed to check for updates"); };
                }
            }
        }

        private fun reloadSource(id: String) {
            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                try {
                    StatePlatform.instance.reloadClient(context, id);
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to reload client.", e)
                    return@launch;
                }

                withContext(Dispatchers.Main) {
                    try {
                        updateSourceViews();
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to update source views.", e)
                    }
                }
            }
        }
    }



    companion object {
        const val TAG = "SourceDetailFragment";
        fun newInstance() = SourceDetailFragment().apply {}
    }
}