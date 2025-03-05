package com.futo.platformplayer.views.adapters.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.dp
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.selectBestImage
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.polycentric.core.toURLInfoSystemLinkUrl

class CreatorBarViewHolder(private val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<IPlatformChannel>(
    LayoutInflater.from(_viewGroup.context).inflate(R.layout.view_subscription_bar_icon, _viewGroup, false)) {

    private val _creatorThumbnail: CreatorThumbnail;
    private val _name: TextView;
    private var _channel: IPlatformChannel? = null;

    val onClick = Event1<IPlatformChannel>();

    init {
        _creatorThumbnail = _view.findViewById(R.id.creator_thumbnail);
        _name = _view.findViewById(R.id.text_channel_name);
        _view.findViewById<LinearLayout>(R.id.root).setOnClickListener {
            val s = _channel ?: return@setOnClickListener;
            onClick.emit(s);
        }
    }

    override fun bind(value: IPlatformChannel) {
        _channel = value;

        _creatorThumbnail.setThumbnail(value.thumbnail, false);
        _name.text = value.name;
    }

    companion object {
        private const val TAG = "CreatorBarViewHolder";
    }
}
class SelectableCreatorBarViewHolder(private val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<SelectableCreatorBarViewHolder.Selectable>(
    LayoutInflater.from(_viewGroup.context).inflate(R.layout.view_subscription_bar_icon, _viewGroup, false)) {

    private val _creatorThumbnail: CreatorThumbnail;
    private val _name: TextView;
    private var _channel: Selectable? = null;

    val onClick = Event1<Selectable>();

    init {
        _creatorThumbnail = _view.findViewById(R.id.creator_thumbnail);
        _name = _view.findViewById(R.id.text_channel_name);
        _view.findViewById<LinearLayout>(R.id.root).setOnClickListener {
            val s = _channel ?: return@setOnClickListener;
            onClick.emit(s);
        }
    }

    override fun bind(value: Selectable) {
        _channel = value;

        if(value.active)
            _view.setBackgroundColor(_view.context.resources.getColor(R.color.colorPrimaryDark, null))
        else
            _view.setBackgroundColor(_view.context.resources.getColor(R.color.transparent, null))

        _creatorThumbnail.setThumbnail(value.channel.thumbnail, false);
        _name.text = value.channel.name;
    }

    companion object {
        private const val TAG = "CreatorBarViewHolder";
    }

    data class Selectable(var channel: IPlatformChannel, var active: Boolean)
}