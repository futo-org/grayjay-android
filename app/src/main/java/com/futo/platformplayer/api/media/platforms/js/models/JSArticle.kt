package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.IPluginSourced
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.post.TextType
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.getOrThrowNullableList
import kotlin.streams.toList

open class JSArticle : JSContent, IPluginSourced {
    final override val contentType: ContentType get() = ContentType.POST;

    val summary: String;
    val thumbnails: Thumbnails?;
    val segments: List<IJSArticleSegment>;

    constructor(config: SourcePluginConfig, obj: V8ValueObject): super(config, obj) {
        val contextName = "PlatformPost";

        summary = _content.getOrThrow(config, "summary", contextName);
        if(_content.has("thumbnails"))
            thumbnails = Thumbnails.fromV8(config, _content.getOrThrow(config, "thumbnails", contextName));
        else
            thumbnails = null;


        segments = (obj.getOrThrowNullableList<V8ValueObject>(config, "segments", contextName)
            ?.map { fromV8Segment(config, it) }
            ?.filterNotNull() ?: listOf());
    }


    companion object {
        fun fromV8Segment(config: SourcePluginConfig, obj: V8ValueObject): IJSArticleSegment? {
            if(!obj.has("type"))
                throw IllegalArgumentException("Object missing type field");
            return when(obj.getOrThrow<SegmentType>(config, "type", "JSArticle.Segment")) {
                SegmentType.TEXT -> JSTextSegment(config, obj);
                SegmentType.IMAGES -> JSImagesSegment(config, obj);
                else -> null;
            }
        }
    }
}

enum class SegmentType(i: Int) {
    UNKNOWN(0),
    TEXT(1),
    IMAGES(2)
}

interface IJSArticleSegment {
    val type: SegmentType;
}
class JSTextSegment: IJSArticleSegment {
    override val type = SegmentType.TEXT;
    val textType: TextType;
    val content: String;

    constructor(config: SourcePluginConfig, obj: V8ValueObject) {
        val contextName = "JSTextSegment";
        textType = TextType.fromInt((obj.getOrDefault<Int>(config, "textType", contextName, null) ?: 0));
        content = obj.getOrDefault(config, "content", contextName, "") ?: "";
    }
}
class JSImagesSegment: IJSArticleSegment {
    override val type = SegmentType.IMAGES;
    val images: List<String>;

    constructor(config: SourcePluginConfig, obj: V8ValueObject) {
        val contextName = "JSTextSegment";
        images = obj.getOrThrowNullableList<String>(config, "images", contextName) ?: listOf();
    }
}