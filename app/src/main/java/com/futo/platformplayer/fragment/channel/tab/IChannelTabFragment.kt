package com.futo.platformplayer.fragment.channel.tab

import com.futo.platformplayer.api.media.models.channels.IPlatformChannel

interface IChannelTabFragment {
    fun setChannel(channel: IPlatformChannel);
}