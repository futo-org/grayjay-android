package com.futo.platformplayer.api.media.platforms.js

import kotlinx.serialization.Serializable

@Serializable
class SourcePluginCaptchaConfig(
    val captchaUrl: String? = null,
    val completionUrl: String? = null,
    val cookiesToFind: List<String>? = null,
    val userAgent: String? = null,
    val cookiesExclOthers: Boolean = true
)