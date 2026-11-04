package com.futo.platformplayer.tv.fragments

import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.tv.TvActivity
import com.futo.platformplayer.tv.playlists.TvPlaylistActions
import com.futo.platformplayer.tv.playlists.TvPlaylistQueue
import com.futo.platformplayer.tv.playlists.TvSavedPlaylistStore
import com.futo.platformplayer.tv.playlists.TvSavedVideosAdapter
import com.futo.platformplayer.tv.views.TvMenuDialog
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class TvSavedPlaylistFragment : Fragment() {
    private var videos = emptyList<SerializedPlatformVideo>()
    private var selectedUrl: String? = null
    private var selectedPosition = 0
    private var restore = false
    private var changing = false
    private var job: Job? = null
    private var dialog: Dialog? = null
    private lateinit var list: RecyclerView
    private lateinit var adapter: TvSavedVideosAdapter
    private lateinit var cover: ImageView
    private lateinit var images: RequestManager
    private lateinit var metadata: TextView
    private lateinit var playAll: View
    private lateinit var shuffle: View
    private lateinit var heading: TextView
    private lateinit var message: LinearLayout

    internal var store: TvSavedPlaylistStore = TvSavedPlaylistStore.platform()

    private val host
        get() = requireActivity() as TvActivity

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        arguments?.getString("playlistId")?.let { store = TvSavedPlaylistStore.playlist(it) }
        selectedUrl = state?.getString("selected")
        selectedPosition = state?.getInt("position") ?: 0
        restore = selectedUrl != null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val c = requireContext()
        val root = TvUi.row(c).apply {
            gravity = Gravity.TOP
            setPadding(c.dp(28), c.dp(28), c.dp(24), c.dp(20))
        }
        val summary = TvUi.column(c)
        cover = TvUi.image(c).apply { id = R.id.tv_watch_later_cover }
        images = Glide.with(cover)
        summary.addView(cover, LinearLayout.LayoutParams(-1, c.dp(144)))
        heading = TvUi.heading(c, arguments?.getString("name") ?: getString(R.string.tv_library)).apply {
            textSize = 28f
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, c.dp(22), 0, 0)
        }
        summary.addView(heading)
        metadata = TvUi.text(c, getString(R.string.tv_loading), 13f, true).apply {
            id = R.id.tv_watch_later_count
            setPadding(0, c.dp(8), 0, c.dp(12))
        }
        summary.addView(metadata)
        summary.addView(
            TvUi.text(
                c,
                getString(
                    if (store.playlistId == null) R.string.tv_watch_later_intro else R.string.tv_saved_playlist_intro),
                14f,
                true))
        val actions = TvUi.row(c).apply { setPadding(0, c.dp(16), 0, 0) }
        playAll = TvUi.button(c, getString(R.string.play_all)) { play() }.apply {
            id = R.id.tv_watch_later_play
            isEnabled = false
        }
        shuffle = TvUi.button(c, getString(R.string.shuffle)) { play(shuffled = true) }.apply {
            id = R.id.tv_watch_later_shuffle
            isEnabled = false
        }
        actions.addView(playAll, LinearLayout.LayoutParams(0, c.dp(42), 1f).apply { marginEnd = c.dp(10) })
        actions.addView(shuffle, LinearLayout.LayoutParams(0, c.dp(42), 1f))
        summary.addView(actions)
        listOf(playAll, shuffle).forEach { button ->
            button.setOnKeyListener { _, key, event ->
                when {
                    key == KeyEvent.KEYCODE_DPAD_LEFT && button != shuffle -> {
                        if (event.action == KeyEvent.ACTION_DOWN) host.focusNavigation()
                        true
                    }
                    key == KeyEvent.KEYCODE_DPAD_RIGHT && button != playAll -> {
                        if (event.action == KeyEvent.ACTION_DOWN) focusVideo()
                        true
                    }
                    else -> false
                }
            }
        }
        root.addView(summary, LinearLayout.LayoutParams(c.dp(256), -2).apply { marginEnd = c.dp(32) })
        val content = TvUi.column(c)
        val header = TvUi.row(c)
        val listTitle = TvUi.text(c, getString(R.string.tv_watch_later_videos), 16f).apply { maxLines = 2 }
        header.addView(listTitle, LinearLayout.LayoutParams(0, c.dp(48), 1f))
        store.playlistId?.let { playlistId ->
            header.addView(
                TvUi.iconButton(c, R.drawable.ic_more, getString(R.string.tv_playlist_options)) {
                    TvPlaylistActions.manage(this, playlistId) { id ->
                        if (id == null) parentFragmentManager.popBackStack() else load()
                    }
                }.apply { id = R.id.tv_playlist_manage })
        }
        content.addView(header)
        val body = FrameLayout(c)
        adapter = TvSavedVideosAdapter({ video, index -> play(video.url, index = index) }, ::options, { focusActions() }) {
            url,
            index ->
            selectedUrl = url
            selectedPosition = index
        }
        list = RecyclerView(c).apply {
            id = R.id.tv_grid
            layoutManager = LinearLayoutManager(c)
            adapter = this@TvSavedPlaylistFragment.adapter
            itemAnimator = null
            clipToPadding = false
            setPadding(0, 0, c.dp(4), c.dp(8))
        }
        body.addView(list, FrameLayout.LayoutParams(-1, -1))
        message = TvUi.column(c).apply {
            gravity = Gravity.CENTER
            setPadding(c.dp(24), 0, c.dp(24), 0)
        }
        body.addView(message, FrameLayout.LayoutParams(-1, -1))
        content.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(
            TvUi.text(c, getString(R.string.tv_watch_later_hint), 11f, true).apply { setPadding(0, c.dp(8), 0, 0) })
        root.addView(content, LinearLayout.LayoutParams(0, -1, 1f))
        return root
    }

    override fun onResume() {
        super.onResume()
        val root = requireView()
        store.observe(this) { root.post { if (view === root && !changing) load() } }
        load()
    }

    override fun onPause() {
        store.release(this)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected", selectedUrl)
        outState.putInt("position", selectedPosition)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        job?.cancel()
        dialog?.dismiss()
        dialog = null
        list.adapter = null
        images.clear(cover)
        super.onDestroyView()
    }

    private fun load() {
        job?.cancel()
        val focus = restore || list.hasFocus()
        job = viewLifecycleOwner.lifecycleScope.launch {
            try {
                host.awaitReady()
                val snapshot = withContext(Dispatchers.IO) { store.name() to store.videos() }
                heading.text = snapshot.first ?: getString(R.string.tv_library)
                videos = snapshot.second
                render(focus)
                restore = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w("TvSavedPlaylist", "Could not load saved videos", error)
                videos = emptyList()
                render(false)
                showMessage(
                    getString(R.string.tv_saved_playlist_error), getString(R.string.tv_watch_later_retry_hint)) {
                    load()
                }
            }
        }
    }

    private fun render(focus: Boolean) {
        val first = videos.firstOrNull()
        if (first != null) {
            cover.scaleType = ImageView.ScaleType.CENTER_CROP
            cover.setPadding(0, 0, 0, 0)
            TvUi.load(cover, first.thumbnails.getHQThumbnail())
        } else {
            images.clear(cover)
            cover.scaleType = ImageView.ScaleType.FIT_CENTER
            val padding = requireContext().dp(40)
            cover.setPadding(padding, padding, padding, padding)
            cover.setImageResource(if (store.playlistId == null) R.drawable.ic_schedule else R.drawable.ic_playlist)
        }
        metadata.text = resources.getQuantityString(R.plurals.tv_watch_later_count, videos.size, videos.size) +
            videos.sumOf { if (it.isLive) 0 else it.duration.coerceAtLeast(0) }
                .takeIf { it > 0 }
                ?.let { " · ${TvUi.duration(it)}" }
                .orEmpty()
        listOf(playAll, shuffle).forEach {
            it.isEnabled = videos.isNotEmpty()
            it.alpha = if (it.isEnabled) 1f else .4f
        }
        adapter.videos.clear()
        adapter.videos.addAll(videos)
        adapter.notifyDataSetChanged()
        message.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        if (adapter.itemCount == 0)
            showMessage(
                getString(R.string.tv_watch_later_empty),
                getString(
                    if (store.playlistId == null) R.string.tv_watch_later_empty_hint
                    else R.string.tv_saved_playlist_empty_hint))
        if (focus && !host.currentFocusInRail()) focusVideo()
    }

    private fun focusActions() {
        if (playAll.isEnabled) playAll.requestFocus() else host.focusNavigation()
    }

    private fun focusVideo() {
        if (adapter.itemCount == 0) {
            host.focusNavigation()
            return
        }
        val index = selectedPosition.takeIf { adapter.videos.getOrNull(it)?.url == selectedUrl }
            ?: adapter.videos.indexOfFirst { it.url == selectedUrl }.coerceAtLeast(0)
        list.scrollToPosition(index)
        fun focus() {
            (list.findViewHolderForAdapterPosition(index) as? TvSavedVideosAdapter.Row)?.content?.requestFocus()
        }
        list.doOnNextLayout { focus() }
        list.post { if (view != null) focus() }
    }

    private fun showMessage(title: String, hint: String, retry: (() -> Unit)? = null) {
        message.visibility = View.VISIBLE
        message.removeAllViews()
        message.addView(
            TvUi.heading(requireContext(), title).apply {
                textSize = 21f
                gravity = Gravity.CENTER
            })
        message.addView(
            TvUi.text(requireContext(), hint, 14f, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, context.dp(12), 0, context.dp(16))
            })
        if (retry != null)
            message.addView(
                TvUi.button(requireContext(), getString(R.string.tv_retry), retry).apply {
                    id = R.id.tv_watch_later_retry
                })
    }

    private fun play(url: String? = null, shuffled: Boolean = false, index: Int = 0) {
        if (changing || videos.isEmpty()) return
        changing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ordered = withContext(Dispatchers.IO) { store.videos() }
                val position = if (url == null) 0
                else index.takeIf { ordered.getOrNull(it)?.url == url } ?: ordered.indexOfFirst { it.url == url }
                val target = ordered.getOrNull(position)
                if (target == null) {
                    load()
                    return@launch
                }
                if (!withContext(Dispatchers.IO) { store.canPlay(target.url) }) {
                    dialog = TvMenuDialog(requireContext()).also { d ->
                        d.panel.title(getString(R.string.tv_history_unavailable))
                        val menu = d.panel.menu()
                        menu.addView(
                            TvUi.text(requireContext(), getString(R.string.tv_history_unavailable_hint), 15f, true))
                        menu.addView(
                            d.panel
                                .option(getString(R.string.tv_sources)) {
                                    d.dismiss()
                                    host.selectTab(4)
                                }.apply { requestFocus() })
                        d.show()
                    }
                    return@launch
                }
                selectedUrl = target.url
                selectedPosition = position
                restore = true
                host.navigate(
                    TvPlayerFragment().apply {
                        arguments = TvPlaylistQueue.start(
                            ordered, position, shuffled, store.playlistId, heading.text.toString())
                    })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showError()
            } finally {
                changing = false
            }
        }
    }

    private fun options(video: SerializedPlatformVideo, position: Int) {
        if (changing) return
        selectedUrl = video.url
        selectedPosition = position
        dialog = TvMenuDialog(requireContext()).also { d ->
            d.panel.title(heading.text.toString())
            val menu = d.panel.menu()
            menu.addView(
                TvUi.text(requireContext(), video.name, 18f).apply {
                    setPadding(context.dp(12), 0, context.dp(12), context.dp(20))
                })
            menu.addView(
                d.panel
                    .option(getString(R.string.tv_play)) {
                        d.dismiss()
                        play(video.url, index = position)
                    }.apply { requestFocus() })
            if (video.author.url.isNotBlank())
                menu.addView(
                    d.panel.option(getString(R.string.tv_channel_title), video.author.name) {
                        d.dismiss()
                        restore = true
                        host.openChannel(video.author.url, video.author.name, video.author.thumbnail)
                    })
            val index = position
            if (index > 0) {
                menu.addView(
                    d.panel
                        .option(getString(R.string.tv_watch_later_first)) {
                            d.dismiss()
                            change {
                                store.move(video.url, 0, index)
                                selectedPosition = 0
                            }
                        }.apply { id = R.id.tv_watch_later_first })
                menu.addView(
                    d.panel
                        .option(getString(R.string.tv_watch_later_up)) {
                            d.dismiss()
                            change {
                                store.move(video.url, index - 1, index)
                                selectedPosition = index - 1
                            }
                        }.apply { id = R.id.tv_watch_later_up })
            }
            if (index < videos.lastIndex)
                menu.addView(
                    d.panel
                        .option(getString(R.string.tv_watch_later_down)) {
                            d.dismiss()
                            change {
                                store.move(video.url, index + 1, index)
                                selectedPosition = index + 1
                            }
                        }.apply { id = R.id.tv_watch_later_down })
            menu.addView(
                d.panel
                    .option(
                        getString(
                            if (store.playlistId == null) R.string.tv_watch_later_remove
                            else R.string.tv_saved_playlist_remove)) {
                        d.dismiss()
                        dialog = TvMenuDialog(requireContext()).also { confirm ->
                            confirm.confirm(
                                getString(
                                    if (store.playlistId == null) R.string.tv_watch_later_remove
                                    else R.string.tv_saved_playlist_remove),
                                getString(R.string.tv_saved_playlist_remove_hint, video.name, heading.text),
                                getString(R.string.tv_history_remove)) {
                                val visible = position
                                selectedUrl = adapter.videos.getOrNull(visible + 1)?.url
                                    ?: adapter.videos.getOrNull(visible - 1)?.url
                                selectedPosition = visible.coerceAtMost(adapter.videos.lastIndex - 1).coerceAtLeast(0)
                                change { store.remove(video.url, position) }
                            }
                            confirm.show()
                        }
                    }.apply { id = R.id.tv_watch_later_remove })
            d.show()
        }
    }

    private fun change(action: () -> Unit) {
        if (changing) return
        changing = true
        job?.cancel()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(NonCancellable + Dispatchers.IO) { action() }
                restore = true
                load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showError()
            } finally {
                changing = false
            }
        }
    }

    private fun showError() {
        dialog = TvMenuDialog(requireContext()).also {
            it.panel.title(heading.text.toString())
            it.panel.message(
                getString(R.string.tv_saved_playlist_change_error), getString(R.string.tv_watch_later_retry_hint))
            it.show()
        }
    }
}
