package com.futo.platformplayer.views.video.thumbnails

import android.graphics.Bitmap

interface ISeekThumbnailProvider {
    val seekResolutionMs: Int;

    suspend fun getThumbnail(positionMs: Long): SeekThumbnail?;
}


sealed interface SeekThumbnail {
    val frames: List<SeekThumbnailFrame>?;

    class Url(val url: String, frames: List<SeekThumbnailFrame>? = null) : SeekThumbnail {
        override val frames = frames?.let { it.sortedBy { it.timestampMs } }
    }

    class Image(val bitmap: Bitmap, frames: List<SeekThumbnailFrame>? = null) : SeekThumbnail {
        override val frames = frames?.let { it.sortedBy { it.timestampMs } }
    }
}

data class SeekThumbnailFrame(val timestampMs: Long, val x: Int, val y: Int, val width: Int, val height: Int) {
    init {
        require(x >= 0 && y >= 0 && width > 0 && height > 0) { "Invalid thumbnail rectangle" };
    }

    internal fun contains(positionMs: Long, resolutionMs: Long): Boolean {
        return positionMs >= timestampMs && positionMs - timestampMs < resolutionMs;
    }
}
