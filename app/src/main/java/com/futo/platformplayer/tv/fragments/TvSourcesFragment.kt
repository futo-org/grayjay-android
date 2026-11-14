package com.futo.platformplayer.tv.fragments

import android.app.Dialog
import android.net.Uri
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
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.tv.playlists.TvPlaylistNameDialog
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.tv.TvActivity
import com.futo.platformplayer.tv.sources.TvSourceAdapter
import com.futo.platformplayer.tv.sources.TvSourceInfo
import com.futo.platformplayer.tv.sources.TvSourcesStore
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvSourcesFragment : Fragment() {
    private var items = emptyList<TvSourceInfo>()
    private var tab = 0
    private var selectedId: String? = null
    private var restore = false
    private lateinit var grid: RecyclerView
    private lateinit var adapter: TvSourceAdapter
    private lateinit var tabs: LinearLayout
    private lateinit var summary: TextView
    private lateinit var message: LinearLayout
    private var loading = false
    private var installDialog: Dialog? = null

    internal var store: TvSourcesStore? = null

    private val host
        get() = requireActivity() as TvActivity

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        tab = state?.getInt("tab") ?: 0
        selectedId = state?.getString("selected")
        restore = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val c = requireContext()
        if (store == null) store = TvSourcesStore.platform(c)
        val root = TvUi.column(c).apply { setPadding(c.dp(28), c.dp(24), c.dp(32), c.dp(16)) }
        val header = TvUi.row(c)
        val title = TvUi.column(c)
        title.addView(TvUi.heading(c, getString(R.string.tv_sources)).apply { textSize = 28f })
        title.addView(
            TvUi.text(c, getString(R.string.tv_sources_intro), 15f, true).apply { setPadding(0, c.dp(8), 0, 0) })
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TvUi.button(c, getString(R.string.add_source), ::addSource).apply { id = R.id.tv_sources_add })
        header.addView(
            TvUi.iconButton(c, R.drawable.ic_refresh, getString(R.string.tv_refresh)) { load() }
                .apply { id = R.id.tv_sources_refresh })
        root.addView(header)
        tabs = TvUi.row(c)
        root.addView(tabs, LinearLayout.LayoutParams(-1, c.dp(62)))
        summary = TvUi.text(c, "", 13f, true).apply { setPadding(c.dp(8), 0, 0, c.dp(12)) }
        root.addView(summary)
        val body = FrameLayout(c)
        adapter = TvSourceAdapter(
            { item ->
                selectedId = item.id
                restore = true
                host.navigate(
                    TvSourceDetailsFragment().apply {
                        store = this@TvSourcesFragment.store
                        arguments = Bundle().apply {
                            putString("id", item.id)
                            putString("name", item.name)
                        }
                    })
            },
            host::focusNavigation) {
            selectedId = adapter.sources.getOrNull(it)?.id
        }
        grid = object : RecyclerView(c) {
            override fun focusSearch(focused: View, direction: Int): View? {
                if (direction == View.FOCUS_UP &&
                    findContainingViewHolder(focused)?.bindingAdapterPosition in 0..2)
                    return tabs.getChildAt(tab)
                return super.focusSearch(focused, direction)
            }
        }.apply {
            id = R.id.tv_grid
            layoutManager = GridLayoutManager(c, 3)
            this.adapter = this@TvSourcesFragment.adapter
            itemAnimator = null
            clipToPadding = false
            setPadding(0, c.dp(4), 0, c.dp(12))
        }
        body.addView(grid, FrameLayout.LayoutParams(-1, -1))
        message = TvUi.column(c).apply { gravity = Gravity.CENTER }
        body.addView(message, FrameLayout.LayoutParams(-1, -1))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        val titles = listOf(R.string.tv_search_all_sources, R.string.tv_enabled, R.string.tv_source_available)
        val ids = listOf(R.id.tv_sources_all, R.id.tv_sources_enabled, R.id.tv_sources_available)
        titles.forEachIndexed { index, label ->
            tabs.addView(
                TvUi.button(requireContext(), getString(label)) {
                    tab = index
                    selectedId = null
                    render()
                }.apply {
                    id = ids[index]
                    setBackgroundResource(R.drawable.tv_channel_tab)
                })
        }
        load()
    }

    override fun onSaveInstanceState(state: Bundle) {
        state.putInt("tab", tab)
        state.putString("selected", selectedId)
        super.onSaveInstanceState(state)
    }

    override fun onDestroyView() {
        installDialog?.dismiss()
        installDialog = null
        grid.adapter = null
        super.onDestroyView()
    }

    private fun addSource() {
        val c = requireContext()
        lateinit var editor: TvPlaylistNameDialog
        editor = TvPlaylistNameDialog(c, getString(R.string.install_by_url), "", getString(R.string.install),
            label = getString(R.string.tv_source_url), hint = "https://example.com/plugin/config.json",
            emptyError = getString(R.string.tv_source_url_required), allowUppercase = true) { url ->
            val uri = Uri.parse(url)
            if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank()) {
                editor.showError(getString(R.string.tv_source_url_required))
            } else {
                editor.dismiss()
                StatePlugins.instance.installPlugin(c, viewLifecycleOwner.lifecycleScope, url) {
                    if (view != null) load()
                }
            }
        }
        installDialog = editor
        editor.show()
    }

    private fun render() {
        for (i in 0 until tabs.childCount) tabs.getChildAt(i).isSelected = i == tab
        summary.text = getString(R.string.tv_sources_count, items.count { it.enabled }, items.size)
        adapter.sources.clear()
        adapter.sources.addAll(
            items.filter {
                when (tab) {
                    1 -> it.enabled
                    2 -> !it.enabled
                    else -> true
                }
            })
        adapter.notifyDataSetChanged()
        message.removeAllViews()
        message.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        if (adapter.itemCount == 0)
            message.addView(TvUi.text(requireContext(), getString(R.string.tv_sources_empty), 18f))
        if (restore && !host.currentFocusInRail() && adapter.itemCount > 0) {
            val index = adapter.sources.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
            grid.scrollToPosition(index)
            grid.doOnNextLayout { grid.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus() }
            restore = false
        }
    }

    private fun load() {
        if (loading) return
        loading = true
        if (items.isEmpty()) {
            message.visibility = View.VISIBLE
            message.removeAllViews()
            message.addView(TvUi.text(requireContext(), getString(R.string.tv_loading), 18f))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                host.awaitReady()
                restore = restore || grid.hasFocus()
                items = withContext(Dispatchers.IO) { checkNotNull(store).list() }
                if (isAdded && view != null) render()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w("TvSources", "Could not load sources", error)
                if (isAdded && view != null) {
                    message.visibility = View.VISIBLE
                    message.removeAllViews()
                    message.addView(TvUi.text(requireContext(), getString(R.string.tv_sources_load_error), 18f))
                    message.addView(
                        TvUi.button(requireContext(), getString(R.string.tv_retry)) { load() }
                            .apply { id = R.id.tv_sources_retry })
                }
            } finally {
                loading = false
            }
        }
    }
}
