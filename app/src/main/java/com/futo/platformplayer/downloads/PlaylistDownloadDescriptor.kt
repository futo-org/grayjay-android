package com.futo.platformplayer.downloads

import com.futo.platformplayer.api.media.models.video.IPlatformVideo

@kotlinx.serialization.Serializable
data class PlaylistDownloadDescriptor(
    val id: String,
    val targetPxCount: Long?,
    val targetBitrate: Long?
) {
    var preventDownload: MutableList<String> = arrayListOf();

    fun getPreventDownloadList(): List<String> = synchronized(preventDownload){ preventDownload };
    fun shouldDownload(video: IPlatformVideo): Boolean {
        synchronized(preventDownload) {
            return !preventDownload.contains(video.url);
        }
    }
}