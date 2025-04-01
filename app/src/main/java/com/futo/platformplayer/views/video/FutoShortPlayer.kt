package com.futo.platformplayer.views.video

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.LinearInterpolator
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePlayer

@UnstableApi
class FutoShortPlayer(context: Context, attrs: AttributeSet? = null) :
    FutoVideoPlayerBase(PLAYER_STATE_NAME, context, attrs) {

    companion object {
        private const val TAG = "FutoShortVideoPlayer"
        private const val PLAYER_STATE_NAME: String = "ShortPlayer"
    }

    private var playerAttached = false
//        private set;

    private val videoView: PlayerView
    private val progressBar: DefaultTimeBar

    private val loadArtwork = object : CustomTarget<Bitmap>() {
        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
            setArtwork(BitmapDrawable(resources, resource))
        }

        override fun onLoadCleared(placeholder: Drawable?) {
            setArtwork(null)
        }
    }

    private val player = StatePlayer.instance.getShortPlayerOrCreate(context)

    private var progressAnimator: ValueAnimator = createProgressBarAnimator()

    private var playerEventListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED
                )
            ) {
                if (player.duration >= 0) {
                    progressAnimator.duration = player.duration
                    setProgressBarDuration(player.duration)
                    progressAnimator.currentPlayTime = player.currentPosition
                }

                if (player.isPlaying) {
                    if (progressAnimator.isPaused){
                        progressAnimator.resume()
                    }
                    else if (!progressAnimator.isStarted) {
                        progressAnimator.start()
                    }
                } else {
                    if (progressAnimator.isRunning) {
                        progressAnimator.pause()
                    }
                }
            }
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_short_player, this, true)
        videoView = findViewById(R.id.video_player)
        progressBar = findViewById(R.id.video_player_progress_bar)

        player.player.repeatMode = Player.REPEAT_MODE_ONE

        progressBar.addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                if (progressAnimator.isRunning) {
                    progressAnimator.pause()
                }
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {}

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                if (canceled) {
                    progressAnimator.currentPlayTime = player.player.currentPosition
                    progressAnimator.resume()
                    return
                }

                // the progress bar should never be available to the user without the player being attached to this view
                assert(playerAttached)
                seekTo(position)
            }
        })
    }

    @OptIn(UnstableApi::class)
    private fun createProgressBarAnimator(): ValueAnimator {
        return ValueAnimator.ofFloat(0f, 1f).apply {
            interpolator = LinearInterpolator()

            addUpdateListener { animation ->
                progressBar.setPosition(animation.currentPlayTime)
            }
        }
    }

    fun setProgressBarDuration(duration: Long) {
        progressBar.setDuration(duration)
    }

    /**
     * Attaches this short player instance to the exo player instance for shorts
     */
    fun attach() {
        // connect the exo player for shorts to the view for this instance
        player.attach(videoView, PLAYER_STATE_NAME)

        // direct the base player what exo player instance to use
        changePlayer(player)

        playerAttached = true

        player.player.addListener(playerEventListener)
    }

    fun detach() {
        playerAttached = false
        player.player.removeListener(playerEventListener)
        player.detach()
    }

    fun setPreview(video: IPlatformVideoDetails) {
        if (video.live != null) {
            setSource(video.live, null, play = true, keepSubtitles = false)
        } else {
            val videoSource =
                VideoHelper.selectBestVideoSource(video.video, Settings.instance.playback.getPreferredPreviewQualityPixelCount(), PREFERED_VIDEO_CONTAINERS)
            val audioSource =
                VideoHelper.selectBestAudioSource(video.video, PREFERED_AUDIO_CONTAINERS, Settings.instance.playback.getPrimaryLanguage(context))
            if (videoSource == null && audioSource != null) {
                val thumbnail = video.thumbnails.getHQThumbnail()
                if (!thumbnail.isNullOrBlank()) {
                    Glide.with(videoView).asBitmap().load(thumbnail).into(loadArtwork)
                } else {
                    Glide.with(videoView).clear(loadArtwork)
                    setArtwork(null)
                }
            } else {
                Glide.with(videoView).clear(loadArtwork)
            }

            setSource(videoSource, audioSource, play = true, keepSubtitles = false)
        }
    }

    @OptIn(UnstableApi::class)
    fun setArtwork(drawable: Drawable?) {
        if (drawable != null) {
            videoView.artworkDisplayMode = PlayerView.ARTWORK_DISPLAY_MODE_FILL
            videoView.defaultArtwork = drawable
        } else {
            videoView.artworkDisplayMode = PlayerView.ARTWORK_DISPLAY_MODE_OFF
            videoView.defaultArtwork = null
        }
    }

    fun getPlaybackRate(): Float {
        return exoPlayer?.player?.playbackParameters?.speed ?: 1.0f
    }

    fun setPlaybackRate(playbackRate: Float) {
        val exoPlayer = exoPlayer?.player
        Logger.i(TAG, "setPlaybackRate playbackRate=$playbackRate exoPlayer=${exoPlayer}")

        val param = PlaybackParameters(playbackRate)
        exoPlayer?.playbackParameters = param
    }

    // TODO remove stub
    fun hideControls(stub: Boolean) {

    }
}
