@file:Suppress("DEPRECATION")

package com.futo.platformplayer.views.video

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.toHumanTime
import com.futo.platformplayer.video.PlayerManager
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.ui.StyledPlayerView


class FutoThumbnailPlayer : FutoVideoPlayerBase {
    companion object {
        private const val TAG = "FutoThumbnailVideoPlayer"
        private const val PLAYER_STATE_NAME : String = "ThumbnailPlayer";
    }

    //Views
    private val videoView : StyledPlayerView;
    private val videoControls : PlayerControlView;
    private val buttonMute : ImageButton;
    private val buttonUnMute : ImageButton;

    private val textDurationInverse : TextView;
    private val containerDuration : LinearLayout;
    private val containerLive : LinearLayout;

    //Events
    private val _evMuteChanged = mutableListOf<(FutoThumbnailPlayer, Boolean)->Unit>();


    constructor(context : Context, attrs: AttributeSet? = null) : super(PLAYER_STATE_NAME, context, attrs) {
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

    fun setLive(live : Boolean) {
        if(live) {
            containerDuration.visibility = GONE;
            containerLive.visibility = VISIBLE;
        }
        else {
            containerLive.visibility = GONE;
            containerDuration.visibility = VISIBLE;
        }
    }

    fun setPlayer(player : PlayerManager?){
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
        val videoSource = VideoHelper.selectBestVideoSource(video.video, Settings.instance.playback.getPreferredPreviewQualityPixelCount(), PREFERED_VIDEO_CONTAINERS);
        val audioSource = VideoHelper.selectBestAudioSource(video.video, PREFERED_AUDIO_CONTAINERS, Settings.instance.playback.getPrimaryLanguage(context));
        setSource(videoSource, audioSource,true, false);
    }
    override fun onSourceChanged(videoSource: IVideoSource?, audioSource: IAudioSource?, resume: Boolean) {

    }
}