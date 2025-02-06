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
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.AddSourceActivity
import com.futo.platformplayer.activities.LoginActivity
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateDeveloper
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.views.buttons.BigButton
import com.futo.platformplayer.views.buttons.BigButtonGroup
import com.futo.platformplayer.views.fields.FieldForm
import com.futo.platformplayer.views.sources.SourceHeaderView
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
        _view?.onShown(parameter);
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
        private val _sourceAdvancedButtons: LinearLayout;
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
            _sourceAdvancedButtons = findViewById(R.id.advanced_source_buttons);
            _settingsAppForm = findViewById(R.id.source_app_setings);
            _settingsForm = findViewById(R.id.source_settings);
            _layoutLoader = findViewById(R.id.layout_loader);
            _imageSpinner = findViewById(R.id.image_spinner);

            updateSourceViews();
        }

        fun onShown(parameter: Any?) {
            if (parameter is SourcePluginConfig) {
                loadConfig(parameter);
                updateSourceViews();
            }
            else if(parameter is UpdatePluginAction) {
                loadConfig(parameter.config);
                updateSourceViews();
                checkForUpdatesSource();
            }

            setLoading(false);
        }

        fun onHide() {
            val id = _config?.id ?: return;

            var shouldReload = false;
            if(_settingsAppChanged) {
                _settingsAppForm.setObjectValues();
                StatePlugins.instance.savePlugin(id);
                shouldReload = true;
            }
            if(_settingsChanged && _settings != null) {
                _settingsChanged = false;
                StatePlugins.instance.setPluginSettings(id, _settings!!);
                shouldReload = true;
                UIDialogs.toast(context.getString(R.string.plugin_settings_saved), false);
            }
            if(shouldReload)
                reloadSource(id);
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
                            if(source.config.developerSubmitUrl.isNullOrEmpty()) {
                                val field = _settingsAppForm.findField("devSubmit");
                                field?.setValue(false);
                                if(field is View)
                                    field.isVisible = false;
                            }
                            _settingsAppForm.onChanged.clear();
                            _settingsAppForm.onChanged.subscribe { field, value ->
                                _settingsAppChanged = true;
                                if(field.descriptor?.id == "devSubmit") {
                                    if(value is Boolean && value) {
                                        UIDialogs.showDialog(context, R.drawable.ic_warning_yellow,
                                            "Are you sure you trust the developer?",
                                            "Developers may gain access to sensitive data. Only enable this when you are trying to help the developer fix a bug.\nThe following domain is used:",
                                                source.config.developerSubmitUrl ?: "", 0,
                                            UIDialogs.Action("Cancel", { field.setValue(false); }, UIDialogs.ActionStyle.NONE),
                                            UIDialogs.Action("Enable", {  }, UIDialogs.ActionStyle.DANGEROUS));
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            Logger.e(TAG, "Failed to load app settings form from plugin settings", e)
                        }

                        //Plugin settings
                        try {
                            _settings = settingValues;
                            _settingsForm.fromPluginSettings(
                                settings, settingValues, context.getString(R.string.plugin_settings),
                                context.getString(R.string.these_settings_are_defined_by_the_plugin)
                            );
                            _settingsForm.onChanged.clear();
                            _settingsForm.onChanged.subscribe { _, _ ->
                                _settingsChanged = true;
                            }
                        } catch (e: Throwable) {
                            Logger.e(TAG, "Failed to load settings form from plugin settings", e)
                        }
                    }
                }
                catch(ex: Throwable) {
                    Logger.e(TAG, "Failed to load source", ex);
                    UIDialogs.toast(context.getString(R.string.failed_to_load_source));
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
                BigButtonGroup(c, context.getString(R.string.update),
                    BigButton(c, context.getString(R.string.check_for_updates), context.getString(R.string.checks_for_new_versions_of_the_source), R.drawable.ic_update) {
                        checkForUpdatesSource();
                    },
                    if(config.changelog?.any() == true)
                        BigButton(c, context.getString(R.string.changelog), context.getString(R.string.changelog_plugin_description), R.drawable.ic_list) {
                            UIDialogs.showChangelogDialog(context, config.version, config.changelog!!.filterKeys { it.toIntOrNull() != null }
                                .mapKeys { it.key.toInt() }
                                .mapValues { config.getChangelogString(it.key.toString()) ?: "" });
                        }.apply {
                            this.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                                setMargins(0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics).toInt(), 0, 0);
                            };
                        }
                    else
                        null
                )
            );

            if (source.isLoggedIn) {
                groups.add(
                    BigButtonGroup(c, context.getString(R.string.authentication),
                        BigButton(c, context.getString(R.string.logout), context.getString(R.string.sign_out_of_the_platform), R.drawable.ic_logout) {
                            logoutSource();
                        },
                        BigButton(c, "Logout without Clear", "Logout but keep the browser cookies.\nThis allows for quick re-logging.", R.drawable.ic_logout) {
                            logoutSource(false);
                        }.apply {
                            this.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                                setMargins(0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics).toInt(), 0, 0);
                            };
                        }
                    )
                );

                val migrationButtons = mutableListOf<BigButton>();
                if (isEnabled && source.capabilities.hasGetUserSubscriptions) {
                    migrationButtons.add(
                        BigButton(c, context.getString(R.string.import_subscriptions), context.getString(R.string.import_your_subscriptions_from_this_source), R.drawable.ic_subscriptions) {
                            Logger.i(TAG, "Import subscriptions clicked.");
                            importSubscriptionsSource();
                        }
                    );
                }

                if (isEnabled && source.capabilities.hasGetUserPlaylists && source.capabilities.hasGetPlaylist) {
                    val bigButton = BigButton(c, context.getString(R.string.import_playlists), context.getString(R.string.import_your_playlists_from_this_source), R.drawable.ic_playlist) {
                        Logger.i(TAG, "Import playlists clicked.");
                        importPlaylistsSource();
                    };

                    bigButton.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics).toInt(), 0, 0);
                    };

                    migrationButtons.add(bigButton);
                }

                if (migrationButtons.size > 0) {
                    groups.add(BigButtonGroup(c, context.getString(R.string.migration), *migrationButtons.toTypedArray()));
                }
            } else {
                if(config.authentication != null) {
                    groups.add(
                        BigButtonGroup(c, context.getString(R.string.authentication),
                            BigButton(c, context.getString(R.string.login), context.getString(R.string.sign_into_the_platform_of_this_source), R.drawable.ic_login) {
                                loginSource();
                            }
                        )
                    );

                    val migrationButtons = mutableListOf<BigButton>();
                    if (isEnabled && source.capabilities.hasGetUserSubscriptions) {
                        migrationButtons.add(
                            BigButton(c, context.getString(R.string.import_subscriptions), context.getString(R.string.login_required), R.drawable.ic_subscriptions) {

                            }.apply { this.alpha = 0.5f }
                        );
                    }

                    if (isEnabled && source.capabilities.hasGetUserPlaylists && source.capabilities.hasGetPlaylist) {
                        val bigButton = BigButton(c, context.getString(R.string.import_playlists), context.getString(R.string.login_required), R.drawable.ic_playlist) {

                        }.apply { this.alpha = 0.5f };

                        bigButton.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics).toInt(), 0, 0);
                        };

                        migrationButtons.add(bigButton);
                    }

                    if (migrationButtons.size > 0) {
                        groups.add(BigButtonGroup(c, context.getString(R.string.migration), *migrationButtons.toTypedArray()));
                    }
                }
            }

            val isEmbedded = StatePlugins.instance.getEmbeddedSources(context).any { it.key == config.id };

            val clientIfExists = if(config.id != StateDeveloper.DEV_ID)
                StatePlugins.instance.getPlugin(config.id);
            else null;
            groups.add(
                BigButtonGroup(c, context.getString(R.string.management),
                    if(!isEmbedded) BigButton(c, context.getString(R.string.uninstall), context.getString(R.string.removes_the_plugin_from_the_app), R.drawable.ic_block) {
                        uninstallSource();
                    }.withBackground(R.drawable.background_big_button_red).apply {
                        this.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics).toInt(), 0, 0);
                        };
                    } else BigButton(c, context.getString(R.string.uninstall), "Cannot uninstall embedded plugins", R.drawable.ic_block, {}).apply {
                        this.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics).toInt(), 0, 0);
                        };
                        this.alpha = 0.5f
                    },
                    if(clientIfExists?.captchaEncrypted != null)
                        BigButton(c, context.getString(R.string.delete_captcha), context.getString(R.string.deletes_stored_captcha_answer_for_this_plugin), R.drawable.ic_block) {
                            clientIfExists.updateCaptcha(null);
                            updateButtons();
                            UIDialogs.toast(context, "Captcha data deleted");
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

            val advancedButtons = BigButtonGroup(c, "Advanced",
                BigButton(c, "Edit Code", "Modify the source of this plugin", R.drawable.ic_code) {

                }.apply {
                    this.alpha = 0.5f;
                },
                if(isEmbedded) BigButton(c, "Reinstall", "Modify the source of this plugin", R.drawable.ic_refresh) {
                    val embeddedConfig = StatePlugins.instance.getEmbeddedPluginConfigFromID(context, config.id);

                    UIDialogs.showDialog(context, R.drawable.ic_warning_yellow, "Are you sure you want to downgrade (${config.version}=>${embeddedConfig?.version})?",
                        "This will revert the plugin back to the originally embedded version.\nVersion change: ${config.version}=>${embeddedConfig?.version}", null,
                        0, UIDialogs.Action("Cancel", {}), UIDialogs.Action("Reinstall", {
                            StatePlugins.instance.updateEmbeddedPlugins(context, listOf(config.id), true);
                            reloadSource(config.id);
                            UIDialogs.toast(context, "Embedded plugin reinstalled, may require refresh");
                        }, UIDialogs.ActionStyle.DANGEROUS));
                }.apply {
                    this.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics).toInt(), 0, 0);
                    };
                } else null
            )

            _sourceAdvancedButtons.removeAllViews();
            _sourceAdvancedButtons.addView(advancedButtons);
        }


        private fun loginSource() {
            val config = _config ?: return;

            if(config.authentication == null)
                return;

            if(config.authentication.loginWarning != null) {
                UIDialogs.showDialog(context, R.drawable.ic_warning_yellow, "Login Warning",
                    config.authentication.loginWarning, null, 0,
                    UIDialogs.Action("Cancel", {}, UIDialogs.ActionStyle.NONE),
                    UIDialogs.Action("Login", {
                        LoginActivity.showLogin(StateApp.instance.context, config) {
                            try {
                                StatePlugins.instance.setPluginAuth(config.id, it);
                                reloadSource(config.id);
                            } catch (e: Throwable) {
                                StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                                    context?.let { c -> UIDialogs.showGeneralErrorDialog(c, "Failed to set plugin authentication (loginSource, loginWarning)", e) }
                                }
                                Logger.e(TAG, "Failed to set plugin authentication (loginSource, loginWarning)", e)
                            }
                        };
                    }, UIDialogs.ActionStyle.PRIMARY))
            }
            else
                LoginActivity.showLogin(StateApp.instance.context, config) {
                    try {
                        StatePlugins.instance.setPluginAuth(config.id, it);
                        reloadSource(config.id);
                    } catch (e: Throwable) {
                        StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                            context?.let { c -> UIDialogs.showGeneralErrorDialog(c, "Failed to set plugin authentication (loginSource)", e) }
                        }
                        Logger.e(TAG, "Failed to set plugin authentication (loginSource)", e)
                    }
                };
        }
        private fun logoutSource(clear: Boolean = true) {
            val config = _config ?: return;

            try {
                StatePlugins.instance.setPluginAuth(config.id, null);
                reloadSource(config.id);
            } catch (e: Throwable) {
                StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                    context?.let { c -> UIDialogs.showGeneralErrorDialog(c, "Failed to clear plugin authentication", e) }
                }
                Logger.e(TAG, "Failed to clear plugin authentication", e)
            }

            //TODO: Maybe add a dialog option..
            if(Settings.instance.plugins.clearCookiesOnLogout && clear) {
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
                            context?.let { UIDialogs.showGeneralErrorDialog(it, it.getString(R.string.failed_to_retrieve_playlists), e) }
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
                        Logger.i(TAG, context.getString(R.string.subscriptioncount_user_subscriptions_retrieved).replace("{subscriptionCount}", subscriptions.size.toString()));

                        withContext(Dispatchers.Main) {
                            fragment.navigate<ImportSubscriptionsFragment>(subscriptions);
                        }
                    } catch(e: Throwable) {
                        withContext(Dispatchers.Main) {
                            context?.let { UIDialogs.showGeneralErrorDialog(it, context.getString(R.string.failed_to_retrieve_subscriptions), e) }
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

            UIDialogs.showConfirmationDialog(context, context.getString(R.string.are_you_sure_you_want_to_uninstall) + " ${source.name}", {
                StatePlugins.instance.deletePlugin(source.id);

                fragment.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        StatePlatform.instance.updateAvailableClients(context);
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to update available clients.");
                    }

                    withContext(Dispatchers.Main) {
                        UIDialogs.toast(context, context.getString(R.string.uninstalled) + " ${source.name}");
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
                        withContext(Dispatchers.Main) { UIDialogs.toast(context.getString(R.string.failed_to_check_for_updates)); };
                        return@launch;
                    }

                    val configJson = response.body.string();
                    Logger.i(TAG, "Downloaded source config ($sourceUrl):\n${configJson}");

                    val config = SourcePluginConfig.fromJson(configJson);
                    if (config.version <= c.version && config.name != "Youtube") {
                        Logger.i(TAG, "Plugin is up to date.");
                        withContext(Dispatchers.Main) { UIDialogs.toast(context.getString(R.string.plugin_is_fully_up_to_date)); };
                        return@launch;
                    }

                    Logger.i(TAG, "Update is available (config.version=${config.version}, source.config.version=${c.version}).");

                    val ctx = context ?: return@launch;
                    val intent = Intent(ctx, AddSourceActivity::class.java).apply {
                        data = Uri.parse(sourceUrl)
                    };

                    fragment.startActivity(intent);
                    Logger.i(TAG, "Started add source activity.");
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to check for updates.", e);
                    withContext(Dispatchers.Main) { UIDialogs.toast(context.getString(R.string.failed_to_check_for_updates)); };
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

    class UpdatePluginAction(val config: SourcePluginConfig) {

    }
}