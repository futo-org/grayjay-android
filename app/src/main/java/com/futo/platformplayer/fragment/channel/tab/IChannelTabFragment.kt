package com.futo.platformplayer.fragment.channel.tab

import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.polycentric.core.PolycentricProfile

interface IChannelTabFragment {
    fun setChannel(channel: IPlatformChannel)
    fun setPolycentricProfile(polycentricProfile: PolycentricProfile?) {

    }
}
