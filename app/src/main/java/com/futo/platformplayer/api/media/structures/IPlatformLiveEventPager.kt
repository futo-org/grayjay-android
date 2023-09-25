package com.futo.platformplayer.api.media.structures

import com.futo.platformplayer.api.media.models.live.IPlatformLiveEvent

/**
 * A special pager intended for live chat implementation. Extended if required based on the JS implementations
 */
interface IPlatformLiveEventPager: IPager<IPlatformLiveEvent> {
    val nextRequest: Int;
}