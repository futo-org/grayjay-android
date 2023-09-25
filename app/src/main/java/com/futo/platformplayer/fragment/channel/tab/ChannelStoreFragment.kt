package com.futo.platformplayer.fragment.channel.tab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel

class ChannelStoreFragment : Fragment, IChannelTabFragment {
    constructor() : super() {

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_channel_store, container, false);
        return view;
    }

    override fun onDestroyView() {
        super.onDestroyView();
    }

    override fun setChannel(channel: IPlatformChannel) {

    }

    companion object {
        val TAG = "StoreListFragment";
        fun newInstance() = ChannelStoreFragment().apply { }
    }
}