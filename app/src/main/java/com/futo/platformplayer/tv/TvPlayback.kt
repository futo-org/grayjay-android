package com.futo.platformplayer.tv

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.futo.platformplayer.R
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.fragment.mainactivity.main.VideoDetailFragment
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuOverlay
import com.futo.platformplayer.views.video.FutoVideoPlayer

@UnstableApi
object TvPlayback {
    fun dispatchKeyEvent(fragment: VideoDetailFragment, event: KeyEvent): Boolean {
        val detail = fragment._viewDetail ?: return false
        if (!TvMode.isEnabled(detail.context) ||
            fragment.state == VideoDetailFragment.State.CLOSED ||
            StateCasting.instance.isCasting)
            return false
        val player = detail.findViewById<FutoVideoPlayer>(R.id.videodetail_player)
        val exoPlayer = player.exoPlayer?.player ?: return false
        val mediaAction: (() -> Unit)? = when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> ({
                    if (exoPlayer.playWhenReady) player.pause() else play(player)
                })
            KeyEvent.KEYCODE_MEDIA_PLAY -> ({ play(player) })
            KeyEvent.KEYCODE_MEDIA_PAUSE -> ({ player.pause() })
            KeyEvent.KEYCODE_MEDIA_STOP -> ({ fragment.closeVideoDetails() })
            KeyEvent.KEYCODE_MEDIA_NEXT -> ({ fragment.nextVideo() })
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> ({ fragment.previousVideo() })
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> ({ seek(player, 10_000) })
            KeyEvent.KEYCODE_MEDIA_REWIND -> ({ seek(player, -10_000) })
            else -> null
        }
        if (mediaAction != null) {
            if (event.action == KeyEvent.ACTION_DOWN &&
                !event.isCanceled &&
                (event.repeatCount == 0 ||
                    event.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ||
                    event.keyCode == KeyEvent.KEYCODE_MEDIA_REWIND))
                mediaAction()
            return true
        }
        if (fragment.state == VideoDetailFragment.State.MINIMIZED &&
            player.isFocused &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) fragment.maximizeVideoDetail()
            return true
        }
        if (fragment.state != VideoDetailFragment.State.MAXIMIZED || hasOverlay(detail)) return false
        val controls = player.findViewById<View>(R.id.layout_controls)
        val controlsVisible = controls.isShown
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                if (controlsVisible) {
                    player.hideControls(false)
                    player.isFocusable = true
                    player.requestFocus()
                } else if (fragment.isFullscreen) {
                    detail.setFullscreen(false)
                } else return false
            }
            return controlsVisible || fragment.isFullscreen || event.action == KeyEvent.ACTION_UP
        }
        val navigation = event.keyCode in
            setOf(
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT)
        if (!navigation || !fragment.isFullscreen) return false
        if (!controlsVisible) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                player.gestureControl.showControls()
                focusPlayPause(player)
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) player.gestureControl.restartHideJob()
        return false
    }

    internal fun focusPlayPause(player: FutoVideoPlayer) {
        val controller = player.findViewById<ViewGroup>(
            if (player.isFullScreen) R.id.video_player_controller_fullscreen else R.id.video_player_controller)
        val button = if (player.exoPlayer?.player?.playWhenReady == true) R.id.button_pause else R.id.button_play
        controller.findViewById<View>(button).requestFocus()
    }

    private fun play(player: FutoVideoPlayer) {
        if (player.exoPlayer?.player?.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }

    private fun seek(player: FutoVideoPlayer, offset: Long) {
        val exoPlayer = player.exoPlayer?.player ?: return
        if (!exoPlayer.isCurrentMediaItemSeekable) return
        val target = (player.position + offset).coerceAtLeast(0)
        player.seekTo(if (player.duration > 0) target.coerceAtMost(player.duration) else target)
    }

    private fun hasOverlay(view: View): Boolean {
        if (!view.isShown) return false
        if (view is SlideUpMenuOverlay && view.isVisible) return true
        return view is ViewGroup && view.children.any(::hasOverlay)
    }
}
