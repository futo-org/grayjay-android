package com.futo.platformplayer

import com.futo.platformplayer.api.media.models.streams.IVideoSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSSource
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.views.video.datasources.JSHttpDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource

fun IPlatformVideoDetails.isDownloadable(): Boolean = VideoHelper.isDownloadable(this);
fun IVideoSource.isDownloadable(): Boolean = VideoHelper.isDownloadable(this);
fun IAudioSource.isDownloadable(): Boolean = VideoHelper.isDownloadable(this);

fun JSSource.getHttpDataSourceFactory(): HttpDataSource.Factory {
    val requestModifier = getRequestModifier();
    return if (requestModifier != null) {
        JSHttpDataSource.Factory().setRequestModifier(requestModifier);
    } else {
        DefaultHttpDataSource.Factory();
    }
}

fun IVideoSourceDescriptor.hasAnySource(): Boolean = this.videoSources.any() || (this is VideoUnMuxedSourceDescriptor && this.audioSources.any());