package com.futo.platformplayer.views.adapters.viewholders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.dp
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.selectBestImage
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.platform.PlatformIndicator
import com.futo.platformplayer.views.subscriptions.SubscribeButton
import com.futo.polycentric.core.toURLInfoSystemLinkUrl

class CreatorViewHolder(private val _viewGroup: ViewGroup, private val _tiny: Boolean) : AnyAdapter.AnyViewHolder<PlatformAuthorLink>(
    LayoutInflater.from(_viewGroup.context).inflate(R.layout.list_creator, _viewGroup, false)) {

    private val _textName: TextView;
    private val _creatorThumbnail: CreatorThumbnail;
    private val _textMetadata: TextView;
    private val _buttonSubscribe: SubscribeButton;
    private val _platformIndicator: PlatformIndicator;
    private var _authorLink: PlatformAuthorLink? = null;

    val onClick = Event1<PlatformAuthorLink>();

    init {
        _textName = _view.findViewById(R.id.text_channel_name);
        _creatorThumbnail = _view.findViewById(R.id.creator_thumbnail);
        _textMetadata = _view.findViewById(R.id.text_channel_metadata);
        _buttonSubscribe = _view.findViewById(R.id.button_subscribe);
        _platformIndicator = _view.findViewById(R.id.platform_indicator);

        if (_tiny) {
            _buttonSubscribe.visibility = View.GONE;
            _textMetadata.visibility = View.GONE;
        }

        _view.findViewById<ConstraintLayout>(R.id.root).setOnClickListener {
            val s = _authorLink ?: return@setOnClickListener;
            onClick.emit(s);
        }
    }

    override fun bind(value: PlatformAuthorLink) {
        _creatorThumbnail.setThumbnail(value.thumbnail, false);
        _textName.text = value.name;

        if(value.subscribers == null || (value.subscribers ?: 0) <= 0L)
            _textMetadata.visibility = View.GONE;
        else {
            _textMetadata.text = if((value.subscribers ?: 0) > 0) value.subscribers!!.toHumanNumber() + " " + _view.context.getString(R.string.subscribers) else "";
            _textMetadata.visibility = View.VISIBLE;
        }
        _buttonSubscribe.setSubscribeChannel(value.url);
        _platformIndicator.setPlatformFromClientID(value.id.pluginId);
        _authorLink = value;
    }

    companion object {
        private const val TAG = "CreatorViewHolder";
    }
}