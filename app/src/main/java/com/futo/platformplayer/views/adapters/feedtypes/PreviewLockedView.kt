package com.futo.platformplayer.views.adapters.feedtypes

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.locked.IPlatformLockedContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.images.GlideHelper.Companion.loadThumbnails
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.video.PlayerManager
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.platform.PlatformIndicator
import com.futo.platformplayer.views.video.FutoThumbnailPlayer
import com.futo.polycentric.core.toURLInfoSystemLinkUrl
import com.google.android.material.imageview.ShapeableImageView


class PreviewLockedView : LinearLayout {
    protected val _feedStyle : FeedStyle;

    private val _textChannelName: TextView;
    private val _textVideoName: TextView;
    //private val _imageNeoPassChannel: ImageView;
    private val _imageChannelThumbnail: ImageView;
    private val _imageVideoThumbnail: ImageView;

    private val _platformIndicator: PlatformIndicator;

    private val _textLockedDescription: TextView;
    //private val _textBrowserOpen: TextView;
    private val _textLockedUrl: TextView;

    private val _textVideoMetadata: TextView;

    val onLockedUrlClicked = Event1<String>();


    constructor(context: Context, feedStyle : FeedStyle) : super(context) {
        inflate(feedStyle);
        _feedStyle = feedStyle;

        _textVideoName = findViewById(R.id.text_video_name);
        _textChannelName = findViewById(R.id.text_channel_name);
        //_imageNeoPassChannel = findViewById(R.id.image_neopass_channel);
        _imageChannelThumbnail = findViewById(R.id.image_channel_thumbnail);
        _imageVideoThumbnail = findViewById(R.id.image_video_thumbnail);

        _platformIndicator = findViewById(R.id.thumbnail_platform)

        _textLockedDescription = findViewById(R.id.text_locked_description);
        //_textBrowserOpen = findViewById(R.id.text_browser_open);
        _textLockedUrl = findViewById(R.id.text_locked_url);

        _textVideoMetadata = findViewById(R.id.text_video_metadata);

        setOnClickListener {
            if(!_textLockedUrl.text.isNullOrEmpty())
                onLockedUrlClicked.emit(_textLockedUrl.text.toString());
        }
    }

    protected open fun inflate(feedStyle: FeedStyle) {
        inflate(context, when(feedStyle) {
            FeedStyle.PREVIEW -> R.layout.list_locked_preview
            else -> R.layout.list_locked_thumbnail
        }, this)
    }

    open fun bind(content: IPlatformContent) {
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
            _textVideoMetadata.text = "Tap to open in browser";

        }
    }

    open fun preview(video: IPlatformContentDetails?, paused: Boolean) {

    }

    companion object {
        private val TAG = "PreviewLockedView"
    }
}
