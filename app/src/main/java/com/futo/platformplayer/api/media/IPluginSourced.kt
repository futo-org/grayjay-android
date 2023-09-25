package com.futo.platformplayer.api.media

import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig

interface IPluginSourced {
    val sourceConfig: SourcePluginConfig;
}