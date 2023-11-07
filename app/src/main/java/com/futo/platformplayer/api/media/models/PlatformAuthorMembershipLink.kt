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
class PlatformAuthorMembershipLink: PlatformAuthorLink {
    val membershipUrl: String?;

    constructor(id: PlatformID, name: String, url: String, thumbnail: String? = null, subscribers: Long? = null, membershipUrl: String? = null): super(id, name, url, thumbnail, subscribers)
    {
        this.membershipUrl = membershipUrl;
    }

    companion object {
        fun fromV8(config: SourcePluginConfig, value: V8ValueObject): PlatformAuthorMembershipLink {
            val context = "AuthorMembershipLink"
            return PlatformAuthorMembershipLink(PlatformID.fromV8(config, value.getOrThrow(config, "id", context, false)),
                value.getOrThrow(config ,"name", context),
                value.getOrThrow(config, "url", context),
                value.getOrDefault<String>(config, "thumbnail", context, null),
                if(value.has("subscribers")) value.getOrThrow(config,"subscribers", context) else null,
                if(value.has("membershipUrl")) value.getOrThrow(config, "membershipUrl", context) else null
            );
        }
    }
}