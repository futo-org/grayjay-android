package com.futo.platformplayer

import java.net.URI

/** Finds the playlist in a link that opens a video inside a playlist, for example "watch?v=...&list=...". */
object PlaylistLinks {
    /**
     * Returns the first URL that [isPlaylistUrl] accepts: the link itself, then a "/playlist?list=" link
     * made from its "list" parameter. Plugins can accept only the second form.
     */
    fun findPlaylistUrl(url: String, isPlaylistUrl: (String) -> Boolean): String? {
        if (isPlaylistUrl(url))
            return url;
        val derived = playlistUrlFromListParameter(url) ?: return null;
        return if (derived != url && isPlaylistUrl(derived)) derived else null;
    }

    fun playlistUrlFromListParameter(url: String): String? {
        val uri = try { URI(url) } catch (ex: Throwable) { return null };
        val scheme = uri.scheme ?: return null;
        val authority = uri.rawAuthority ?: return null;
        val list = uri.rawQuery?.split("&")
            ?.map { it.split("=", limit = 2) }
            ?.firstOrNull { it.size == 2 && it[0] == "list" && it[1].isNotEmpty() }
            ?.get(1) ?: return null;
        return "$scheme://$authority/playlist?list=$list";
    }
}
