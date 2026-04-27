package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.getOrThrowNullable
import com.futo.platformplayer.invokeV8
import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

@kotlinx.serialization.Serializable
class JSComment : IPlatformComment {
    @kotlinx.serialization.Transient
    private var _hasGetReplies: Boolean = false;

    @kotlinx.serialization.Transient
    private var _config: SourcePluginConfig? = null;
    @kotlinx.serialization.Transient
    private var _comment: V8ValueObject? = null;
    @kotlinx.serialization.Transient
    private var _plugin: V8Plugin? = null;

    override val contextUrl: String;
    override val author: PlatformAuthorLink;
    override val message: String;
    override val rating: IRating;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeNullableSerializer::class)
    override val date: OffsetDateTime?;
    override val replyCount: Int?;

    val context: Map<String, String>;


    constructor(config: SourcePluginConfig, plugin: V8Plugin, obj: V8ValueObject) {
        _config = config;
        _comment = obj;
        _plugin = plugin;

        var parsedContextUrl: String? = null;
        var parsedAuthor: PlatformAuthorLink? = null;
        var parsedMessage: String? = null;
        var parsedRating: IRating? = null;
        var parsedDate: OffsetDateTime? = null;
        var parsedReplyCount: Int? = null;
        var parsedContext: Map<String, String>? = null;
        var parsedHasGetReplies = false;

        plugin.busy {
            val contextName = "Comment";
            parsedContextUrl = _comment!!.getOrThrow(config, "contextUrl", contextName);
            parsedAuthor = PlatformAuthorLink.fromV8(_config!!, _comment!!.getOrThrow(config, "author", contextName));
            parsedMessage = _comment!!.getOrThrow(config, "message", contextName);
            parsedRating = IRating.fromV8(config, _comment!!.getOrThrow(config, "rating", contextName));
            parsedDate = _comment!!.getOrThrowNullable<Int>(config, "date", contextName)?.let { OffsetDateTime.of(LocalDateTime.ofEpochSecond(it.toLong(), 0, ZoneOffset.UTC), ZoneOffset.UTC) };
            parsedReplyCount = _comment!!.getOrThrowNullable(config, "replyCount", contextName);
            parsedContext = _comment!!.getOrDefault(config, "context", contextName, hashMapOf()) ?: hashMapOf();
            parsedHasGetReplies = _comment!!.has("getReplies");
        }

        contextUrl = parsedContextUrl ?: "";
        author = parsedAuthor ?: PlatformAuthorLink.UNKNOWN;
        message = parsedMessage ?: "";
        rating = parsedRating ?: throw IllegalStateException("Missing comment rating");
        date = parsedDate;
        replyCount = parsedReplyCount;
        context = parsedContext ?: hashMapOf();
        _hasGetReplies = parsedHasGetReplies;
    }

    override fun getReplies(client: IPlatformClient): IPager<IPlatformComment>? {
        if(!_hasGetReplies)
            return null;

        val plugin = if(client is JSClient) client else throw NotImplementedError("Only implemented for JSClient");
        return plugin.busy {
            val obj = _comment!!.invokeV8<V8ValueObject>("getReplies", arrayOf<Any>());
            return@busy JSCommentPager(_config!!, plugin, obj);
        }
    }
}
