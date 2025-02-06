package com.futo.platformplayer.api.media.platforms.js

import android.net.Uri
import com.futo.platformplayer.SignatureProvider
import com.futo.platformplayer.api.media.Serializer
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.matchesDomain
import com.futo.platformplayer.states.StatePlugins
import kotlinx.serialization.Contextual
import java.net.URL
import java.util.UUID

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
    override val packagesOptional: List<String> = listOf(),

    val settings: List<Setting> = listOf(),

    var captcha: SourcePluginCaptchaConfig? = null,
    val authentication: SourcePluginAuthConfig? = null,
    var sourceUrl: String? = null,
    val constants: HashMap<String, String> = hashMapOf(),

    //TODO: These should be vals...but prob for serialization reasons cannot be changed.
    var platformUrl: String? = null,
    var subscriptionRateLimit: Int? = null,
    var enableInSearch: Boolean = true,
    var enableInHome: Boolean = true,
    var supportedClaimTypes: List<Int> = listOf(),
    var primaryClaimFieldType: Int? = null,
    var developerSubmitUrl: String? = null,
    var allowAllHttpHeaderAccess: Boolean = false,
    var maxDownloadParallelism: Int = 0,
    var reduceFunctionsInLimitedVersion: Boolean = false,
    var changelog: HashMap<String, List<String>>? = null
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
            _allowUrlsLowerVal = allowUrls.map { it.lowercase() }
                .filter { it.length > 0 };
        return _allowUrlsLowerVal!!;
    };

    fun isLowRiskUpdate(oldScript: String, newConfig: SourcePluginConfig, newScript: String): Boolean{
        //New allow header access
        if(!allowAllHttpHeaderAccess && newConfig.allowAllHttpHeaderAccess)
            return false;

        //All urls should already be allowed
        for(url in newConfig.allowUrls) {
            if(!allowUrls.contains(url))
                return false;
        }
        //All packages should already be allowed
        for(pack in newConfig.packages) {
            if(!packages.contains(pack))
                return false;
        }
        for(pack in newConfig.packagesOptional) {
            if(!packagesOptional.contains(pack))
                return false;
        }
        //Developer Submit Url should be same or empty
        if(!newConfig.developerSubmitUrl.isNullOrEmpty() && developerSubmitUrl != newConfig.developerSubmitUrl)
            return false;

        //Should have a public key
        if(scriptPublicKey.isNullOrEmpty() || scriptSignature.isNullOrEmpty())
            return false;

        //Should be same public key
        if(scriptPublicKey != newConfig.scriptPublicKey)
            return false;

        //Old signature should be valid
        if(!validate(oldScript))
            return false;

        //New signature should be valid
        if(!newConfig.validate(newScript))
            return false;

        return true;
    }

    fun getWarnings(scriptToCheck: String? = null) : List<Pair<String,String>> {
        val list = mutableListOf<Pair<String,String>>();

        val currentlyInstalledPlugin = StatePlugins.instance.getPlugin(id);
        if (currentlyInstalledPlugin != null) {
            if (currentlyInstalledPlugin.config.scriptPublicKey != scriptPublicKey && !currentlyInstalledPlugin.config.scriptPublicKey.isNullOrEmpty()) {
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
        if(allowAllHttpHeaderAccess)
            list.add(Pair(
                "Unrestricted Http Header access",
                "Allows this plugin to access all headers (including cookies and authorization headers) for unauthenticated requests."
            ))

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
        return _allowUrlsLower.any { it == host || (it.length > 0 && it[0] == '.' && host.matchesDomain(it)) };
    }

    fun getChangelogString(version: String): String?{
        if(changelog == null || !changelog!!.containsKey(version))
            return null;
        val changelog = changelog!![version]!!;
        if(changelog.size > 1) {
            return "Changelog (${version})\n" + changelog.map { " - " + it.trim() }.joinToString("\n");
        }
        else if(changelog.size == 1) {
            return "Changelog (${version})\n" + changelog[0].trim();
        }
        return null;
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
        val variable: String? = null,
        val dependency: String? = null,
        val warningDialog: String? = null,
        val options: List<String>? = null
    ) {
        val variableOrName: String get() = variable ?: name;
    }
}