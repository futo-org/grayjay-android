package com.futo.platformplayer.api.media.models.playlists

import com.futo.platformplayer.api.media.models.contents.IPlatformContent

interface IPlatformPlaylist : IPlatformContent {
    val thumbnail: String?;
    val videoCount: Int;
}