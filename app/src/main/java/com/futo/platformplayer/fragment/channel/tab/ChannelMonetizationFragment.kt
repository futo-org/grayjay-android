package com.futo.platformplayer.fragment.channel.tab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.views.SupportView
import com.futo.polycentric.core.PolycentricProfile


class ChannelMonetizationFragment : Fragment, IChannelTabFragment {
    private var _supportView: SupportView? = null
    private var _textMonetization: TextView? = null

    private var _lastChannel: IPlatformChannel? = null;
    private var _lastPolycentricProfile: PolycentricProfile? = null;

    constructor() : super() { }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_channel_monetization, container, false);
        _supportView = view.findViewById(R.id.support);
        _textMonetization = view.findViewById(R.id.text_monetization);

        _lastChannel?.also {
            setChannel(it);
        };

        _supportView?.visibility = View.GONE;
        _textMonetization?.visibility = View.GONE;
        setPolycentricProfile(_lastPolycentricProfile);
        return view;
    }

    override fun onDestroyView() {
        super.onDestroyView();
        _supportView = null;
        _textMonetization = null;
    }

    override fun setChannel(channel: IPlatformChannel) {
        _lastChannel = channel;
    }

    override fun setPolycentricProfile(polycentricProfile: PolycentricProfile?) {
        _lastPolycentricProfile = polycentricProfile
        if (polycentricProfile != null) {
            _supportView?.setPolycentricProfile(polycentricProfile)
            _supportView?.visibility = View.VISIBLE
            _textMonetization?.visibility = View.GONE
        } else {
            _supportView?.setPolycentricProfile(null)
            _supportView?.visibility = View.GONE
            _textMonetization?.visibility = View.VISIBLE
        }
    }

    companion object {
        val TAG = "ChannelMonetizationFragment";
        fun newInstance() = ChannelMonetizationFragment().apply { }
    }
}