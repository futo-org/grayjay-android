package com.futo.platformplayer.api.media.models.modifier

interface IModifierOptions {
    val applyAuthClient: String?;
    val applyCookieClient: String?;
    val applyOtherHeaders: Boolean;
}