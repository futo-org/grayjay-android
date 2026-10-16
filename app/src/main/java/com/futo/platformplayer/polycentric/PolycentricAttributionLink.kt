package com.futo.platformplayer.polycentric

import polycentric.v2.AttributedTo
import polycentric.v2.Link

/** Server-side limits on `Link` fields, in UTF-8 bytes (rs-common `models/link.rs`). */
private const val LINK_TITLE_MAX_BYTES = 100
private const val LINK_URL_MAX_BYTES = 200

/**
 * Build an [AttributedTo] link for [url]. The server requires a non-empty title of at most
 * [LINK_TITLE_MAX_BYTES] bytes. When no title is known the URL's host is used instead.
 */
internal fun attributionLink(url: String, title: String?): AttributedTo {
    val resolved = title?.trim()?.takeIf { it.isNotEmpty() }
        ?: runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotEmpty() }
        ?: url
    return AttributedTo(link = Link(title = truncateUtf8(resolved, LINK_TITLE_MAX_BYTES), url = url))
}

/** Throws [IllegalArgumentException] if the server would reject [url] as a link target. */
internal fun requirePublishableUrl(url: String) {
    require(url.toByteArray(Charsets.UTF_8).size <= LINK_URL_MAX_BYTES) { "This URL is too long to use on Harbor" }
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
