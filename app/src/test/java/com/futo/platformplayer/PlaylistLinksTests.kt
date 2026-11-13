package com.futo.platformplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistLinksTests {
    private val watch = "https://www.youtube.com/watch?v=64PeEQQ10EQ&list=PLOzg_E5wDEpQ&index=3";
    private val playlist = "https://www.youtube.com/playlist?list=PLOzg_E5wDEpQ";

    @Test
    fun findPlaylistUrl_usesLinkWhenPluginAcceptsIt() {
        assertEquals(watch, PlaylistLinks.findPlaylistUrl(watch) { true });
    }

    @Test
    fun findPlaylistUrl_triesPlaylistFormFromListParameter() {
        assertEquals(playlist, PlaylistLinks.findPlaylistUrl(watch) { it == playlist });
        assertNull(PlaylistLinks.findPlaylistUrl(watch) { false });
        assertNull(PlaylistLinks.findPlaylistUrl("https://www.youtube.com/watch?v=64PeEQQ10EQ") { it.contains("list=") });
    }

    @Test
    fun playlistUrlFromListParameter_keepsHostAndScheme() {
        assertEquals("https://m.youtube.com/playlist?list=PL1", PlaylistLinks.playlistUrlFromListParameter("https://m.youtube.com/watch?list=PL1&v=a"));
        assertNull(PlaylistLinks.playlistUrlFromListParameter("https://www.youtube.com/watch?v=a&list="));
        assertNull(PlaylistLinks.playlistUrlFromListParameter("not a url"));
    }
}
