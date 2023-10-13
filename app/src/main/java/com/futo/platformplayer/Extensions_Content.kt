package com.futo.platformplayer

import com.futo.platformplayer.api.media.models.streams.IVideoSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.helpers.VideoHelper

fun IPlatformVideoDetails.isDownloadable(): Boolean = VideoHelper.isDownloadable(this);
fun IVideoSource.isDownloadable(): Boolean = VideoHelper.isDownloadable(this);
fun IAudioSource.isDownloadable(): Boolean = VideoHelper.isDownloadable(this);


fun IVideoSourceDescriptor.hasAnySource(): Boolean = this.videoSources.any() || (this is VideoUnMuxedSourceDescriptor && this.audioSources.any());