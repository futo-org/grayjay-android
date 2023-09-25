package com.futo.platformplayer.views.adapters.viewholders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.platform.PlatformIndicator
import com.futo.platformplayer.views.subscriptions.SubscribeButton

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

    override fun bind(authorLink: PlatformAuthorLink) {
        _textName.text = authorLink.name;
        _creatorThumbnail.setThumbnail(authorLink.thumbnail, false);
        if(authorLink.subscribers == null || (authorLink.subscribers ?: 0) <= 0L)
            _textMetadata.visibility = View.GONE;
        else {
            _textMetadata.text = authorLink.subscribers!!.toHumanNumber() + " subscribers";
            _textMetadata.visibility = View.VISIBLE;
        }
        _buttonSubscribe.setSubscribeChannel(authorLink.url);
        _platformIndicator.setPlatformFromClientID(authorLink.id.pluginId);
        _authorLink = authorLink;
    }

    companion object {
        private const val TAG = "CreatorViewHolder";
    }
}