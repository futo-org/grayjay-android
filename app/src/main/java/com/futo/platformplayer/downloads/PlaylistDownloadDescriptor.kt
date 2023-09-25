package com.futo.platformplayer.downloads

@kotlinx.serialization.Serializable
data class PlaylistDownloadDescriptor(
    val id: String,
    val targetPxCount: Long?,
    val targetBitrate: Long?
);