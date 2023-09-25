package com.futo.platformplayer.models

import com.futo.platformplayer.api.media.models.video.IPlatformVideo

data class PlatformVideoWithTime(val video: IPlatformVideo, val time: Long);