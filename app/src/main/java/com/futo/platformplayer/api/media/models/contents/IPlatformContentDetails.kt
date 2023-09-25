package com.futo.platformplayer.api.media.models.contents

import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.playback.IPlaybackTracker
import com.futo.platformplayer.api.media.structures.IPager

interface IPlatformContentDetails: IPlatformContent {


    fun getComments(client: IPlatformClient): IPager<IPlatformComment>?;
    fun getPlaybackTracker(): IPlaybackTracker?;
}