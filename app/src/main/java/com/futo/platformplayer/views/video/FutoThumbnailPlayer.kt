package com.futo.platformplayer.views.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.toHumanTime
import com.futo.platformplayer.video.PlayerManager


class FutoThumbnailPlayer : FutoVideoPlayerBase {
    companion object {
        private const val TAG = "FutoThumbnailVideoPlayer"
        private const val PLAYER_STATE_NAME : String = "ThumbnailPlayer";
    }

    //Views
    private val videoView : PlayerView;
    private val videoControls : PlayerControlView;
    private val buttonMute : ImageButton;
    private val buttonUnMute : ImageButton;

    private val textDurationInverse : TextView;
    private val containerDuration : LinearLayout;
    private val containerLive : LinearLayout;

    //Events
    private val _evMuteChanged = mutableListOf<(FutoThumbnailPlayer, Boolean)->Unit>();
    private val _loadArtwork = object: CustomTarget<Bitmap>() {
        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
            setArtwork(BitmapDrawable(resources, resource));
        }
        override fun onLoadCleared(placeholder: Drawable?) {
            setArtwork(null);
        }
    }


    @OptIn(UnstableApi::class)
    constructor(context: Context, attrs: AttributeSet? = null) : super(PLAYER_STATE_NAME, context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.thumbnail_video_view, this, true);

        videoView = findViewById(R.id.video_player);
        videoControls = findViewById(R.id.video_player_controller);
        buttonMute = videoControls.findViewById(R.id.thumbnail_player_mute);
        buttonUnMute = videoControls.findViewById(R.id.thumbnail_player_unmute);
        textDurationInverse = videoControls.findViewById(R.id.exo_duration_inverse);
        containerDuration = videoControls.findViewById(R.id.exo_duration_container);
        containerLive = videoControls.findViewById(R.id.exo_live_container);

        videoControls.setProgressUpdateListener { position, _ ->
            if(position < 0)
                textDurationInverse.visibility = View.INVISIBLE;
            else
                textDurationInverse.visibility = View.VISIBLE;
            val newText = Math.max(0, ((exoPlayer?.player?.duration ?: 0) - position)).toHumanTime(true);
            if(newText != "0:00")
                textDurationInverse.text = newText;
        }

        buttonMute.setOnClickListener {
            mute();
        }
        buttonUnMute.setOnClickListener {
            unmute();
        }
    }

    fun setLive(live: Boolean) {
        if(live) {
            containerDuration.visibility = GONE;
            containerLive.visibility = VISIBLE;
        }
        else {
            containerLive.visibility = GONE;
            containerDuration.visibility = VISIBLE;
        }
    }

    @OptIn(UnstableApi::class)
    fun setPlayer(player: PlayerManager?){
        changePlayer(player);
        player?.attach(videoView, PLAYER_STATE_NAME);
        videoControls.player = player?.player;
    }
    fun setTempDuration(duration : Long, ms : Boolean) {
        textDurationInverse.text = duration.toHumanTime(ms);
    }

    //Controls
    fun mute(){
        this.exoPlayer?.setMuted(true);
        this.buttonMute.visibility = View.GONE;
        this.buttonUnMute.visibility = View.VISIBLE;
        _evMuteChanged.forEach { it(this, false) };
    }
    fun unmute(){
        this.exoPlayer?.setMuted(false);
        this.buttonMute.visibility = View.VISIBLE;
        this.buttonUnMute.visibility = View.GONE;
        _evMuteChanged.forEach { it(this, true) };
    }


    //Events
    fun setMuteChangedListener(callback : (FutoThumbnailPlayer, Boolean) -> Unit) {
        _evMuteChanged.add(callback);
    }

    fun setPreview(video: IPlatformVideoDetails) {
        if (video.live != null) {
            setSource(video.live, null,true, false);
        } else {
            val videoSource = VideoHelper.selectBestVideoSource(video.video, Settings.instance.playback.getPreferredPreviewQualityPixelCount(), PREFERED_VIDEO_CONTAINERS);
            val audioSource = VideoHelper.selectBestAudioSource(video.video, PREFERED_AUDIO_CONTAINERS, Settings.instance.playback.getPrimaryLanguage(context));
            if (videoSource == null && audioSource != null) {
                val thumbnail = video.thumbnails.getHQThumbnail();
                if (!thumbnail.isNullOrBlank()) {
                    Glide.with(videoView).asBitmap().load(thumbnail).into(_loadArtwork);
                } else {
                    Glide.with(videoView).clear(_loadArtwork);
                    setArtwork(null);
                }
            } else {
                Glide.with(videoView).clear(_loadArtwork);
            }

            setSource(videoSource, audioSource,true, false);
        }
    }
    override fun onSourceChanged(videoSource: IVideoSource?, audioSource: IAudioSource?, resume: Boolean) {

    }

    @OptIn(UnstableApi::class)
    fun setArtwork(drawable: Drawable?) {
        if (drawable != null) {
            videoView.defaultArtwork = drawable;
            videoView.artworkDisplayMode = PlayerView.ARTWORK_DISPLAY_MODE_FILL;
        } else {
            videoView.defaultArtwork = null;
            videoView.artworkDisplayMode = PlayerView.ARTWORK_DISPLAY_MODE_OFF;
        }
    }
}