package com.futo.platformplayer.api.media.models.playlists

import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.structures.IPager

interface IPlatformPlaylist : IPlatformContent {
    val thumbnail: String?;
    val videoCount: Int;
}