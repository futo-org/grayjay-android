package com.futo.platformplayer.api.media.platforms.js

import android.net.Uri
import com.futo.platformplayer.SignatureProvider
import com.futo.platformplayer.api.media.Serializer
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.states.StatePlugins
import kotlinx.serialization.decodeFromString
import java.net.URL
import java.util.*

@kotlinx.serialization.Serializable
class SourcePluginConfig(
    override val name: String,
    val description: String = "",

    //Author
    val author: String = "",
    val authorUrl: String = "",

    //Script
    val repositoryUrl: String? = null,
    val scriptUrl: String = "",
    val version: Int = -1,

    val iconUrl: String? = null,
    var id: String = UUID.randomUUID().toString(),

    val scriptSignature: String? = null,
    val scriptPublicKey: String? = null,

    override val allowEval: Boolean = false,
    override val allowUrls: List<String> = listOf(),
    override val packages: List<String> = listOf(),

    val settings: List<Setting> = listOf(),

    val authentication: SourcePluginAuthConfig? = null,
    var sourceUrl: String? = null,
    val constants: HashMap<String, String> = hashMapOf(),

    //TODO: These should be vals...but prob for serialization reasons cannot be changed.
    var enableInSearch: Boolean = true,
    var enableInHome: Boolean = true,
    var supportedClaimTypes: List<Int> = listOf()
) : IV8PluginConfig {

    val absoluteIconUrl: String? get() = resolveAbsoluteUrl(iconUrl, sourceUrl);
    val absoluteScriptUrl: String get() = resolveAbsoluteUrl(scriptUrl, sourceUrl)!!;

    private fun resolveAbsoluteUrl(url: String?, sourceUrl: String?): String? {
        if(url == null)
            return null;
        val uri = Uri.parse(url);
        if(uri.isAbsolute)
            return url;
        else if(sourceUrl.isNullOrEmpty())
            return url;
        //    throw IllegalStateException("Attempted to get absolute script url from relative, without base url");
        else {
            val sourceUrlParsed = URL(sourceUrl);
            return URL(sourceUrlParsed, uri.path).toString();
        }
    }

    private var _allowAnywhereVal: Boolean? = null;
    private val _allowAnywhere: Boolean get() {
        if(_allowAnywhereVal == null)
            _allowAnywhereVal = allowUrls.any { it.lowercase() == "everywhere" };
        return _allowAnywhereVal!!;
    };
    private var _allowUrlsLowerVal: List<String>? = null;
    private val _allowUrlsLower: List<String> get() {
        if(_allowUrlsLowerVal == null)
            _allowUrlsLowerVal = allowUrls.map { it.lowercase() };
        return _allowUrlsLowerVal!!;
    };

    fun getWarnings(scriptToCheck: String? = null) : List<Pair<String,String>> {
        val list = mutableListOf<Pair<String,String>>();

        val currentlyInstalledPlugin = StatePlugins.instance.getPlugin(id);
        if (currentlyInstalledPlugin != null) {
            if (currentlyInstalledPlugin.config.scriptPublicKey != scriptPublicKey) {
                list.add(Pair(
                    "Different Author",
                    "This plugin was signed by a different author. Please ensure that this is correct and that the plugin was not provided by a malicious actor."));
            }
        }

        if(scriptPublicKey.isNullOrEmpty() || scriptSignature.isNullOrEmpty())
            list.add(Pair(
                "Missing Signature",
                "This plugin does not have a signature. This makes updating the plugin less safe as it makes it easier for a malicious actor besides the developer to update a malicious version."));
        else if(scriptToCheck != null && !this.validate(scriptToCheck))
            list.add(Pair(
                "Invalid Signature",
                "This plugin does not have a signature. This makes updating the plugin less safe as it makes it easier for a malicious actor besides the developer to update a malicious version."));
        if(allowEval)
            list.add(Pair(
                "Eval Access",
                "Eval allows injection of unsure code, and should be avoided when possible."));
        if(allowUrls.any { it == "everywhere" })
            list.add(Pair(
                "Unrestricted Web Access",
                "This plugin requires access to all URLs, this may include malicious URLs."));

        return list;
    }

    fun validate(text: String): Boolean {
        if(scriptPublicKey.isNullOrEmpty())
            throw IllegalStateException("No public key present");
        if(scriptSignature.isNullOrEmpty())
            throw IllegalStateException("No signature present");

        return SignatureProvider.verify(text, scriptSignature, scriptPublicKey);
    }

    fun isUrlAllowed(url: String): Boolean {
        if(_allowAnywhere)
            return true;
        val uri = Uri.parse(url);
        val host = uri.host?.lowercase() ?: "";
        return _allowUrlsLower.any { it == host };
    }

    companion object {
        fun fromJson(json: String, sourceUrl: String? = null): SourcePluginConfig {
            val obj = Serializer.json.decodeFromString<SourcePluginConfig>(json);
            if(obj.sourceUrl == null)
                obj.sourceUrl = sourceUrl;
            return obj;
        }
    }

    @kotlinx.serialization.Serializable
    data class Setting(
        val name: String,
        val description: String,
        val type: String,
        val default: String? = null,
        val variable: String? = null
    ) {
        @kotlinx.serialization.Transient
        val variableOrName: String get() = variable ?: name;
    }
}