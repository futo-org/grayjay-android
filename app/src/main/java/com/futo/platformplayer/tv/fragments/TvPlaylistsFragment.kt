package com.futo.platformplayer.tv.fragments

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.tv.TvActivity
import com.futo.platformplayer.tv.playlists.TvPlaylistActions
import com.futo.platformplayer.tv.playlists.TvSavedPlaylistsAdapter
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvPlaylistsFragment : Fragment() {
    private var selected: String? = null
    private var job: Job? = null
    private lateinit var grid: RecyclerView
    private lateinit var adapter: TvSavedPlaylistsAdapter
    private lateinit var status: TextView
    private lateinit var refresh: View
    private lateinit var create: View
    private lateinit var empty: View

    private val host
        get() = requireActivity() as TvActivity

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        selected = state?.getString("selected")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val c = requireContext()
        val root = TvUi.column(c).apply { setPadding(c.dp(24), c.dp(24), c.dp(30), c.dp(16)) }
        val header = TvUi.row(c)
        header.addView(TvUi.heading(c, getString(R.string.playlists)), LinearLayout.LayoutParams(0, c.dp(48), 1f))
        create = TvUi.button(c, getString(R.string.new_playlist), ::createPlaylist).apply { id = R.id.tv_playlist_create }
        header.addView(create, LinearLayout.LayoutParams(-2, c.dp(42)).apply { marginEnd = c.dp(12) })
        refresh = TvUi.iconButton(c, R.drawable.ic_refresh, getString(R.string.tv_refresh)) { load() }
            .apply { id = R.id.tv_playlists_refresh }
        header.addView(refresh)
        root.addView(header)
        status = TvUi.text(c, getString(R.string.tv_saved_playlists_intro), 14f, true)
        root.addView(status)
        adapter = TvSavedPlaylistsAdapter(
            { playlist ->
                selected = playlist.id
                host.navigate(
                    TvSavedPlaylistFragment().apply {
                        arguments = Bundle().apply {
                            putString("playlistId", playlist.id)
                            putString("name", playlist.name)
                        }
                    })
            },
            { playlist ->
                selected = playlist.id
                val index = adapter.playlists.indexOfFirst { it.id == playlist.id }
                val neighbor = adapter.playlists.getOrNull(index + 1)?.id ?: adapter.playlists.getOrNull(index - 1)?.id
                TvPlaylistActions.manage(this, playlist.id) { id ->
                    selected = id ?: neighbor
                    load(true)
                }
            },
            { host.focusNavigation() },
            { selected = it })
        grid = RecyclerView(c).apply {
            id = R.id.tv_grid
            layoutManager = GridLayoutManager(c, 3)
            adapter = this@TvPlaylistsFragment.adapter
            clipToPadding = false
            itemAnimator = null
            setPadding(0, c.dp(16), 0, c.dp(6))
        }
        val body = FrameLayout(c)
        body.addView(grid, FrameLayout.LayoutParams(-1, -1))
        empty = TvUi.column(c).apply {
            gravity = Gravity.CENTER
            addView(TvUi.heading(c, getString(R.string.tv_saved_playlists_empty)).apply { textSize = 24f })
            addView(
                TvUi.text(c, getString(R.string.tv_saved_playlists_empty_hint), 15f, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(c.dp(60), c.dp(12), c.dp(60), c.dp(24))
                })
            addView(TvUi.button(c, getString(R.string.new_playlist), ::createPlaylist))
            visibility = View.GONE
        }
        body.addView(empty, FrameLayout.LayoutParams(-1, -1))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TvUi.text(c, getString(R.string.tv_playlists_hint), 11f, true))
        return root
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected", selected)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        job?.cancel()
        grid.adapter = null
        super.onDestroyView()
    }

    private fun createPlaylist() {
        TvPlaylistActions.create(this) {
            selected = it
            load(true)
        }
    }

    private fun load(restoreFocus: Boolean = false) {
        job?.cancel()
        val restore = restoreFocus || grid.hasFocus() || selected != null
        job = viewLifecycleOwner.lifecycleScope.launch {
            try {
                host.awaitReady()
                val playlists = withContext(Dispatchers.IO) {
                    StatePlaylists.instance
                        .getPlaylists()
                        .filter { it.id != StatePlaylists.LAST_QUEUE_PLAYLIST_ID }
                        .sortedByDescending { maxOf(it.datePlayed, it.dateUpdate, it.dateCreation) }
                }
                adapter.playlists.clear()
                adapter.playlists.addAll(playlists)
                adapter.notifyDataSetChanged()
                status.text = getString(R.string.tv_saved_playlists_intro)
                empty.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
                if (restore && !host.currentFocusInRail()) {
                    if (playlists.isEmpty()) create.requestFocus()
                    else {
                        val index = playlists.indexOfFirst { it.id == selected }.coerceAtLeast(0)
                        grid.scrollToPosition(index)
                        fun focus() {
                            grid.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()
                        }
                        grid.doOnNextLayout { focus() }
                        grid.post { if (view != null) focus() }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w("TvPlaylists", "Could not load playlists", error)
                empty.visibility = View.GONE
                status.text = getString(R.string.tv_saved_playlists_error)
                if (!host.currentFocusInRail()) refresh.requestFocus()
            }
        }
    }
}
