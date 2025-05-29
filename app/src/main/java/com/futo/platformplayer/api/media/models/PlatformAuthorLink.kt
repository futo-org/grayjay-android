package com.futo.platformplayer.api.media.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.platforms.js.models.JSContent
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow

/**
 * A link to a channel, often with its own name and thumbnail
 */
@kotlinx.serialization.Serializable
open class PlatformAuthorLink {
    val id: PlatformID;
    val name: String;
    val url: String;
    var thumbnail: String?;
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
        val UNKNOWN = PlatformAuthorLink(PlatformID.NONE, "Unknown", "", null, null);

        fun fromV8(config: SourcePluginConfig, value: V8ValueObject): PlatformAuthorLink {
            if(value.has("membershipUrl"))
                return PlatformAuthorMembershipLink.fromV8(config, value);

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

interface IPlatformChannelContent : IPlatformContent {
    val thumbnail: String?
    val subscribers: Long?
}

open class JSChannelContent : JSContent, IPlatformChannelContent {
    override val contentType: ContentType get() = ContentType.CHANNEL
    override val thumbnail: String?
    override val subscribers: Long?

    constructor(config: SourcePluginConfig, obj: V8ValueObject) : super(config, obj) {
        val contextName = "Channel";
        thumbnail = obj.getOrDefault<String>(config, "thumbnail", contextName, null)
        subscribers = if(obj.has("subscribers")) obj.getOrThrow(config,"subscribers", contextName) else null
    }
}