package com.futo.platformplayer.api.media.platforms.js

import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.serializers.FlexibleBooleanSerializer
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

    constructor(config :SourcePluginConfig, authEncrypted: String? = null, captchaEncrypted: String? = null) {
        this.config = config;
        this.authEncrypted = authEncrypted;
        this.captchaEncrypted = captchaEncrypted;
        this.flags = listOf();
    }
    constructor(config :SourcePluginConfig, authEncrypted: String? = null, captchaEncrypted: String? = null, flags: List<String>) {
        this.config = config;
        this.authEncrypted = authEncrypted;
        this.captchaEncrypted = captchaEncrypted;
        this.flags = flags;
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

        @FormField("Visibility", "group", "Enable where this plugin's content are visible.", 2)
        var tabEnabled = TabEnabled();
        @Serializable
        class TabEnabled {
            @FormField("Home", FieldForm.TOGGLE, "Show content in home tab", 1)
            var enableHome: Boolean? = null;


            @FormField("Search", FieldForm.TOGGLE, "Show content in search results", 2)
            var enableSearch: Boolean? = null;
        }



        fun loadDefaults(config: SourcePluginConfig) {
            if(tabEnabled.enableHome == null)
                tabEnabled.enableHome = config.enableInHome ?: true;
            if(tabEnabled.enableSearch == null)
                tabEnabled.enableSearch = config.enableInSearch ?: true;
        }
    }

    companion object {
        const val FLAG_EMBEDDED = "EMBEDDED";
    }
}
