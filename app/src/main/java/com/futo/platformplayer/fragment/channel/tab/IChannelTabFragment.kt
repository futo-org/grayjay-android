package com.futo.platformplayer.fragment.channel.tab

import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.fragment.mainactivity.main.PolycentricProfile

interface IChannelTabFragment {
    fun setChannel(channel: IPlatformChannel)
    fun setPolycentricProfile(polycentricProfile: PolycentricProfile?) {

    }
}
