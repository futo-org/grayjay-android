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

open class JSContent(
    protected val _pluginConfig: SourcePluginConfig,
    protected val _content: V8ValueObject
) : IPlatformContent, IPluginSourced {

    override val contentType: ContentType = ContentType.UNKNOWN

    protected val _hasGetDetails: Boolean = _content.has("getDetails")

    override val id: PlatformID =
        PlatformID.fromV8(_pluginConfig, _content.getOrThrow(_pluginConfig, "id", CTX))

    override val name: String =
        HtmlCompat.fromHtml(
            _content.getOrThrow<String>(_pluginConfig, "name", CTX).decodeUnicode(),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()

    override val author: PlatformAuthorLink =
        _content.getOrDefault<V8ValueObject>(_pluginConfig, "author", CTX, null)
            ?.let { PlatformAuthorLink.fromV8(_pluginConfig, it) }
            ?: PlatformAuthorLink.UNKNOWN

    private val _epoch: Long? =
        _content.getOrDefault<Long>(_pluginConfig, "datetime", CTX, null)?.toLong()

    override val datetime: OffsetDateTime? =
        _epoch?.takeIf { it != 0L }?.let {
            OffsetDateTime.of(LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC), ZoneOffset.UTC)
        }

    override val url: String =
        _content.getOrThrow<String>(_pluginConfig, "url", CTX)

    override val shareUrl: String =
        _content.getOrDefault<String>(_pluginConfig, "shareUrl", CTX, null) ?: url

    override val sourceConfig: SourcePluginConfig
        get() = _pluginConfig

    fun getUnderlyingObject(): V8ValueObject? = _content

    companion object {
        private const val CTX = "PlatformContent"
    }
}
