package com.futo.platformplayer.api.media.platforms.js

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.Dictionary

@Serializable
class SourcePluginAuthConfig(
    val loginUrl: String,
    val completionUrl: String? = null,
    val allowedDomains: List<String>? = null,
    val headersToFind: List<String>? = null,
    val cookiesToFind: List<String>? = null,
    val cookiesExclOthers: Boolean = true,
    val userAgent: String? = null,
    val loginButton: String? = null,
    val domainHeadersToFind: Map<String, List<String>>? = null,
    val loginWarning: String? = null,
    val loginWarnings: List<Warning>? = null,
    val uiMods: List<UIMod>? = null
) {

    @Serializable
    class Warning(
        val url: String,
        val text: String?,
        val details: String? = null,
        val once: Boolean? = true
    ) {
        @Transient
        private var _regex: Regex? = null;

        fun getRegex(): Regex {
            return _regex ?: url.let {
                val reg = Regex(it);
                _regex = reg;
                return reg;
            }
        }
    }
    @Serializable
    class UIMod(
        val url: String,
        val scale: Float?,
        val desktop: Boolean?
    ) {
        @Contextual
        private var _regex: Regex? = null;

        fun getRegex(): Regex {
            return _regex ?: url.let {
                val reg = Regex(it);
                _regex = reg;
                return reg;
            }
        }
    }
}