package com.futo.platformplayer.polycentric

import org.junit.Assert.assertEquals
import org.junit.Test

class PolycentricUrlCanonicalizationTests {
    private fun assertNormalize(expected: String, input: String) =
        assertEquals(expected, normalizeVideoUrl(input))

    private fun assertRoundTrip(url: String) {
        val once = normalizeVideoUrl(url)
        assertEquals(once, normalizeVideoUrl(once))
    }

    @Test
    fun `generic fallback enforces www strips mobile prefix fragment and trailing slash`() {
        assertNormalize("https://www.example.org/watch/abc", "https://www.example.org/watch/abc/")
        assertNormalize("https://www.example.org/watch/abc", "https://example.org/watch/abc#fragment")
        assertNormalize("https://www.example.org/watch/abc", "https://m.example.org/watch/abc")
        assertNormalize("https://www.example.org/watch/abc", "https://mobile.example.org/watch/abc")
        assertNormalize("https://www.example.org/watch/abc?x=1", "https://example.org/watch/abc?x=1")
        assertNormalize("https://www.example.org", "https://example.org/")
    }

    @Test
    fun `www is added when absent and kept when present`() {
        assertNormalize(
            "https://www.youtube.com/watch?v=u_Lxkt50xOg",
            "https://youtube.com/watch?v=u_Lxkt50xOg",
        )
        assertNormalize(
            "https://www.youtube.com/watch?v=u_Lxkt50xOg",
            "https://www.youtube.com/watch?v=u_Lxkt50xOg",
        )
        assertNormalize(
            "https://www.youtube.com/watch?v=u_Lxkt50xOg",
            "https://m.youtube.com/watch?v=u_Lxkt50xOg",
        )
    }

    @Test
    fun `www is enforced on subdomained hosts as well`() {
        assertNormalize("https://www.open.lbry.com/video/abc", "https://open.lbry.com/video/abc")
    }

    @Test
    fun `query is preserved untouched`() {
        assertNormalize("https://www.example.org/watch/abc?p=3&share_source=copy", "https://example.org/watch/abc?p=3&share_source=copy")
        assertNormalize("https://www.example.org/watch/abc", "https://example.org/watch/abc?")
    }

    @Test
    fun `http upgrades to https`() {
        assertNormalize("https://www.example.org/watch/abc", "http://www.example.org/watch/abc")
    }

    @Test
    fun `unsupported schemes and unparseable input return unchanged`() {
        assertNormalize("not a url", "not a url")
        assertNormalize("ftp://example.org/video", "ftp://example.org/video")
        assertNormalize("lbry://@channel/video#0123abcd?fee=1", "lbry://@channel/video#0123abcd?fee=1")
        assertNormalize("", "   ")
    }

    @Test
    fun `normalization is idempotent`() {
        for (url in listOf(
            "https://www.example.org/watch/abc/?utm=social",
            "https://example.org/watch/abc#fragment",
            "https://example.org",
            "https://m.example.org/watch/abc",
            "not a url",
            "lbry://@channel/video#0123abcd"
        )) {
            assertRoundTrip(url)
        }
    }
}
