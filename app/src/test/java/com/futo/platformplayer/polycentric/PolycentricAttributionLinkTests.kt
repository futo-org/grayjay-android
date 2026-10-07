package com.futo.platformplayer.polycentric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that link attributions always satisfy the server's `Link` validation (non-empty title of
 * at most 100 UTF-8 bytes, URL of at most 200 UTF-8 bytes).
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
}
