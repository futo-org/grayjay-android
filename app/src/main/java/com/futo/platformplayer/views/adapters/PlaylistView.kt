package com.futo.platformplayer.views.adapters

import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.platform.PlatformIndicator


open class PlaylistView : LinearLayout {
    protected val _feedStyle : FeedStyle;

    protected val _imageThumbnail: ImageView
    protected val _imageChannel: ImageView?
    protected val _creatorThumbnail: CreatorThumbnail?
    protected val _imageNeopassChannel: ImageView?;
    protected val _platformIndicator: PlatformIndicator;
    protected val _textPlaylistName: TextView
    protected val _textVideoCount: TextView
    protected val _textVideoCountLabel: TextView;
    protected val _textPlaylistItems: TextView
    protected val _textChannelName: TextView
    protected var _neopassAnimator: ObjectAnimator? = null;

    private val _taskLoadValidClaims = TaskHandler<PlatformID, PolycentricCache.CachedOwnedClaims>(StateApp.instance.scopeGetter,
        { PolycentricCache.instance.getValidClaimsAsync(it).await() })
        .success { it -> updateClaimsLayout(it, animate = true) }
        .exception<Throwable> {
            Logger.w(TAG, "Failed to load claims.", it);
        };

    val onPlaylistClicked = Event1<IPlatformPlaylist>();
    val onChannelClicked = Event1<PlatformAuthorLink>();

    var currentPlaylist: IPlatformPlaylist? = null
        private set

    val content: IPlatformContent? get() = currentPlaylist;

    constructor(context: Context, feedStyle : FeedStyle) : super(context) {
        inflate(feedStyle);
        _feedStyle = feedStyle;

        _imageThumbnail = findViewById(R.id.image_thumbnail);
        _imageChannel = findViewById(R.id.image_channel_thumbnail);
        _creatorThumbnail = findViewById(R.id.creator_thumbnail);
        _platformIndicator = findViewById(R.id.thumbnail_platform);
        _textPlaylistName = findViewById(R.id.text_playlist_name);
        _textVideoCount = findViewById(R.id.text_video_count);
        _textVideoCountLabel = findViewById(R.id.text_video_count_label);
        _textChannelName = findViewById(R.id.text_channel_name);
        _textPlaylistItems = findViewById(R.id.text_playlist_items);
        _imageNeopassChannel = findViewById(R.id.image_neopass_channel);

        setOnClickListener { onOpenClicked()  };
        _imageChannel?.setOnClickListener { currentPlaylist?.let { onChannelClicked.emit(it.author) }  };
        _textChannelName.setOnClickListener { currentPlaylist?.let { onChannelClicked.emit(it.author) }  };
    }

    protected open fun inflate(feedStyle: FeedStyle) {
        inflate(context, when(feedStyle) {
            FeedStyle.PREVIEW -> R.layout.list_playlist_feed_preview
            else -> R.layout.list_playlist_feed
        }, this)
    }

    protected open fun onOpenClicked() {
        currentPlaylist?.let {
            onPlaylistClicked.emit(it);
        }
    }


    open fun bind(content: IPlatformContent) {
        _taskLoadValidClaims.cancel();

        if (content.author.id.claimType > 0) {
            val cachedClaims = PolycentricCache.instance.getCachedValidClaims(content.author.id);
            if (cachedClaims != null) {
                updateClaimsLayout(cachedClaims, animate = false);
            } else {
                updateClaimsLayout(null, animate = false);
                _taskLoadValidClaims.run(content.author.id);
            }
        } else {
            updateClaimsLayout(null, animate = false);
        }

        isClickable = true;

        _imageChannel?.let {
            if (content.author.thumbnail != null)
                Glide.with(it)
                    .load(content.author.thumbnail)
                    .placeholder(R.drawable.placeholder_channel_thumbnail)
                    .into(it)
            else
                Glide.with(it).load(R.drawable.placeholder_channel_thumbnail).into(it);
        };

        _imageChannel?.clipToOutline = true;

        _textPlaylistName.text = content.name;
        _textChannelName.text = content.author.name;
        _textPlaylistItems.text = ""; //TODO: Show items

        _platformIndicator.setPlatformFromClientID(content.id.pluginId);

        if(content is IPlatformPlaylist) {
            val playlist = content;

            currentPlaylist = playlist
            val thumbnail = playlist.thumbnail
            if(thumbnail != null)
                Glide.with(_imageThumbnail)
                    .load(thumbnail)
                    .placeholder(R.drawable.placeholder_video_thumbnail)
                    .crossfade()
                    .into(_imageThumbnail);
            else
                Glide.with(_imageThumbnail)
                    .load(R.drawable.placeholder_video_thumbnail)
                    .crossfade()
                    .into(_imageThumbnail);

            if(content.videoCount >= 0) {
                _textVideoCount.text = content.videoCount.toString();
                _textVideoCount.visibility = View.VISIBLE;
                _textVideoCountLabel.visibility = VISIBLE;
            }
            else {
                _textVideoCount.visibility = View.GONE;
                _textVideoCountLabel.visibility = GONE;
            }
        }
        else {
            currentPlaylist = null;
            _imageThumbnail.setImageResource(0);
        }
    }

    private fun updateClaimsLayout(claims: PolycentricCache.CachedOwnedClaims?, animate: Boolean) {
        _neopassAnimator?.cancel();
        _neopassAnimator = null;

        val firstClaim = claims?.ownedClaims?.firstOrNull();
        val harborAvailable = firstClaim != null
        if (harborAvailable) {
            _imageNeopassChannel?.visibility = View.VISIBLE
            if (animate) {
                _neopassAnimator = ObjectAnimator.ofFloat(_imageNeopassChannel, "alpha", 0.0f, 1.0f).setDuration(500)
                _neopassAnimator?.start()
            }
        } else {
            _imageNeopassChannel?.visibility = View.GONE
        }

        _creatorThumbnail?.setHarborAvailable(harborAvailable, animate, firstClaim?.system?.toProto())
    }

    companion object {
        private val TAG = "VideoPreviewViewHolder"
    }
}
