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
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.states.StatePlaylists
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class TvPlaylistQueue(
    private val arguments: Bundle,
    saved: Bundle?,
    private val controls: PlayerControlView,
    private val play: (String) -> Unit,
    private val scope: CoroutineScope
) {
    private val playlistId = arguments.getString("playlistId")
    private val playlistUrl = arguments.getString("playlistUrl")
    private val remote = playlistUrl?.let { TvRemotePlaylistQueue(it) }
    private val type = when {
        playlistUrl != null -> StatePlayer.TYPE_QUEUE
        playlistId != null -> StatePlayer.TYPE_PLAYLIST
        else -> StatePlayer.TYPE_WATCHLATER
    }
    private var current = arguments.getString("url")!!
    private var advancing = false
    private var loading: Job? = null
    private var pendingNext: Boolean? = null
    private val previous: View
    private val next: View

    private val state
        get() = StatePlayer.instance

    init {
        if (state.getQueueType() != type ||
            state.getCurrentQueueItem()?.url != current ||
            (playlistId != null && state.playlistId != playlistId)) {
            val urls = saved?.getStringArrayList(KEY) ?: arguments.getStringArrayList(KEY).orEmpty()
            val stored = (if (playlistUrl != null) emptyList()
                else if (playlistId == null) StatePlaylists.instance.getWatchLater()
                else StatePlaylists.instance.getPlaylist(playlistId)?.videos.orEmpty())
                .associateBy { it.url }
            val videos = urls.mapNotNull { stored[it] }
            val index = saved?.getInt("queuePosition", -1)?.takeIf { videos.getOrNull(it)?.url == current }
                ?: videos.indexOfFirst { it.url == current }.coerceAtLeast(0)
            prepare(videos, index, false, playlistId, arguments.getString("playlistName"), playlistUrl)
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
        loadMore()
    }

    fun loaded(video: IPlatformVideo) {
        current = video.url
        advancing = false
        state.setCurrentlyPlaying(video)
        updateButtons()
        if (state.getQueueLength() - state.getQueueProgress() < 4) loadMore()
    }

    fun next(automatic: Boolean = false): Boolean {
        if (pendingNext != null) return true
        if (!advancing && remote?.exhausted == false && (!active() || state.getNextQueueItem() == null)) {
            pendingNext = automatic
            updateButtons()
            loadMore()
            return true
        }
        if (!active() || (!automatic && state.getNextQueueItem() == null)) return false
        if (automatic && type == StatePlayer.TYPE_WATCHLATER && Settings.instance.playback.deleteFromWatchLaterAuto)
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
            button.isEnabled = (video != null || (button == next && remote?.exhausted == false)) &&
                !advancing && pendingNext == null
            button.alpha = if (button.isEnabled) 1f else .35f
        }
    }

    private fun loadMore() {
        val remote = remote ?: return
        if (remote.exhausted || loading?.isActive == true) return
        loading = scope.launch {
            try {
                do {
                    val page = remote.next()
                    val videos = state.getQueue().toMutableList()
                    val urls = videos.mapTo(hashSetOf()) { it.url }
                    videos.addAll(page.filter { urls.add(it.url) })
                    val position = videos.indexOfFirst { it.url == current }
                    state.setQueueWithPosition(videos, type, position)
                } while (!remote.exhausted && (!active() || state.getNextQueueItem() == null))
                val automatic = pendingNext
                pendingNext = null
                if (automatic != null && !next(automatic)) controls.show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w("TvPlaylistQueue", "Failed to load more playlist videos", error)
                pendingNext = null
                controls.show()
                UIDialogs.toast(controls.context, controls.context.getString(R.string.tv_error), false)
            } finally {
                loading = null
                updateButtons()
            }
        }
    }

    companion object {
        const val KEY = "playlistQueue"

        fun start(
            videos: List<IPlatformVideo>,
            index: Int,
            shuffle: Boolean,
            playlistId: String? = null,
            name: String? = null,
            playlistUrl: String? = null
        ): Bundle {
            prepare(videos, index, shuffle, playlistId, name, playlistUrl)
            val state = StatePlayer.instance
            return Bundle().apply {
                putString("url", state.getCurrentQueueItem()!!.url)
                putString("playlistId", playlistId)
                putString("playlistName", name)
                putString("playlistUrl", playlistUrl)
                putStringArrayList(KEY, ArrayList(state.getQueue().map { it.url }))
            }
        }

        private fun prepare(
            videos: List<IPlatformVideo>, index: Int, shuffle: Boolean, id: String?, name: String?, playlistUrl: String?
        ) {
            val state = StatePlayer.instance
            state.clearQueue()
            state.setQueueRepeat(false)
            val ordered = if (shuffle) videos.shuffled() else videos
            val position = if (shuffle) 0 else index
            if (id == null) {
                state.setQueue(ordered, if (playlistUrl == null) StatePlayer.TYPE_WATCHLATER else StatePlayer.TYPE_QUEUE, name)
                ordered.getOrNull(position)?.let { state.setQueuePosition(it) }
            } else
                state.setPlaylist(
                    Playlist(id, name.orEmpty(), ordered.map { SerializedPlatformVideo.fromVideo(it) }), position)
            if (shuffle) state.setQueueShuffle(true)
        }
    }
}
