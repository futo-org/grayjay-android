package com.futo.platformplayer.api.media.platforms.js

import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.AnnouncementType
import com.futo.platformplayer.states.StateAnnouncement
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateHistory
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.views.fields.DropdownFieldOptions
import com.futo.platformplayer.views.fields.FieldForm
import com.futo.platformplayer.views.fields.FormField
import com.futo.platformplayer.views.fields.FormFieldButton
import com.futo.platformplayer.views.fields.FormFieldWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
class SourcePluginDescriptor {
    val config: SourcePluginConfig;
    var settings: HashMap<String,String?> = hashMapOf();

    var appSettings: AppPluginSettings = AppPluginSettings();

    var authEncrypted: String? = null
        private set;
    var captchaEncrypted: String? = null
        private set;

    val flags: List<String>;

    @kotlinx.serialization.Transient
    val onAuthChanged = Event0();
    @kotlinx.serialization.Transient
    val onCaptchaChanged = Event0();

    constructor(config :SourcePluginConfig, authEncrypted: String? = null, captchaEncrypted: String? = null, settings: HashMap<String, String?>? = null) {
        this.config = config;
        this.authEncrypted = authEncrypted;
        this.captchaEncrypted = captchaEncrypted;
        this.flags = listOf();
        this.settings = settings ?: hashMapOf();
    }
    constructor(config :SourcePluginConfig, authEncrypted: String? = null, captchaEncrypted: String? = null, flags: List<String>,  settings: HashMap<String, String?>? = null) {
        this.config = config;
        this.authEncrypted = authEncrypted;
        this.captchaEncrypted = captchaEncrypted;
        this.flags = flags;
        this.settings = settings ?: hashMapOf();
    }

    fun getSettingsWithDefaults(): HashMap<String, String?> {
        val map = HashMap(settings);
        for(field in config.settings) {
            if(!map.containsKey(field.variableOrName) || map[field.variableOrName] == null)
                map.put(field.variableOrName, field.default);
        }
        return map;
    }

    fun updateCaptcha(captcha: SourceCaptchaData?) {
        captchaEncrypted = captcha?.toEncrypted();
        onCaptchaChanged.emit();
    }
    fun getCaptchaData(): SourceCaptchaData? {
        try {
            return SourceCaptchaData.fromEncrypted(captchaEncrypted);
        }
        catch(ex: Throwable) {
            Logger.e("SourcePluginDescriptor", "Captcha decode failed, disabling auth.", ex);
            StateAnnouncement.instance.registerAnnouncement("CAP_BROKEN_" + config.id,
                "Captcha corrupted for plugin [${config.name}]",
                "Something went wrong in the stored captcha, you'll have to login again", AnnouncementType.SESSION);
            return null;
        }
    }

    fun updateAuth(str: SourceAuth?) {
        authEncrypted = str?.toEncrypted();
        onAuthChanged.emit();
    }
    fun getAuth(): SourceAuth? {
        try {
            return SourceAuth.fromEncrypted(authEncrypted);
        }
        catch(ex: Throwable) {
            Logger.e("SourcePluginDescriptor", "Authentication decode failed, disabling auth.", ex);
            StateAnnouncement.instance.registerAnnouncement("AUTH_BROKEN_" + config.id,
                "Authentication corrupted for plugin [${config.name}]",
                "Something went wrong in the stored authentication, you'll have to login again", AnnouncementType.SESSION);
            return null;
        }
    }

    @Serializable
    class AppPluginSettings {

        @FormField(R.string.check_for_updates_setting, FieldForm.TOGGLE, R.string.check_for_updates_setting_description, -1)
        var checkForUpdates: Boolean = true;
        @FormField(R.string.automatic_update_setting, FieldForm.TOGGLE, R.string.automatic_update_setting_description, 0)
        var automaticUpdate: Boolean = false;

        @FormField(R.string.visibility, "group", R.string.enable_where_this_plugins_content_are_visible, 2)
        var tabEnabled = TabEnabled();
        @Serializable
        class TabEnabled {
            @FormField(R.string.home, FieldForm.TOGGLE, R.string.show_content_in_home_tab, 1)
            var enableHome: Boolean? = null;

            @FormField(R.string.search, FieldForm.TOGGLE, R.string.show_content_in_search_results, 2)
            var enableSearch: Boolean? = null;

            @FormField(R.string.shorts, FieldForm.TOGGLE, R.string.show_content_in_shorts_tab, 3)
            var enableShorts: Boolean? = null;
        }

        @FormField(R.string.sync, "group", R.string.sync_desc, 3)
        var sync = Sync();
        @Serializable
        class Sync {
            @FormField(R.string.sync_history, FieldForm.TOGGLE, R.string.sync_history_desc, 1)
            var enableHistorySync: Boolean? = null;

            @FormField(R.string.sync_history, FieldForm.BUTTON, R.string.sync_history_desc, 2)
            @FormFieldButton()
            fun syncHistoryNow() {
                StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                    val clients = StatePlatform.instance.getEnabledClients();
                    for (client in clients) {
                        if (client is JSClient) {//) && client.descriptor.appSettings.sync.enableHistorySync  == true) {
                            StateHistory.instance.syncRemoteHistory(client);
                        }
                    }
                };
            }
        }

        @FormField(R.string.ratelimit, "group", R.string.ratelimit_description, 4)
        var rateLimit = RateLimit();
        @Serializable
        class RateLimit {
            @FormField(R.string.subscriptions, FieldForm.DROPDOWN, R.string.ratelimit_sub_setting_description, 1)
            @DropdownFieldOptions("Plugin defined", "25", "50", "75", "100", "125", "150", "200")
            var rateLimitSubs: Int = 0;

            fun getSubRateLimit(): Int {
                return when(rateLimitSubs) {
                    0 -> -1
                    1 -> 25
                    2 -> 50
                    3 -> 75
                    4 -> 100
                    5 -> 125
                    6 -> 150
                    7 -> 200
                    else -> -1
                }
            }

        }



        @FormField(R.string.allow_developer_submit, FieldForm.TOGGLE, R.string.allow_developer_submit_description, 1, "devSubmit")
        var allowDeveloperSubmit: Boolean = false;


        fun loadDefaults(config: SourcePluginConfig) {
            if(tabEnabled.enableHome == null)
                tabEnabled.enableHome = config.enableInHome
            if(tabEnabled.enableSearch == null)
                tabEnabled.enableSearch = config.enableInSearch
            if(tabEnabled.enableShorts == null)
                tabEnabled.enableShorts = config.enableInShorts
        }
    }

    companion object {
        const val FLAG_EMBEDDED = "EMBEDDED";
    }
}
