package com.futo.platformplayer.api.media.platforms.js

import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.views.fields.DropdownFieldOptions
import com.futo.platformplayer.views.fields.FieldForm
import com.futo.platformplayer.views.fields.FormField
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
        return SourceCaptchaData.fromEncrypted(captchaEncrypted);
    }

    fun updateAuth(str: SourceAuth?) {
        authEncrypted = str?.toEncrypted();
        onAuthChanged.emit();
    }
    fun getAuth(): SourceAuth? {
        return SourceAuth.fromEncrypted(authEncrypted);
    }

    @Serializable
    class AppPluginSettings {

        @FormField(R.string.visibility, "group", R.string.enable_where_this_plugins_content_are_visible, 2)
        var tabEnabled = TabEnabled();
        @Serializable
        class TabEnabled {
            @FormField(R.string.home, FieldForm.TOGGLE, R.string.show_content_in_home_tab, 1)
            var enableHome: Boolean? = null;


            @FormField(R.string.search, FieldForm.TOGGLE, R.string.show_content_in_search_results, 2)
            var enableSearch: Boolean? = null;
        }

        @FormField(R.string.ratelimit, "group", R.string.ratelimit_description, 3)
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


        fun loadDefaults(config: SourcePluginConfig) {
            if(tabEnabled.enableHome == null)
                tabEnabled.enableHome = config.enableInHome
            if(tabEnabled.enableSearch == null)
                tabEnabled.enableSearch = config.enableInSearch
        }
    }

    companion object {
        const val FLAG_EMBEDDED = "EMBEDDED";
    }
}
