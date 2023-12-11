package com.futo.platformplayer.views.adapters.feedtypes

import android.annotation.SuppressLint
import android.content.Context
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.locked.IPlatformLockedContent
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.images.GlideHelper.Companion.loadThumbnails
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.platform.PlatformIndicator

@SuppressLint("ViewConstructor")
class PreviewLockedView : LinearLayout {
    private val _feedStyle : FeedStyle;

    private val _textChannelName: TextView;
    private val _textVideoName: TextView;
    private val _imageChannelThumbnail: ImageView;
    private val _imageVideoThumbnail: ImageView;

    private val _platformIndicator: PlatformIndicator;

    private val _textLockedDescription: TextView;
    private val _textLockedUrl: TextView;

    private val _textVideoMetadata: TextView;

    val onLockedUrlClicked = Event1<String>();

    constructor(context: Context, feedStyle: FeedStyle) : super(context) {
        inflate(feedStyle);
        _feedStyle = feedStyle;

        _textVideoName = findViewById(R.id.text_video_name);
        _textChannelName = findViewById(R.id.text_channel_name);
        _imageChannelThumbnail = findViewById(R.id.image_channel_thumbnail);
        _imageVideoThumbnail = findViewById(R.id.image_video_thumbnail);
        _platformIndicator = findViewById(R.id.thumbnail_platform)
        _textLockedDescription = findViewById(R.id.text_locked_description);
        _textLockedUrl = findViewById(R.id.text_locked_url);
        _textVideoMetadata = findViewById(R.id.text_video_metadata);

        setOnClickListener {
            if(!_textLockedUrl.text.isNullOrEmpty())
                onLockedUrlClicked.emit(_textLockedUrl.text.toString());
        }
    }

    fun inflate(feedStyle: FeedStyle) {
        inflate(context, when(feedStyle) {
            FeedStyle.PREVIEW -> R.layout.list_locked_preview
            else -> R.layout.list_locked_thumbnail
        }, this)
    }

    fun bind(content: IPlatformContent) {
        _textVideoName.text = content.name;
        _textChannelName.text = content.author.name;
        _platformIndicator.setPlatformFromClientID(content.id.pluginId);

        if(content is IPlatformLockedContent) {
            _imageVideoThumbnail.loadThumbnails(content.contentThumbnails, false) {
                it.placeholder(R.drawable.placeholder_video_thumbnail)
                    .into(_imageVideoThumbnail);
            };
            Glide.with(_imageChannelThumbnail)
                .load(content.author.thumbnail)
                .placeholder(R.drawable.placeholder_channel_thumbnail)
                .into(_imageChannelThumbnail);
            _textLockedDescription.text = content.lockDescription ?: "";
            _textLockedUrl.text = content.unlockUrl ?: "";
        }
        else {
            _imageChannelThumbnail.setImageResource(0);
            _imageVideoThumbnail.setImageResource(0);
            _textLockedDescription.text = "";
            _textLockedUrl.text = "";
        }

        if(_textLockedUrl.text.isNullOrEmpty()) {
            _textLockedUrl.visibility = GONE;
            _textVideoMetadata.text = "";
        }
        else {
            _textLockedUrl.visibility = VISIBLE;
            _textVideoMetadata.text = context.getString(R.string.tap_to_open_in_browser);

        }
    }

    companion object {
        private val TAG = "PreviewLockedView"
    }
}
