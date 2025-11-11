package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.IPluginSourced
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.article.IPlatformArticle
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.playback.IPlaybackTracker
import com.futo.platformplayer.api.media.models.post.IPlatformPost
import com.futo.platformplayer.api.media.models.post.TextType
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.api.media.platforms.js.DevJSClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.getOrThrowNullableList
import com.futo.platformplayer.states.StateDeveloper

open class JSArticle(
    config: SourcePluginConfig,
    obj: V8ValueObject
) : JSContent(config, obj), IPlatformArticle, IPluginSourced {

    final override val contentType: ContentType = ContentType.ARTICLE

    override val summary: String =
        obj.getOrDefault<String>(config, "summary", "PlatformArticle", "") ?: ""

    override val thumbnails: Thumbnails? =
        if (obj.has("thumbnails"))
            Thumbnails.fromV8(
                config,
                obj.getOrThrow<V8ValueObject>(config, "thumbnails", "PlatformArticle")
            )
        else
            null
}
