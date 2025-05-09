package com.futo.platformplayer.views.adapters

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.IPlatformChannelContent
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.platform.PlatformIndicator
import com.futo.platformplayer.views.subscriptions.SubscribeButton


open class ChannelView : LinearLayout {
    protected val _feedStyle : FeedStyle;
    protected val _tiny: Boolean

    private val _textName: TextView;
    private val _creatorThumbnail: CreatorThumbnail;
    private val _textMetadata: TextView;
    private val _buttonSubscribe: SubscribeButton;
    private val _platformIndicator: PlatformIndicator;

    val onClick = Event1<IPlatformChannelContent>();

    var currentChannel: IPlatformChannelContent? = null
        private set

    val content: IPlatformContent? get() = currentChannel;

    constructor(context: Context, feedStyle: FeedStyle, tiny: Boolean) : super(context) {
        inflate(feedStyle);
        _feedStyle = feedStyle;
        _tiny = tiny

        _textName = findViewById(R.id.text_channel_name);
        _creatorThumbnail = findViewById(R.id.creator_thumbnail);
        _textMetadata = findViewById(R.id.text_channel_metadata);
        _buttonSubscribe = findViewById(R.id.button_subscribe);
        _platformIndicator = findViewById(R.id.platform_indicator);

        //_textName.setOnClickListener { currentChannel?.let { onClick.emit(it) }; }
        //_creatorThumbnail.setOnClickListener { currentChannel?.let { onClick.emit(it) }; }
        //_textMetadata.setOnClickListener { currentChannel?.let { onClick.emit(it) }; }

        if (_tiny) {
            _buttonSubscribe.visibility = View.GONE;
            _textMetadata.visibility = View.GONE;
        }

        findViewById<ConstraintLayout>(R.id.root).setOnClickListener {
            val s = currentChannel ?: return@setOnClickListener;
            onClick.emit(s);
        }
    }

    protected open fun inflate(feedStyle: FeedStyle) {
        inflate(context, when(feedStyle) {
            FeedStyle.PREVIEW -> R.layout.list_creator
            else -> R.layout.list_creator
        }, this)
    }

    open fun bind(content: IPlatformContent) {
        isClickable = true;

        if(content !is IPlatformChannelContent) {
            currentChannel = null;
            return;
        }
        currentChannel = content;

        _creatorThumbnail.setThumbnail(content.thumbnail, false);
        _textName.text = content.name;

        if(content.subscribers == null || (content.subscribers ?: 0) <= 0L)
            _textMetadata.visibility = View.GONE;
        else {
            _textMetadata.text = if((content.subscribers ?: 0) > 0) content.subscribers!!.toHumanNumber() + " " + context.getString(R.string.subscribers) else "";
            _textMetadata.visibility = View.VISIBLE;
        }
        _buttonSubscribe.setSubscribeChannel(content.url);
        _platformIndicator.setPlatformFromClientID(content.id.pluginId);
    }

    companion object {
        private val TAG = "ChannelView"
    }
}
