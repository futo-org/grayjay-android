package com.futo.platformplayer.api.media

import kotlinx.serialization.json.Json

class Serializer {
    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true };
    }
}