package com.futo.platformplayer.api.media.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow

/**
 * A link to a channel, often with its own name and thumbnail
 */
@kotlinx.serialization.Serializable
class PlatformAuthorLink {
    val id: PlatformID;
    val name: String;
    val url: String;
    val thumbnail: String?;
    var subscribers: Long? = null; //Optional

    constructor(id: PlatformID, name: String, url: String, thumbnail: String? = null, subscribers: Long? = null)
    {
        this.id = id;
        this.name = name;
        this.url = url;
        this.thumbnail = thumbnail;
        this.subscribers = subscribers;
    }

    companion object {
        fun fromV8(config: SourcePluginConfig, value: V8ValueObject): PlatformAuthorLink {
            val context = "AuthorLink"
            return PlatformAuthorLink(PlatformID.fromV8(config, value.getOrThrow(config, "id", context, false)),
                value.getOrThrow(config ,"name", context),
                value.getOrThrow(config, "url", context),
                value.getOrDefault<String>(config, "thumbnail", context, null),
                if(value.has("subscribers")) value.getOrThrow(config,"subscribers", context) else null
            );
        }
    }
}