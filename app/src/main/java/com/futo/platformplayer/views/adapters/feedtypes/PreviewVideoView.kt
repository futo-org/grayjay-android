package com.futo.platformplayer.views.adapters.feedtypes

import android.animation.ObjectAnimator
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.dp
import com.futo.platformplayer.getNowDiffSeconds
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.images.GlideHelper.Companion.loadThumbnails
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.selectBestImage
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.states.StateHistory
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.toHumanTime
import com.futo.platformplayer.video.PlayerManager
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.others.ProgressBar
import com.futo.platformplayer.views.platform.PlatformIndicator
import com.futo.platformplayer.views.video.FutoThumbnailPlayer
import com.futo.polycentric.core.toURLInfoSystemLinkUrl


open class PreviewVideoView : LinearLayout {
    protected val _feedStyle : FeedStyle;

    protected val _imageVideo: ImageView
    protected val _imageChannel: ImageView?
    protected val _creatorThumbnail: CreatorThumbnail?
    protected val _platformIndicator: PlatformIndicator;
    protected val _textVideoName: TextView
    protected val _textChannelName: TextView
    protected val _textVideoMetadata: TextView
    protected val _containerDuration: LinearLayout;
    protected val _textVideoDuration: TextView;
    protected var _playerVideoThumbnail: FutoThumbnailPlayer? = null;
    protected val _containerLive: LinearLayout;
    protected val _playerContainer: FrameLayout;
    protected val _layoutDownloaded: FrameLayout;

    protected val _button_add_to_queue : View;
    protected val _button_add_to_watch_later : View;
    protected val _button_add_to : View;

    protected val _exoPlayer: PlayerManager?;
    private val _timeBar: ProgressBar?;

    val onVideoClicked = Event2<IPlatformVideo, Long>();
    val onLongPress = Event1<IPlatformVideo>();
    val onChannelClicked = Event1<PlatformAuthorLink>();
    val onAddToClicked = Event1<IPlatformVideo>();
    val onAddToQueueClicked = Event1<IPlatformVideo>();
    val onAddToWatchLaterClicked = Event1<IPlatformVideo>();

    var currentVideo: IPlatformVideo? = null
        private set

    val content: IPlatformContent? get() = currentVideo;
    val shouldShowTimeBar: Boolean

    constructor(context: Context, feedStyle : FeedStyle, exoPlayer: PlayerManager? = null, shouldShowTimeBar: Boolean = true) : super(context) {
        inflate(feedStyle);
        _feedStyle = feedStyle;
        this.shouldShowTimeBar = shouldShowTimeBar

        _playerContainer = findViewById(R.id.player_container);
        _imageVideo = findViewById(R.id.image_video_thumbnail)
        _imageChannel = findViewById(R.id.image_channel_thumbnail);
        _creatorThumbnail = findViewById(R.id.creator_thumbnail);
        _platformIndicator = findViewById(R.id.thumbnail_platform);
        _textVideoName = findViewById(R.id.text_video_name)
        _textChannelName = findViewById(R.id.text_channel_name)
        _textVideoMetadata = findViewById(R.id.text_video_metadata)
        _textVideoDuration = findViewById(R.id.thumbnail_duration);
        _containerDuration = findViewById(R.id.thumbnail_duration_container);
        _containerLive = findViewById(R.id.thumbnail_live_container);
        _button_add_to_queue = findViewById(R.id.button_add_to_queue);
        _button_add_to_watch_later = findViewById(R.id.button_add_to_watch_later);
        _button_add_to = findViewById(R.id.button_add_to);
        _layoutDownloaded = findViewById(R.id.layout_downloaded);
        _timeBar = findViewById(R.id.time_bar)

        this._exoPlayer = exoPlayer

        setOnLongClickListener {
            onLongPress()
            true
        };
        setOnClickListener {
            onOpenClicked()
        };

        _imageChannel.setOnClickListener { currentVideo?.let { onChannelClicked.emit(it.author) }  };
        _textChannelName.setOnClickListener { currentVideo?.let { onChannelClicked.emit(it.author) }  };
        _textVideoMetadata.setOnClickListener { currentVideo?.let { onChannelClicked.emit(it.author) }  };
        _button_add_to.setOnClickListener { currentVideo?.let { onAddToClicked.emit(it) } };
        _button_add_to_queue.setOnClickListener { currentVideo?.let { onAddToQueueClicked.emit(it) } };
        _button_add_to_watch_later.setOnClickListener { currentVideo?.let { onAddToWatchLaterClicked.emit(it); } }
    }

    fun hideAddTo() {
        _button_add_to.visibility = View.GONE
        //_button_add_to_queue.visibility = View.GONE
    }

    protected open fun inflate(feedStyle: FeedStyle) {
        inflate(context, when(feedStyle) {
            FeedStyle.PREVIEW -> R.layout.list_video_preview
            else -> R.layout.list_video_thumbnail
        }, this)
    }

    protected open fun onOpenClicked() {
        currentVideo?.let {
            val currentPlayer = _playerVideoThumbnail;
            var sec = if(currentPlayer != null && currentPlayer.playing)
                (currentPlayer.position / 1000).toLong();
            else 0L;
            onVideoClicked.emit(it, sec);
        }
    }

    protected open fun onLongPress() {
        currentVideo?.let {
            onLongPress.emit(it);
        }
    }


    open fun bind(content: IPlatformContent) {
        isClickable = true;

        val isPlanned = (content.datetime?.getNowDiffSeconds() ?: 0) < 0;

        stopPreview();

        _creatorThumbnail?.setThumbnail(content.author.thumbnail, false);

        val thumbnail = content.author.thumbnail
        if (thumbnail != null) {
            _imageChannel?.visibility = View.VISIBLE
            _imageChannel?.let {
                Glide.with(_imageChannel)
                    .load(content.author.thumbnail)
                    .placeholder(R.drawable.placeholder_channel_thumbnail)
                    .into(_imageChannel);
            }
        } else {
            _imageChannel?.visibility = View.GONE
        }

        _textChannelName.text = content.author.name

        _imageChannel?.clipToOutline = true;

        _textVideoName.text = content.name;
        _layoutDownloaded.visibility = if (StateDownloads.instance.isDownloaded(content.id)) VISIBLE else GONE;

        _platformIndicator.setPlatformFromClientID(content.id.pluginId);

        var metadata = ""
        if (content is IPlatformVideo && content.viewCount > 0) {
            if(content.isLive)
                metadata += "${content.viewCount.toHumanNumber()} ${context.getString(R.string.watching)} • ";
            else
                metadata += "${content.viewCount.toHumanNumber()} ${context.getString(R.string.views)} • ";
        }

        var timeMeta = if(isPlanned) {
            val ago = content.datetime?.toHumanNowDiffString(true) ?: ""
            context.getString(R.string.available_in) + " $ago";
        } else {
            val ago = content.datetime?.toHumanNowDiffString() ?: ""
            if (ago.isNotBlank()) ago + " ago" else ago;
        }

        if(content is IPlatformVideo) {
            val video = content;

            currentVideo = video

            _imageVideo.loadThumbnails(video.thumbnails, true) {
                it.placeholder(R.drawable.placeholder_video_thumbnail)
                    .crossfade()
                    .into(_imageVideo);
            };

            if(!isPlanned)
                _textVideoDuration.text = video.duration.toHumanTime(false);
            else
                _textVideoDuration.text = context.getString(R.string.planned);

            _playerVideoThumbnail?.setLive(video.isLive);
            if(!isPlanned && video.isLive) {
                _containerDuration.visibility = GONE;
                _containerLive.visibility = VISIBLE;
                timeMeta = context.getString(R.string.live_capitalized)
            }
            else {
                _containerLive.visibility = GONE;
                _containerDuration.visibility = VISIBLE;
            }

            val timeBar = _timeBar
            if (timeBar != null) {
                if (shouldShowTimeBar) {
                    val historyPosition = StateHistory.instance.getHistoryPosition(video.url)
                    timeBar.visibility = if (historyPosition > 0) VISIBLE else GONE
                    timeBar.progress = historyPosition.toFloat() / video.duration.toFloat()
                } else {
                    timeBar.visibility = GONE
                }
            }
        }
        else {
            currentVideo = null;
            _imageVideo.setImageDrawable(null);
            _containerDuration.visibility = GONE;
            _containerLive.visibility = GONE;
            _timeBar?.visibility = GONE;
        }

        _textVideoMetadata.text = metadata + timeMeta;
    }

    open fun preview(video: IPlatformContentDetails?, paused: Boolean) {
        if(video == null)
            return;
        Logger.i(TAG, "Previewing");
        if(video !is IPlatformVideoDetails)
            throw IllegalStateException("Expected VideoDetails");

        if(_feedStyle == FeedStyle.THUMBNAIL)
            return;

        val exoPlayer = _exoPlayer ?: return;

        Log.v(TAG, "video preview start playing" + video.name);

        val playerVideoThumbnail = FutoThumbnailPlayer(context);
        playerVideoThumbnail.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        playerVideoThumbnail.setPlayer(exoPlayer);
        if(!exoPlayer.currentState.muted)
            playerVideoThumbnail.unmute();
        else
            playerVideoThumbnail.mute();

        playerVideoThumbnail.setTempDuration(video.duration, false);
        playerVideoThumbnail.setPreview(video);
        _playerContainer.addView(playerVideoThumbnail);
        _playerVideoThumbnail = playerVideoThumbnail;
    }
    fun stopPreview() {
        if(_feedStyle == FeedStyle.THUMBNAIL)
            return;
        Log.v(TAG, "video preview stopping=" + currentVideo?.name);

        val playerVideoThumbnail = _playerVideoThumbnail;
        if (playerVideoThumbnail != null) {
            playerVideoThumbnail.stop();
            playerVideoThumbnail.setPlayer(null);
            _playerContainer.removeView(playerVideoThumbnail);
            _playerVideoThumbnail = null;
        }

        Log.v(TAG, "video preview playing and made invisible" + currentVideo?.name)
    }
    fun pausePreview() {
        if(_feedStyle == FeedStyle.THUMBNAIL)
            return;
        Log.v(TAG, "video preview pausing " + currentVideo?.name)

        _playerVideoThumbnail?.pause();

        Log.v(TAG, "video preview paused " + currentVideo?.name)
    }
    fun resumePreview() {
        if(_feedStyle == FeedStyle.THUMBNAIL)
            return;
        Log.v(TAG, "video preview resuming " + currentVideo?.name)

        _playerVideoThumbnail?.play();

        Log.v(TAG, "video preview resumed" + currentVideo?.name)
    }


    //Events
    fun setMuteChangedListener(callback : (FutoThumbnailPlayer, Boolean) -> Unit) {
        _playerVideoThumbnail?.setMuteChangedListener(callback);
    }

    companion object {
        private val TAG = "VideoPreviewViewHolder"
    }
}
