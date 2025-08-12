package com.futo.platformplayer.views.video

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.LinearInterpolator
import androidx.annotation.Dimension
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.video.PlayerManager

@UnstableApi
class FutoShortPlayer(context: Context, attrs: AttributeSet? = null) :
    FutoVideoPlayerBase(PLAYER_STATE_NAME, context, attrs) {

    companion object {
        private const val TAG = "FutoShortVideoPlayer"
        private const val PLAYER_STATE_NAME: String = "ShortPlayer"
    }

    private var playerAttached = false
    private val videoView: PlayerView
    private val progressBar: DefaultTimeBar
    private lateinit var player: PlayerManager
    private var progressAnimator: ValueAnimator = createProgressBarAnimator()

    val onPlaybackStateChanged = Event1<Int>();

    private var playerEventListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED
                )
            ) {
                progressAnimator.cancel()
                if (player.duration >= 0) {
                    progressAnimator.duration = player.duration
                    setProgressBarDuration(player.duration)
                    progressAnimator.currentPlayTime = player.currentPosition
                }

                if (player.isPlaying) {
                    progressAnimator.start()
                }
            }

            if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                onPlaybackStateChanged.emit(player.playbackState)
            }
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_short_player, this, true)
        videoView = findViewById(R.id.short_player_view)
        progressBar = findViewById(R.id.short_player_progress_bar)

        videoView.subtitleView?.setFixedTextSize(Dimension.SP, 18F);

        if (!isInEditMode) {
            player = StatePlayer.instance.getShortPlayerOrCreate(context)
            player.player.repeatMode = Player.REPEAT_MODE_ONE
        }

        progressBar.addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                progressAnimator.cancel()
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {}

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                if (canceled) {
                    progressAnimator.currentPlayTime = player.player.currentPosition
                    progressAnimator.duration = player.player.duration
                    progressAnimator.start()
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
}
