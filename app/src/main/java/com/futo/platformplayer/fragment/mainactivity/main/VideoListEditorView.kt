package com.futo.platformplayer.fragment.mainactivity.main

import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
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
            _buttonShare?.visibility = View.GONE;

        buttonPlayAll.setOnClickListener { onPlayAllClick(); };
        buttonShuffle.setOnClickListener { onShuffleClick(); };

        if (canEdit())
            _buttonEdit.setOnClickListener { onEditClick(); };
        else
            _buttonEdit.visibility = View.GONE;

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


    protected fun setName(name: String?) {
        _textName.text = name ?: "";
    }

    protected fun setVideoCount(videoCount: Int = -1) {
        _textMetadata.text = if (videoCount == -1) "" else "${videoCount} videos";
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
            _textMetadata.text = "0 videos";
            if(_imagePlaylistThumbnail != null) {
                Glide.with(_imagePlaylistThumbnail)
                    .load(R.drawable.placeholder_video_thumbnail)
                    .into(_imagePlaylistThumbnail);
            }
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