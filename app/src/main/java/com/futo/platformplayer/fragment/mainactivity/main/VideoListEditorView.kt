package com.futo.platformplayer.fragment.mainactivity.main

import android.graphics.drawable.Animatable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.assume
import com.futo.platformplayer.downloads.VideoDownload
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.toHumanDuration
import com.futo.platformplayer.toHumanTime
import com.futo.platformplayer.views.lists.VideoListEditorView

abstract class VideoListEditorView : LinearLayout {
    private var _videoListEditorView: VideoListEditorView;
    private var _imagePlaylistThumbnail: ImageView;
    private var _textName: TextView;
    private var _textMetadata: TextView;
    private var _loaderOverlay: FrameLayout;
    private var _imageLoader: ImageView;
    protected var overlayContainer: FrameLayout
        private set;
    protected var _buttonDownload: ImageButton;
    private var _buttonShare: ImageButton;
    private var _buttonEdit: ImageButton;

    private var _onShare: (()->Unit)? = null;

    constructor(inflater: LayoutInflater) : super(inflater.context) {
        inflater.inflate(R.layout.fragment_video_list_editor, this);

        val videoListEditorView = findViewById<VideoListEditorView>(R.id.video_list_editor);
        _textName = findViewById(R.id.text_name);
        _textMetadata = findViewById(R.id.text_metadata);
        _imagePlaylistThumbnail = findViewById(R.id.image_playlist_thumbnail);
        _loaderOverlay = findViewById(R.id.layout_loading_overlay);
        _imageLoader = findViewById(R.id.image_loader);
        overlayContainer = findViewById(R.id.overlay_container);
        val buttonPlayAll = findViewById<LinearLayout>(R.id.button_play_all);
        val buttonShuffle = findViewById<LinearLayout>(R.id.button_shuffle);
        _buttonEdit = findViewById(R.id.button_edit);
        _buttonDownload = findViewById(R.id.button_download);
        _buttonDownload.visibility = View.GONE;

        _buttonShare = findViewById(R.id.button_share);
        val onShare = _onShare;
        if(onShare != null) {
            _buttonShare.setOnClickListener { onShare.invoke() };
            _buttonShare.visibility = View.VISIBLE;
        }
        else
            _buttonShare.visibility = View.GONE;

        buttonPlayAll.setOnClickListener { onPlayAllClick(); };
        buttonShuffle.setOnClickListener { onShuffleClick(); };

        _buttonEdit.setOnClickListener { onEditClick(); };
        setButtonDownloadVisible(canEdit());

        videoListEditorView.onVideoOrderChanged.subscribe(::onVideoOrderChanged);
        videoListEditorView.onVideoRemoved.subscribe(::onVideoRemoved);
        videoListEditorView.onVideoClicked.subscribe(::onVideoClicked);

        _videoListEditorView = videoListEditorView;
    }

    fun setOnShare(onShare: (()-> Unit)? = null) {
        _onShare = onShare;
        _buttonShare.setOnClickListener {
            onShare?.invoke();
        };
        _buttonShare.visibility = View.VISIBLE;
    }

    open fun canEdit(): Boolean { return false; }
    open fun onPlayAllClick() { }
    open fun onShuffleClick() { }
    open fun onEditClick() { }
    open fun onVideoRemoved(video: IPlatformVideo) {}
    open fun onVideoOrderChanged(videos : List<IPlatformVideo>) {}
    open fun onVideoClicked(video: IPlatformVideo) {

    }

    protected fun updateDownloadState(groupType: String, playlistId: String, onDownload: ()->Unit) {
        //val playlist = _playlist ?: return;
        val isDownloading = StateDownloads.instance.getDownloading().any { it.groupType == groupType && it.groupID == playlistId };
        val isDownloaded = StateDownloads.instance.isPlaylistCached(playlistId);

        val dp10 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics);

        if(isDownloaded && !isDownloading)
            _buttonDownload.setBackgroundResource(R.drawable.background_button_round_green);
        else
            _buttonDownload.setBackgroundResource(R.drawable.background_button_round);

        if(isDownloading) {
            _buttonDownload.setImageResource(R.drawable.ic_loader_animated);
            _buttonDownload.drawable.assume<Animatable, Unit> { it.start() };
            _buttonDownload.setOnClickListener {
                UIDialogs.showConfirmationDialog(context, context.getString(R.string.are_you_sure_you_want_to_delete_the_downloaded_videos), {
                    StateDownloads.instance.deleteCachedPlaylist(playlistId);
                });
            }
        }
        else if(isDownloaded) {
            _buttonDownload.setImageResource(R.drawable.ic_download_off);
            _buttonDownload.setOnClickListener {
                UIDialogs.showConfirmationDialog(context, context.getString(R.string.are_you_sure_you_want_to_delete_the_downloaded_videos), {
                    StateDownloads.instance.deleteCachedPlaylist(playlistId);
                });
            }
        }
        else {
            _buttonDownload.setImageResource(R.drawable.ic_download);
            _buttonDownload.setOnClickListener {
                onDownload();
                //UISlideOverlays.showDownloadPlaylistOverlay(playlist, overlayContainer);
            }
        }
        _buttonDownload.setPadding(dp10.toInt());
    }

    protected fun setName(name: String?) {
        _textName.text = name ?: "";
    }

    protected fun setMetadata(videoCount: Int = -1, duration: Long = -1) {
        val parts = mutableListOf<String>()
        if(videoCount >= 0)
            parts.add("${videoCount} " + context.getString(R.string.videos));
        if(duration > 0)
            parts.add("${duration.toHumanDuration(false)} ");

        _textMetadata.text = parts.joinToString(" • ");
    }

    protected fun setVideos(videos: List<IPlatformVideo>?, canEdit: Boolean) {
        if (videos != null && videos.isNotEmpty()) {
            val video = videos.first();
            _imagePlaylistThumbnail.let {
                Glide.with(it)
                    .load(video.thumbnails.getHQThumbnail())
                    .placeholder(R.drawable.placeholder_video_thumbnail)
                    .crossfade()
                    .into(it);
            };
        } else {
            _textMetadata.text = "0 " + context.getString(R.string.videos);
            Glide.with(_imagePlaylistThumbnail)
                .load(R.drawable.placeholder_video_thumbnail)
                .into(_imagePlaylistThumbnail)
        }

        _videoListEditorView.setVideos(videos, canEdit);
    }

    protected fun setButtonDownloadVisible(isVisible: Boolean) {
        _buttonDownload.visibility = if (isVisible) View.VISIBLE else View.GONE;
    }

    protected fun setButtonEditVisible(isVisible: Boolean) {
        _buttonEdit.visibility = if (isVisible) View.VISIBLE else View.GONE;
    }

    protected fun setLoading(isLoading: Boolean) {
        if(isLoading){
            (_imageLoader.drawable as Animatable?)?.start()
            _loaderOverlay.visibility = View.VISIBLE;
        }
        else {
            _loaderOverlay.visibility = View.GONE;
            (_imageLoader.drawable as Animatable?)?.stop()
        }
    }
}