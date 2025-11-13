package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.IPluginSourced
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.article.IPlatformArticle
import com.futo.platformplayer.api.media.models.article.IPlatformArticleDetails
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.playback.IPlaybackTracker
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
import com.futo.platformplayer.invokeV8
import com.futo.platformplayer.states.StateDeveloper

open class JSArticleDetails(
    private val client: JSClient,
    obj: V8ValueObject
) : JSContent(client.config, obj), IPlatformArticleDetails, IPluginSourced, IPlatformContentDetails {

    final override val contentType: ContentType = ContentType.ARTICLE

    private val _hasGetComments: Boolean = _content.has("getComments")
    private val _hasGetContentRecommendations: Boolean = _content.has("getContentRecommendations")

    override val rating: IRating =
        obj.getOrDefault<V8ValueObject>(client.config, "rating", "PlatformArticle", null)
            ?.let { IRating.fromV8(client.config, it, "PlatformArticle") }
            ?: RatingLikes(0)

    override val summary: String =
        _content.getOrThrow(client.config, "summary", "PlatformArticle")

    override val thumbnails: Thumbnails? =
        if (_content.has("thumbnails"))
            Thumbnails.fromV8(
                client.config,
                _content.getOrThrow(client.config, "thumbnails", "PlatformArticle")
            )
        else
            null

    override val segments: List<IJSArticleSegment> =
        obj.getOrThrowNullableList<V8ValueObject>(client.config, "segments", "PlatformArticle")
            ?.mapNotNull { fromV8Segment(client, it) }
            ?: emptyList()

    override fun getComments(client: IPlatformClient): IPager<IPlatformComment>? {
        if(!_hasGetComments || _content.isClosed)
            return null;

        if(client is DevJSClient)
            return StateDeveloper.instance.handleDevCall(client.devID, "videoDetail.getComments()") {
                return@handleDevCall getCommentsJS(client);
            }
        else if(client is JSClient)
            return getCommentsJS(client);

        return null;
    }

    override fun getPlaybackTracker(): IPlaybackTracker? = null;

    override fun getContentRecommendations(client: IPlatformClient): IPager<IPlatformContent>? {
        if(!_hasGetContentRecommendations || _content.isClosed)
            return  null;

        if(client is DevJSClient)
            return StateDeveloper.instance.handleDevCall(client.devID, "postDetail.getContentRecommendations()") {
                return@handleDevCall getContentRecommendationsJS(client);
            }
        else if(client is JSClient)
            return getContentRecommendationsJS(client);

        return null;
    }

    private fun getContentRecommendationsJS(client: JSClient): JSContentPager {
        val contentPager = _content.invokeV8<V8ValueObject>("getContentRecommendations", arrayOf<Any>());
        return JSContentPager(_pluginConfig, client, contentPager);
    }

    private fun getCommentsJS(client: JSClient): JSCommentPager {
        val commentPager = _content.invokeV8<V8ValueObject>("getComments", arrayOf<Any>());
        return JSCommentPager(_pluginConfig, client, commentPager);
    }

    companion object {
        fun fromV8Segment(client: JSClient, obj: V8ValueObject): IJSArticleSegment? {
            if(!obj.has("type"))
                throw IllegalArgumentException("Object missing type field");
            return when(SegmentType.fromInt(obj.getOrThrow(client.config, "type", "JSArticle.Segment"))) {
                SegmentType.TEXT -> JSTextSegment(client, obj);
                SegmentType.IMAGES -> JSImagesSegment(client, obj);
                SegmentType.HEADER -> JSHeaderSegment(client, obj);
                SegmentType.NESTED -> JSNestedSegment(client, obj);
                else -> null;
            }
        }
    }
}

enum class SegmentType(val value: Int) {
    UNKNOWN(0),
    TEXT(1),
    IMAGES(2),
    HEADER(3),

    NESTED(9);


    companion object {
        fun fromInt(value: Int): SegmentType
        {
            val result = SegmentType.entries.firstOrNull { it.value == value };
            if(result == null)
                throw IllegalArgumentException("Unknown Texttype: $value");
            return result;
        }
    }
}

interface IJSArticleSegment {
    val type: SegmentType;
}
class JSTextSegment: IJSArticleSegment {
    override val type = SegmentType.TEXT;
    val textType: TextType;
    val content: String;

    constructor(client: JSClient, obj: V8ValueObject) {
        val contextName = "JSTextSegment";
        textType = TextType.fromInt((obj.getOrDefault<Int>(client.config, "textType", contextName, null) ?: 0));
        content = obj.getOrDefault(client.config, "content", contextName, "") ?: "";
    }
}
class JSImagesSegment: IJSArticleSegment {
    override val type = SegmentType.IMAGES;
    val images: List<String>;
    val caption: String;

    constructor(client: JSClient, obj: V8ValueObject) {
        val contextName = "JSTextSegment";
        images = obj.getOrThrowNullableList<String>(client.config, "images", contextName) ?: listOf();
        caption = obj.getOrDefault(client.config, "caption", contextName, "") ?: "";
    }
}
class JSHeaderSegment: IJSArticleSegment {
    override val type = SegmentType.HEADER;
    val content: String;
    val level: Int;

    constructor(client: JSClient, obj: V8ValueObject) {
        val contextName = "JSHeaderSegment";
        content = obj.getOrDefault(client.config, "content", contextName, "") ?: "";
        level = obj.getOrDefault(client.config, "level", contextName, 1) ?: 1;
    }
}
class JSNestedSegment: IJSArticleSegment {
    override val type = SegmentType.NESTED;
    val nested: IPlatformContent;

    constructor(client: JSClient, obj: V8ValueObject) {
        val contextName = "JSNestedSegment";
        val nestedObj = obj.getOrThrow<V8ValueObject>(client.config, "nested", contextName, false);
        nested = IJSContent.fromV8(client, nestedObj);
    }
}