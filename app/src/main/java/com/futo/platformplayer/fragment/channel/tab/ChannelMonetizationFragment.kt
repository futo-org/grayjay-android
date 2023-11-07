package com.futo.platformplayer.fragment.channel.tab

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.fragment.mainactivity.main.PolycentricProfile
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.views.SupportView
import com.futo.platformplayer.views.buttons.BigButton


class ChannelMonetizationFragment : Fragment, IChannelTabFragment {
    private var _supportView: SupportView? = null

    private var _lastChannel: IPlatformChannel? = null;
    private var _lastPolycentricProfile: PolycentricProfile? = null;

    constructor() : super() { }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_channel_monetization, container, false);
        _supportView = view.findViewById(R.id.support);

        _lastChannel?.also {
            setChannel(it);
        };

        _lastPolycentricProfile?.also {
            setPolycentricProfile(it, animate = false);
        }

        return view;
    }

    override fun onDestroyView() {
        super.onDestroyView();
        _supportView = null;
    }

    override fun setChannel(channel: IPlatformChannel) {
        _lastChannel = channel;
    }

    fun setPolycentricProfile(polycentricProfile: PolycentricProfile?, animate: Boolean) {
        _lastPolycentricProfile = polycentricProfile
        _supportView?.setPolycentricProfile(polycentricProfile, animate)
    }

    companion object {
        val TAG = "ChannelMonetizationFragment";
        fun newInstance() = ChannelMonetizationFragment().apply { }
    }
}