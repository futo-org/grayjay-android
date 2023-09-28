package com.futo.platformplayer.api.media.platforms.js.models

import androidx.core.text.HtmlCompat
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.IPluginSourced
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.decodeUnicode
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.logging.Logger
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

open class JSContent : IPlatformContent, IPluginSourced {
    protected val _pluginConfig: SourcePluginConfig;
    protected val _content : V8ValueObject;

    protected val _hasGetDetails: Boolean;

    override val contentType: ContentType get() = ContentType.UNKNOWN;

    override val id: PlatformID;
    override val name: String;
    override val author: PlatformAuthorLink;
    override val datetime: OffsetDateTime?;

    override val url: String;
    override val shareUrl: String;

    override val sourceConfig: SourcePluginConfig get() = _pluginConfig;

    constructor(config: SourcePluginConfig, obj: V8ValueObject) {
        _pluginConfig = config;
        _content = obj;

        val contextName = "PlatformContent";

        id = PlatformID.fromV8(_pluginConfig, _content.getOrThrow(config, "id", contextName));
        name = HtmlCompat.fromHtml(_content.getOrThrow<String>(config, "name", contextName).decodeUnicode(), HtmlCompat.FROM_HTML_MODE_LEGACY).toString();
        Logger.i("JSContent", "name=$name");
        author = PlatformAuthorLink.fromV8(_pluginConfig, _content.getOrThrow(config, "author", contextName));

        val datetimeInt = _content.getOrThrow<Int>(config, "datetime", contextName).toLong();
        if(datetimeInt == 0.toLong())
            datetime = null;
        else
            datetime = OffsetDateTime.of(LocalDateTime.ofEpochSecond(datetimeInt, 0, ZoneOffset.UTC), ZoneOffset.UTC);
        url = _content.getOrThrow(config, "url", contextName);
        shareUrl = _content.getOrDefault<String>(config, "shareUrl", contextName, null) ?: url;

        _hasGetDetails = _content.has("getDetails");
    }
}