package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.getOrThrow

interface IJSContentDetails: IPlatformContent  {

    companion object {
        fun fromV8(config: SourcePluginConfig, obj: V8ValueObject): IPlatformContentDetails {
            val type: Int = obj.getOrThrow(config, "contentType", "ContentDetails");
            return when(ContentType.fromInt(type)) {
                ContentType.MEDIA -> JSVideoDetails(config, obj);
                ContentType.POST -> JSPostDetails(config, obj);
                else -> throw NotImplementedError("Unknown content type ${type}");
            }
        }
    }
}