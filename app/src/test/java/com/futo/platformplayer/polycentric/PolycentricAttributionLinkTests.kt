package com.futo.platformplayer.polycentric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that link attributions and previews always satisfy the server's `Link` validation (non-empty
 * title of at most 100 UTF-8 bytes, description, image and URL of at most 200 UTF-8 bytes).
 */
class PolycentricAttributionLinkTests {
    private val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    private fun titleOf(title: String?, url: String = this.url): String =
        attributionLink(url, title).link!!.title

    private fun utf8Size(s: String) = s.toByteArray(Charsets.UTF_8).size

    @Test
    fun usesProvidedTitle() {
        assertEquals("A video", titleOf("  A video  "))
    }

    @Test
    fun fallsBackToHostWhenTitleMissingOrBlank() {
        assertEquals("www.youtube.com", titleOf(null))
        assertEquals("www.youtube.com", titleOf("   "))
    }

    @Test
    fun fallsBackToUrlWhenHostMissing() {
        assertEquals("not a url", titleOf(null, "not a url"))
    }

    @Test
    fun keepsUrlUnchanged() {
        assertEquals(url, attributionLink(url, "A video").link!!.url)
    }

    @Test
    fun truncatesAsciiTitleTo100Bytes() {
        val title = titleOf("a".repeat(250))
        assertEquals(100, utf8Size(title))
    }

    @Test
    fun truncatesMultiByteTitleWithoutSplittingCodePoints() {
        // 3 bytes per character: 33 characters fit in 100 bytes.
        val title = titleOf("日".repeat(80))
        assertEquals("日".repeat(33), title)
        assertTrue(utf8Size(title) <= 100)

        // 4 bytes per code point (surrogate pairs): 25 emoji fit exactly.
        val emoji = titleOf("😀".repeat(40))
        assertEquals("😀".repeat(25), emoji)
    }

    @Test
    fun truncateUtf8KeepsShortStrings() {
        assertEquals("abc", truncateUtf8("abc", 100))
        assertEquals("", truncateUtf8("日", 2))
    }

    @Test
    fun requirePublishableUrlRejectsLongUrls() {
        requirePublishableUrl("https://example.com/" + "a".repeat(180))
        assertThrows(IllegalArgumentException::class.java) {
            requirePublishableUrl("https://example.com/" + "a".repeat(181))
        }
    }

    @Test
    fun linkPreviewFillsOutAllFields() {
        val link = linkPreview(url, AttributedContent("A video", "A description", listOf("https://i.ytimg.com/vi/x/hq.jpg")))
        assertEquals("A video", link.title)
        assertEquals("A description", link.description)
        assertEquals("https://i.ytimg.com/vi/x/hq.jpg", link.image)
        assertEquals(url, link.url)
    }

    @Test
    fun linkPreviewWithoutContentFallsBackToHost() {
        val link = linkPreview(url, null)
        assertEquals("www.youtube.com", link.title)
        assertNull(link.description)
        assertNull(link.image)
    }

    @Test
    fun linkPreviewConvertsDescriptionHtmlToText() {
        val link = linkPreview(url, AttributedContent("t", "<p>Hello <b>world</b></p>\n\n<a href=\"x\">link</a>"))
        assertEquals("Hello world link", link.description)
    }

    @Test
    fun linkPreviewOmitsBlankDescription() {
        assertNull(linkPreview(url, AttributedContent("t", "  <p> </p> ")).description)
    }

    @Test
    fun linkPreviewTruncatesLongDescriptionWithEllipsis() {
        val description = linkPreview(url, AttributedContent("t", "a".repeat(500))).description!!
        assertTrue(utf8Size(description) <= 200)
        assertTrue(description.endsWith("…"))
    }

    @Test
    fun linkPreviewPicksFirstUsableImage() {
        val images = listOf(
            "",
            "file:///storage/thumb.jpg",
            "https://example.com/" + "a".repeat(181),
            "https://example.com/ok.jpg",
            "https://example.com/other.jpg",
        )
        assertEquals("https://example.com/ok.jpg", linkPreview(url, AttributedContent("t", null, images)).image)
    }

    @Test
    fun truncateUtf8WithEllipsisKeepsShortStrings() {
        assertEquals("abc", truncateUtf8WithEllipsis("abc", 3))
        assertEquals("ab…", truncateUtf8WithEllipsis("abcdef", 5))
        assertEquals("日…", truncateUtf8WithEllipsis("日日日", 8))
    }
}
