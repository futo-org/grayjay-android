package com.futo.platformplayer.views.adapters.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.channels.SerializedChannel
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.dp
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.selectBestImage
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.polycentric.core.toURLInfoSystemLinkUrl

class SubscriptionBarViewHolder(private val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<Subscription>(
    LayoutInflater.from(_viewGroup.context).inflate(R.layout.view_subscription_bar_icon, _viewGroup, false)) {

    private val _creatorThumbnail: CreatorThumbnail;
    private val _name: TextView;
    private var _subscription: Subscription? = null;
    private var _channel: SerializedChannel? = null;

    val onClick = Event1<Subscription>();
    
    init {
        _creatorThumbnail = _view.findViewById(R.id.creator_thumbnail);
        _name = _view.findViewById(R.id.text_channel_name);
        _view.findViewById<LinearLayout>(R.id.root).setOnClickListener {
            val s = _subscription ?: return@setOnClickListener;
            onClick.emit(s);
        }
    }

    override fun bind(value: Subscription) {
        _channel = value.channel;

        _creatorThumbnail.setThumbnail(value.channel.thumbnail, false);
        _name.text = value.channel.name;

        _subscription = value;
    }

    companion object {
        private const val TAG = "SubscriptionBarViewHolder";
    }
}