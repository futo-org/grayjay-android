package com.futo.platformplayer.tv.playlists

import android.app.Dialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.tv.views.TvMenuDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class TvPlaylistActions private constructor(
    fragment: Fragment,
    private val changed: (String?) -> Unit
) : DefaultLifecycleObserver {
    private val owner = fragment.viewLifecycleOwner
    private val context = fragment.requireContext()
    private val state get() = StatePlaylists.instance
    private var dialog: Dialog? = null
    private var busy = false

    override fun onStop(owner: LifecycleOwner) {
        dialog?.dismiss()
    }

    private fun show(next: Dialog) {
        owner.lifecycle.addObserver(this)
        val previous = dialog
        dialog = next
        previous?.dismiss()
        next.setOnDismissListener {
            if (dialog === next) {
                dialog = null
                if (!busy) owner.lifecycle.removeObserver(this)
            }
        }
        next.show()
    }

    private fun options(id: String) {
        val playlist = state.getPlaylist(id)
        show(TvMenuDialog(context).apply {
            panel.title(context.getString(R.string.tv_playlist_options))
            if (playlist == null) panel.message(context.getString(R.string.tv_playlist_missing))
            else {
                val menu = panel.menu()
                menu.addView(panel.option(context.getString(R.string.tv_playlist_rename), playlist.name) {
                    name(id, playlist.name)
                }.apply {
                    this.id = R.id.tv_playlist_rename
                    requestFocus()
                })
                menu.addView(panel.option(context.getString(R.string.tv_playlist_delete)) {
                    show(TvMenuDialog(context).apply {
                        confirm(
                            context.getString(R.string.tv_playlist_delete),
                            context.getString(R.string.tv_playlist_delete_hint, playlist.name),
                            context.getString(R.string.delete)
                        ) {
                            val progress = TvMenuDialog(context).apply {
                                panel.message(context.getString(R.string.tv_loading))
                            }
                            show(progress)
                            change {
                                state.getPlaylist(id)?.let { state.removePlaylist(it, true) }
                                null
                            }
                        }
                    })
                }.apply { this.id = R.id.tv_playlist_delete })
            }
        })
    }

    private fun name(id: String? = null, initial: String = "", videos: List<IPlatformVideo> = emptyList()) {
        val title = context.getString(if (id == null) R.string.new_playlist else R.string.tv_playlist_rename)
        show(TvPlaylistNameDialog(
            context,
            title,
            initial,
            context.getString(if (id == null) R.string.create else R.string.save)
        ) { value ->
            change {
                if (id == null) {
                    val playlist = Playlist(value, videos.map { SerializedPlatformVideo.fromVideo(it) })
                    // The existing create API saves asynchronously; make the new item visible before reloading the TV list.
                    state.playlistStore.save(playlist, true)
                    state.createOrUpdatePlaylist(playlist, true)
                    playlist.id
                } else synchronized(state.playlistStore) {
                    val playlist = checkNotNull(state.getPlaylist(id)) { "Playlist no longer exists" }
                    playlist.name = value
                    state.createOrUpdatePlaylist(playlist, true)
                    id
                }
            }
        })
    }

    private fun change(action: () -> String?) {
        if (busy) return
        busy = true
        (dialog as? TvPlaylistNameDialog)?.setSaving(true)
        owner.lifecycleScope.launch {
            try {
                val id = withContext(NonCancellable + Dispatchers.IO) { action() }
                dialog?.dismiss()
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) changed(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w("TvPlaylistActions", "Could not update playlist", error)
                val message = context.getString(R.string.tv_playlist_manage_error)
                (dialog as? TvPlaylistNameDialog)?.showError(message)
                (dialog as? TvMenuDialog)?.panel?.message(message)
            } finally {
                busy = false
                (dialog as? TvPlaylistNameDialog)?.setSaving(false)
                if (dialog == null) owner.lifecycle.removeObserver(this@TvPlaylistActions)
            }
        }
    }

    companion object {
        fun create(fragment: Fragment, videos: List<IPlatformVideo> = emptyList(), saved: (String) -> Unit) {
            TvPlaylistActions(fragment, { id -> id?.let(saved) }).name(videos = videos)
        }

        fun manage(fragment: Fragment, id: String, changed: (String?) -> Unit) {
            TvPlaylistActions(fragment, changed).options(id)
        }
    }
}
