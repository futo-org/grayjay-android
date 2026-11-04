package com.futo.platformplayer.tv.playlists

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.media3.ui.PlayerControlView
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.states.StatePlaylists

internal class TvPlaylistQueue(
    private val arguments: Bundle,
    saved: Bundle?,
    controls: PlayerControlView,
    private val play: (String) -> Unit
) {
    private val playlistId = arguments.getString("playlistId")
    private val type = if (playlistId == null) StatePlayer.TYPE_WATCHLATER else StatePlayer.TYPE_PLAYLIST
    private var current = arguments.getString("url")!!
    private var advancing = false
    private val previous: View
    private val next: View

    private val state
        get() = StatePlayer.instance

    init {
        if (state.getQueueType() != type ||
            state.getCurrentQueueItem()?.url != current ||
            (playlistId != null && state.playlistId != playlistId)) {
            val urls = saved?.getStringArrayList(KEY) ?: arguments.getStringArrayList(KEY).orEmpty()
            val stored = (if (playlistId == null) StatePlaylists.instance.getWatchLater()
                else StatePlaylists.instance.getPlaylist(playlistId)?.videos.orEmpty())
                .associateBy { it.url }
            val videos = urls.mapNotNull { stored[it] }
            val index = saved?.getInt("queuePosition", -1)?.takeIf { videos.getOrNull(it)?.url == current }
                ?: videos.indexOfFirst { it.url == current }.coerceAtLeast(0)
            prepare(videos, index, false, playlistId, arguments.getString("playlistName"))
        }
        val time = controls.findViewById<View>(androidx.media3.ui.R.id.exo_position)
        val row = time.parent as ViewGroup
        val buttons = LayoutInflater.from(controls.context).inflate(R.layout.tv_playlist_queue_controls, row, false)
        row.addView(buttons, row.indexOfChild(time))
        previous = buttons.findViewById(R.id.tv_queue_previous)
        next = buttons.findViewById(R.id.tv_queue_next)
        previous.setOnClickListener { previous() }
        next.setOnClickListener { next() }
        listOf(previous, next).forEach { button ->
            button.setOnFocusChangeListener { _, focused ->
                if (focused)
                    controls.findViewById<TextView>(R.id.tv_player_action_hint).text = button.contentDescription
            }
        }
        updateButtons()
    }

    fun loaded(video: IPlatformVideo) {
        current = video.url
        advancing = false
        state.setCurrentlyPlaying(video)
        updateButtons()
    }

    fun next(automatic: Boolean = false): Boolean {
        if (!active() || (!automatic && state.getNextQueueItem() == null)) return false
        if (automatic && playlistId == null && Settings.instance.playback.deleteFromWatchLaterAuto)
            StatePlaylists.instance.removeFromWatchLater(current)
        val video = state.nextQueueItem(withoutRemoval = !automatic, bypassVideoLoop = true)
        if (video == null) {
            updateButtons()
            return false
        }
        switch(video)
        return true
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_MEDIA_NEXT && event.keyCode != KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (event.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) next() else previous()
        }
        return true
    }

    fun save(out: Bundle) {
        out.putStringArrayList(KEY, ArrayList(state.getQueue().map { it.url }))
        out.putInt("queuePosition", state.getQueueProgress())
    }

    private fun active() =
        !advancing && state.getQueueType() == type && state.getCurrentQueueItem(false)?.url == current

    private fun previous() {
        if (!active() || state.getPrevQueueItem() == null) return
        state.prevQueueItem(withoutRemoval = true)?.let(::switch)
    }

    private fun switch(video: IPlatformVideo) {
        advancing = true
        current = video.url
        updateButtons()
        play(video.url)
    }

    private fun updateButtons() {
        listOf(previous to state.getPrevQueueItem(), next to state.getNextQueueItem()).forEach { (button, video) ->
            button.isEnabled = video != null && !advancing
            button.alpha = if (button.isEnabled) 1f else .35f
        }
    }

    companion object {
        const val KEY = "playlistQueue"

        fun start(
            videos: List<IPlatformVideo>,
            index: Int,
            shuffle: Boolean,
            playlistId: String? = null,
            name: String? = null
        ): Bundle {
            prepare(videos, index, shuffle, playlistId, name)
            val state = StatePlayer.instance
            return Bundle().apply {
                putString("url", state.getCurrentQueueItem()!!.url)
                putString("playlistId", playlistId)
                putString("playlistName", name)
                putStringArrayList(KEY, ArrayList(state.getQueue().map { it.url }))
            }
        }

        private fun prepare(videos: List<IPlatformVideo>, index: Int, shuffle: Boolean, id: String?, name: String?) {
            val state = StatePlayer.instance
            state.clearQueue()
            state.setQueueRepeat(false)
            if (id == null) {
                state.setQueue(videos, StatePlayer.TYPE_WATCHLATER)
                videos.getOrNull(index)?.let { state.setQueuePosition(it) }
            } else
                state.setPlaylist(
                    Playlist(id, name.orEmpty(), videos.map { SerializedPlatformVideo.fromVideo(it) }), index)
            if (shuffle) state.setQueueShuffle(true)
        }
    }
}
