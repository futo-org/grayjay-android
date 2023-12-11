package com.futo.platformplayer.views.adapters.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.others.Checkbox
import com.futo.platformplayer.views.platform.PlatformIndicator

class ImportSubscriptionViewHolder(private val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<SelectableIPlatformChannel>(
    LayoutInflater.from(_viewGroup.context).inflate(R.layout.list_import_subscription, _viewGroup, false)) {

    private val _checkbox: Checkbox;
    private val _imageThumbnail: ImageView;
    private val _textName: TextView;
    private val _platform: PlatformIndicator;
    private val _root: LinearLayout;
    private var _channel: SelectableIPlatformChannel? = null;

    val onSelectedChange = Event1<SelectableIPlatformChannel>();
    
    init {
        _checkbox = _view.findViewById(R.id.checkbox);
        _imageThumbnail = _view.findViewById(R.id.image_channel_thumbnail);
        _textName = _view.findViewById(R.id.text_name);
        _platform = _view.findViewById(R.id.platform);
        _root = _view.findViewById(R.id.root);

        _imageThumbnail.clipToOutline = true;

        _checkbox.onValueChanged.subscribe {
            _channel?.selected = it;
            _channel?.let { onSelectedChange.emit(it); };
        };

        _root.setOnClickListener {
            _checkbox.value = !_checkbox.value;
            _channel?.selected = _checkbox.value;
            _channel?.let { onSelectedChange.emit(it); };
        };
    }

    override fun bind(value: SelectableIPlatformChannel) {
        _textName.text = value.channel.name;
        _checkbox.value = value.selected;

        val thumbnail = value.channel.thumbnail;
        if (thumbnail != null)
            Glide.with(_imageThumbnail)
                .load(thumbnail)
                .placeholder(R.drawable.placeholder_channel_thumbnail)
                .into(_imageThumbnail);
        else
            Glide.with(_imageThumbnail).clear(_imageThumbnail);

        _platform.setPlatformFromClientID(value.channel.id.pluginId);
        _channel = value;
    }
}

class SelectableIPlatformChannel(
    val channel: IPlatformChannel,
    var selected: Boolean = false
) { }