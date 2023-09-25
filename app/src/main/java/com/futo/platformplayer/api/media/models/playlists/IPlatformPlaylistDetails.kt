package com.futo.platformplayer.api.media.models.playlists

import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.models.Playlist

interface IPlatformPlaylistDetails: IPlatformPlaylist {
    //TODO: Determine if this should be IPlatformContent (probably not?)
    val contents: IPager<IPlatformVideo>;

    fun toPlaylist(): Playlist;
}