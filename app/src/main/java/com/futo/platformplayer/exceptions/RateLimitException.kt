package com.futo.platformplayer.exceptions

class RateLimitException : Throwable {
    val pluginIds: List<String>;

    constructor(pluginIds: List<String>): super() {
        this.pluginIds = pluginIds ?: listOf();
    }
}