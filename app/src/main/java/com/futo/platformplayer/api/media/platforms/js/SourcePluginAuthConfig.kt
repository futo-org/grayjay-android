package com.futo.platformplayer.api.media.platforms.js

@kotlinx.serialization.Serializable
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
    val loginWarning: String? = null
) { }