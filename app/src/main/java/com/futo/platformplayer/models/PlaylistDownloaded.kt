package com.futo.platformplayer.models

import com.futo.platformplayer.downloads.PlaylistDownloadDescriptor

data class PlaylistDownloaded(
    val downloadDescriptor: PlaylistDownloadDescriptor,
    val playlist: Playlist
);