package com.futo.platformplayer.tv.views

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.tv.playlists.TvPlaylistActions
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvUi.lines
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class TvVideoActions private constructor(
    private val fragment: Fragment,
    private val video: IPlatformVideo,
    private val openChannel: () -> Unit
) : DefaultLifecycleObserver {
    private val owner = fragment.viewLifecycleOwner
    private val context = fragment.requireContext()
    private val dialog = TvMenuDialog(context)
    private val image = TvUi.image(context)
    private val requests = Glide.with(fragment)
    private var job: Job? = null
    private var busy = false

    private val panel
        get() = dialog.panel

    private val state
        get() = StatePlaylists.instance

    init {
        owner.lifecycle.addObserver(this)
        dialog.setOnDismissListener {
            job?.cancel()
            requests.clear(image)
            owner.lifecycle.removeObserver(this)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        dialog.dismiss()
    }

    private fun main() {
        panel.title(context.getString(R.string.tv_watch_later_options))
        panel.onBack = null
        read({ state.getWatchLaterFromUrl(video.url) != null }, ::main) { saved ->
            val menu = panel.menu()
            val preview = TvUi.row(context).apply { setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(24)) }
            (image.parent as? ViewGroup)?.removeView(image)
            preview.addView(image, LinearLayout.LayoutParams(context.dp(128), context.dp(72)))
            requests.load(video.thumbnails.getHQThumbnail()).centerCrop().into(image)
            val text = TvUi.column(context)
            text.addView(TvUi.text(context, video.name, 15f).apply { lines(3) })
            text.addView(
                TvUi.text(context, video.author.name, 12f, true).apply {
                    lines(1)
                    setPadding(0, context.dp(6), 0, 0)
                })
            preview.addView(text, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(14) })
            menu.addView(preview)
            menu.addView(
                option(
                    context.getString(
                        if (saved) R.string.tv_watch_later_remove else R.string.tv_video_save_watch_later),
                    if (saved) R.drawable.ic_check else R.drawable.ic_schedule) {
                    change(::main) {
                        if (saved) {
                            state.removeFromWatchLater(video.url, true)
                            context.getString(R.string.tv_video_removed_watch_later)
                        } else {
                            state.addToWatchLater(SerializedPlatformVideo.fromVideo(video), true)
                            context.getString(R.string.tv_video_saved_watch_later)
                        }
                    }
                }.apply {
                    id = R.id.tv_video_watch_later
                    requestFocus()
                })
            menu.addView(
                option(context.getString(R.string.tv_video_add_playlist), R.drawable.ic_playlist_add, ::playlists)
                    .apply {
                        id = R.id.tv_video_add_playlist
                    })
            if (video.author.url.isNotBlank())
                menu.addView(
                    option(context.getString(R.string.tv_channel_title), R.drawable.ic_person) {
                        if (!busy) {
                            dialog.dismiss()
                            openChannel()
                        }
                    }.apply { id = R.id.tv_video_open_channel })
        }
    }

    private fun playlists() {
        if (busy) return
        panel.title(context.getString(R.string.tv_video_add_playlist), true)
        panel.onBack = {
            if (!busy) main()
            true
        }
        read(
            {
                state.getPlaylists()
                    .filter { it.id != StatePlaylists.LAST_QUEUE_PLAYLIST_ID }
                    .sortedByDescending { maxOf(it.datePlayed, it.dateUpdate, it.dateCreation) }
                    .map { Triple(it.id, it.name, it.videos.any { item -> item.url == video.url }) }
            },
            ::playlists) { playlists ->
            val menu = panel.menu()
            menu.addView(
                option(context.getString(R.string.new_playlist), R.drawable.ic_playlist_add) {
                    dialog.dismiss()
                    TvPlaylistActions.create(fragment, listOf(video)) { id ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.tv_video_added_playlist, state.getPlaylist(id)?.name.orEmpty()),
                            Toast.LENGTH_SHORT)
                            .show()
                    }
                }.apply {
                    id = R.id.tv_playlist_create
                    requestFocus()
                })
            playlists.forEach { (id, name, contains) ->
                menu.addView(
                    panel.option(
                        name,
                        if (contains)
                            context.getString(
                                if (Settings.instance.other.playlistAllowDups) R.string.tv_video_add_again
                                else R.string.tv_video_already_saved)
                        else null,
                        checked = contains) {
                        change(::playlists) {
                            synchronized(state.playlistStore) {
                                checkNotNull(state.getPlaylist(id)) { "Playlist no longer exists" }
                                if (state.addToPlaylist(id, video))
                                    context.getString(R.string.tv_video_added_playlist, name)
                                else context.getString(R.string.tv_video_already_saved)
                            }
                        }
                    }.apply { tag = "playlist:$id" })
            }
        }
    }

    private fun option(label: String, icon: Int, action: () -> Unit): View =
        panel.option(label, action = action).also { row ->
            (row as LinearLayout).addView(
                ImageView(context).apply {
                    setImageResource(icon)
                    setColorFilter(Color.WHITE)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                0,
                LinearLayout.LayoutParams(context.dp(24), context.dp(24)).apply { marginEnd = context.dp(16) })
        }

    private fun <T> read(load: () -> T, retry: () -> Unit, render: (T) -> Unit) {
        job?.cancel()
        panel.message(context.getString(R.string.tv_loading))
        job = owner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { load() }
                if (dialog.isShowing) render(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                error(error, retry)
            }
        }
    }

    private fun change(retry: () -> Unit, action: () -> String) {
        if (busy) return
        busy = true
        job = owner.lifecycleScope.launch {
            try {
                val message = withContext(NonCancellable + Dispatchers.IO) { action() }
                if (dialog.isShowing) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                error(error, retry)
            } finally {
                busy = false
            }
        }
    }

    private fun error(error: Exception, retry: () -> Unit) {
        Logger.w("TvVideoActions", "Video action failed", error)
        if (dialog.isShowing) panel.message(context.getString(R.string.tv_video_action_error), retry = retry)
    }

    companion object {
        fun show(fragment: Fragment, video: IPlatformVideo, openChannel: () -> Unit) {
            TvVideoActions(fragment, video, openChannel).apply {
                dialog.show()
                main()
            }
        }
    }
}
