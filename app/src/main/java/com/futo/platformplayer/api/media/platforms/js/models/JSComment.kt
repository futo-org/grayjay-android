package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.getOrThrowNullable
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

        val contextName = "Comment";
        contextUrl = _comment!!.getOrThrow(config, "contextUrl", contextName);
        author = PlatformAuthorLink.fromV8(_config!!, _comment!!.getOrThrow(config, "author", contextName));
        message = _comment!!.getOrThrow(config, "message", contextName);
        rating = IRating.fromV8(config, _comment!!.getOrThrow(config, "rating", contextName));
        date = _comment!!.getOrThrowNullable<Int>(config, "date", contextName)?.let { OffsetDateTime.of(LocalDateTime.ofEpochSecond(it.toLong(), 0, ZoneOffset.UTC), ZoneOffset.UTC) }
        replyCount = _comment!!.getOrThrowNullable(config, "replyCount", contextName);
        context = _comment!!.getOrDefault(config, "context", contextName, hashMapOf()) ?: hashMapOf();
        _hasGetReplies = _comment!!.has("getReplies");
    }

    override fun getReplies(client: IPlatformClient): IPager<IPlatformComment>? {
        if(!_hasGetReplies)
            return null;

        val obj = _comment!!.invoke<V8ValueObject>("getReplies", arrayOf<Any>());
        return JSCommentPager(_config!!, _plugin!!, obj);
    }
}