package com.futo.platformplayer.polycentric

import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.article.IPlatformArticle
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.post.IPlatformPost
import com.futo.platformplayer.api.media.models.post.IPlatformPostDetails
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import org.jsoup.Jsoup
import polycentric.v2.AttributedTo
import polycentric.v2.Link

/** Server-side limits on `Link` fields, in UTF-8 bytes (rs-common `models/link.rs`). */
private const val LINK_TITLE_MAX_BYTES = 100
private const val LINK_DESCRIPTION_MAX_BYTES = 200
private const val LINK_IMAGE_MAX_BYTES = 200
private const val LINK_URL_MAX_BYTES = 200

private const val ELLIPSIS = "…"

/**
 * Metadata of the content a comment is attributed to, used to fill out the comment's link preview.
 * [images] are candidate preview image URLs, most preferred first.
 */
data class AttributedContent(val title: String?, val description: String? = null, val images: List<String> = emptyList())

/** Collect the [AttributedContent] of this content for use in a link preview. */
fun IPlatformContent.toAttributedContent(): AttributedContent {
    val description = when (this) {
        is IPlatformVideoDetails -> description
        is IPlatformPostDetails -> description.ifBlank { content }
        is IPlatformPost -> description
        is IPlatformArticle -> summary
        else -> null
    }
    val images = when (this) {
        is IPlatformVideo -> thumbnails.urlsBestFirst()
        is IPlatformPost -> thumbnails.flatMap { it?.urlsBestFirst().orEmpty() } + images
        is IPlatformArticle -> thumbnails?.urlsBestFirst().orEmpty()
        else -> emptyList()
    }
    return AttributedContent(name, description, images)
}

private fun Thumbnails.urlsBestFirst(): List<String> = sources.reversed().mapNotNull { it.url }

/**
 * Build an [AttributedTo] link for [url]. The server requires a non-empty title of at most
 * [LINK_TITLE_MAX_BYTES] bytes. When no title is known the URL's host is used instead.
 */
internal fun attributionLink(url: String, title: String?): AttributedTo =
    AttributedTo(link = Link(title = linkTitle(url, title), url = url))

/**
 * Build a link preview for [url], filled out with as much of [content] as fits the server's limits.
 * The description is converted from HTML to plain text and shortened with an ellipsis if needed.
 * The image is the first of the content's images with a short enough http(s) URL.
 */
internal fun linkPreview(url: String, content: AttributedContent?): Link {
    val description = content?.description
        ?.let { Jsoup.parse(it).text().trim() }
        ?.takeIf { it.isNotEmpty() }
        ?.let { truncateUtf8WithEllipsis(it, LINK_DESCRIPTION_MAX_BYTES) }
    val image = content?.images?.firstOrNull { isHttpUrl(it) && utf8Size(it) <= LINK_IMAGE_MAX_BYTES }
    return Link(title = linkTitle(url, content?.title), description = description, image = image, url = url)
}

private fun isHttpUrl(s: String) = s.startsWith("https://") || s.startsWith("http://")

private fun linkTitle(url: String, title: String?): String {
    val resolved = title?.trim()?.takeIf { it.isNotEmpty() }
        ?: runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotEmpty() }
        ?: url
    return truncateUtf8(resolved, LINK_TITLE_MAX_BYTES)
}

/** Throws [IllegalArgumentException] if the server would reject [url] as a link target. */
internal fun requirePublishableUrl(url: String) {
    require(utf8Size(url) <= LINK_URL_MAX_BYTES) { "This URL is too long to use on Harbor" }
}

/** Longest prefix of [s] that fits in [maxBytes] UTF-8 bytes, without splitting a code point. */
internal fun truncateUtf8(s: String, maxBytes: Int): String {
    var bytes = 0
    var end = 0
    while (end < s.length) {
        val cp = s.codePointAt(end)
        val size = when {
            cp < 0x80 -> 1
            cp < 0x800 -> 2
            cp < 0x10000 -> 3
            else -> 4
        }
        if (bytes + size > maxBytes) break
        bytes += size
        end += Character.charCount(cp)
    }
    return s.substring(0, end)
}

/** [s] if it fits in [maxBytes] UTF-8 bytes, otherwise its longest prefix that fits with an ellipsis appended. */
internal fun truncateUtf8WithEllipsis(s: String, maxBytes: Int): String {
    if (utf8Size(s) <= maxBytes) return s
    return truncateUtf8(s, maxBytes - utf8Size(ELLIPSIS)).trimEnd() + ELLIPSIS
}

private fun utf8Size(s: String) = s.toByteArray(Charsets.UTF_8).size
