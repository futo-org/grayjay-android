package com.futo.platformplayer.tv.fragments

import android.app.Dialog
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
import com.futo.platformplayer.api.media.structures.IAsyncPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.HistoryVideo
import com.futo.platformplayer.tv.TvActivity
import com.futo.platformplayer.tv.history.TvHistoryAdapter
import com.futo.platformplayer.tv.history.TvHistorySearchDialog
import com.futo.platformplayer.tv.history.TvHistoryStore
import com.futo.platformplayer.tv.views.TvMenuDialog
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvHistoryFragment : Fragment() {
    private var query = ""
    private var selectedUrl: String? = null
    private val items = mutableListOf<HistoryVideo>()
    private var pager: IPager<HistoryVideo>? = null
    private var job: Job? = null
    private var loading = false
    private var generation = 0
    private var changing = false
    private var restore = false
    private var dialog: Dialog? = null
    private lateinit var grid: RecyclerView
    private lateinit var adapter: TvHistoryAdapter
    private lateinit var searchButton: View
    private lateinit var queryLabel: TextView
    private lateinit var clearSearch: View
    private lateinit var message: LinearLayout
    private lateinit var status: LinearLayout

    internal var store: TvHistoryStore = TvHistoryStore.platform()

    private val host
        get() = requireActivity() as TvActivity

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        query = state?.getString("query").orEmpty()
        selectedUrl = state?.getString("selected")
        restore = selectedUrl != null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val c = requireContext()
        val root = TvUi.column(c).apply { setPadding(c.dp(28), c.dp(24), c.dp(32), c.dp(12)) }
        val header = TvUi.row(c)
        val title = TvUi.column(c)
        title.addView(TvUi.heading(c, getString(R.string.history)).apply { textSize = 28f })
        title.addView(
            TvUi.text(c, getString(R.string.tv_history_intro), 15f, true).apply { setPadding(0, c.dp(8), 0, 0) })
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        searchButton = TvUi.iconButton(c, R.drawable.ic_search, getString(R.string.tv_history_search), ::search).apply {
            id = R.id.tv_history_search
        }
        header.addView(searchButton)
        header.addView(
            TvUi.iconButton(c, R.drawable.ic_trash, getString(R.string.tv_history_clear), ::clear).apply {
                id = R.id.tv_history_clear
            },
            LinearLayout.LayoutParams(c.dp(36), c.dp(36)).apply { marginStart = c.dp(16) })
        root.addView(header)
        val queryRow = TvUi.row(c)
        queryLabel = TvUi.text(c, "", 13f, true).apply { setPadding(c.dp(8), 0, 0, 0) }
        queryRow.addView(queryLabel, LinearLayout.LayoutParams(0, -2, 1f))
        clearSearch = TvUi.iconButton(c, R.drawable.ic_close_thin, getString(R.string.tv_search_clear)) {
            query = ""
            selectedUrl = null
            restore = false
            load(true)
            searchButton.requestFocus()
        }.apply { id = R.id.tv_search_clear }
        queryRow.addView(clearSearch)
        root.addView(queryRow, LinearLayout.LayoutParams(-1, c.dp(32)).apply { topMargin = c.dp(20) })
        val body = FrameLayout(c)
        adapter = TvHistoryAdapter({ play(it) }, ::options, host::focusNavigation) { item, position ->
            selectedUrl = item.video.url
            if (position >= adapter.itemCount - 4) load(false)
        }
        grid = object : RecyclerView(c) {
            override fun focusSearch(focused: View, direction: Int): View? {
                if (direction == View.FOCUS_UP &&
                    this@TvHistoryFragment.adapter.inFirstRow(
                        findContainingViewHolder(focused)?.bindingAdapterPosition ?: -1))
                    return searchButton
                return super.focusSearch(focused, direction)
            }
        }.apply {
            id = R.id.tv_grid
            layoutManager = GridLayoutManager(c, 3).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int) =
                        if (this@TvHistoryFragment.adapter.isHeader(position)) 3 else 1
                }
            }
            this.adapter = this@TvHistoryFragment.adapter
            itemAnimator = null
            clipToPadding = false
            setPadding(0, 0, 0, c.dp(12))
        }
        body.addView(grid, FrameLayout.LayoutParams(-1, -1))
        message = TvUi.column(c).apply { gravity = Gravity.CENTER }
        body.addView(message, FrameLayout.LayoutParams(-1, -1))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        status = TvUi.row(c)
        root.addView(status)
        return root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        load(true)
    }

    override fun onSaveInstanceState(state: Bundle) {
        state.putString("query", query)
        state.putString("selected", selectedUrl)
        super.onSaveInstanceState(state)
    }

    override fun onDestroyView() {
        job?.cancel()
        dialog?.dismiss()
        dialog = null
        grid.adapter = null
        super.onDestroyView()
    }

    private fun load(reset: Boolean) {
        if (changing || (!reset && (loading || pager?.hasMorePages() != true))) return
        if (reset) {
            generation++
            job?.cancel()
        }
        val request = generation
        loading = true
        val keepFocus = restore || grid.hasFocus()
        restore = false
        val requestedQuery = query
        val target = selectedUrl.takeIf { reset && keepFocus }
        queryLabel.text = if (query.isBlank()) getString(R.string.tv_history_hint)
        else getString(R.string.tv_history_searching, query)
        clearSearch.visibility = if (query.isBlank()) View.GONE else View.VISIBLE
        status.removeAllViews()
        if (reset) {
            pager = null
            items.clear()
            adapter.submit(emptyList())
            showMessage(getString(R.string.tv_loading))
        }
        job = viewLifecycleOwner.lifecycleScope.launch {
            try {
                host.awaitReady()
                val result = withContext(Dispatchers.IO) {
                    val current = if (reset) store.search(requestedQuery) else checkNotNull(pager)
                    val incoming = mutableListOf<HistoryVideo>()
                    var first = reset
                    do {
                        ensureActive()
                        if (!first) {
                            if (current is IAsyncPager<*>) current.nextPageAsync() else current.nextPage()
                        }
                        incoming.addAll(current.getResults())
                        first = false
                    } while (current.hasMorePages() &&
                        (incoming.size < 9 || (target != null && incoming.none { it.video.url == target })))
                    current to incoming
                }
                pager = result.first
                val urls = items.map { it.video.url }.toMutableSet()
                items.addAll(result.second.filter { urls.add(it.video.url) })
                adapter.submit(items)
                message.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                if (items.isEmpty())
                    showMessage(
                        getString(
                            if (query.isBlank()) R.string.tv_history_empty else R.string.tv_history_no_results),
                        getString(
                            if (query.isBlank()) R.string.tv_history_empty_hint
                            else R.string.tv_history_no_results_hint))
                if (keepFocus) restoreFocus()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w("TvHistory", "History request failed", error)
                if (reset) showMessage(getString(R.string.tv_history_error)) { load(true) }
                else {
                    status.addView(
                        TvUi.text(requireContext(), getString(R.string.tv_history_more_error), 14f, true),
                        LinearLayout.LayoutParams(0, -2, 1f))
                    status.addView(
                        TvUi.button(requireContext(), getString(R.string.tv_retry)) { load(false) }
                            .apply { id = R.id.tv_history_retry })
                }
            } finally {
                if (request == generation) loading = false
            }
        }
    }

    private fun restoreFocus() {
        val index = adapter.indexOf(selectedUrl).takeIf { it >= 0 } ?: adapter.firstVideo()
        if (index < 0) {
            searchButton.requestFocus()
            return
        }
        grid.scrollToPosition(index)
        grid.doOnNextLayout { grid.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus() }
    }

    private fun showMessage(title: String, description: String = "", retry: (() -> Unit)? = null) {
        message.visibility = View.VISIBLE
        message.removeAllViews()
        message.addView(
            TvUi.heading(requireContext(), title).apply {
                textSize = 23f
                gravity = Gravity.CENTER
            })
        if (description.isNotBlank())
            message.addView(
                TvUi.text(requireContext(), description, 15f, true).apply {
                    setPadding(0, context.dp(12), 0, context.dp(20))
                    gravity = Gravity.CENTER
                })
        if (retry != null)
            message.addView(
                TvUi.button(requireContext(), getString(R.string.tv_retry), retry).apply { id = R.id.tv_history_retry })
    }

    private fun search() {
        dialog = TvHistorySearchDialog(requireContext(), query) {
            query = it
            selectedUrl = null
            restore = true
            load(true)
        }.also { it.show() }
    }

    private fun play(item: HistoryVideo, startOver: Boolean = false) {
        if (changing) return
        changing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (withContext(Dispatchers.IO) { store.canPlay(item.video.url) }) {
                    selectedUrl = item.video.url
                    restore = true
                    host.navigate(
                        TvPlayerFragment().apply {
                            arguments = Bundle().apply {
                                putString("url", item.video.url)
                                if (startOver) putLong("position", 0)
                            }
                        })
                } else {
                    dialog = TvMenuDialog(requireContext()).also { d ->
                        d.panel.title(getString(R.string.tv_history_unavailable))
                        val menu = d.panel.menu()
                        menu.addView(
                            TvUi.text(requireContext(), getString(R.string.tv_history_unavailable_hint), 16f)
                                .apply { setPadding(context.dp(12), 0, context.dp(12), context.dp(24)) })
                        menu.addView(
                            d.panel
                                .option(getString(R.string.tv_sources)) {
                                    d.dismiss()
                                    host.selectTab(4)
                                }.apply { requestFocus() })
                        d.show()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showError()
            } finally {
                changing = false
            }
        }
    }

    private fun options(item: HistoryVideo) {
        if (changing) return
        dialog = TvMenuDialog(requireContext()).also { d ->
            d.panel.title(getString(R.string.history))
            val menu = d.panel.menu()
            menu.addView(
                TvUi.heading(requireContext(), item.video.name).apply {
                    textSize = 20f
                    setPadding(context.dp(12), 0, context.dp(12), context.dp(24))
                })
            menu.addView(
                d.panel
                    .option(
                        if (item.position > 0) getString(R.string.tv_history_resume, TvUi.duration(item.position))
                        else getString(R.string.tv_play)) {
                        d.dismiss()
                        play(item)
                    }.apply {
                    id = R.id.tv_history_resume
                    requestFocus()
                })
            if (item.position > 0)
                menu.addView(
                    d.panel
                        .option(getString(R.string.tv_restart)) {
                            d.dismiss()
                            play(item, true)
                        }.apply { id = R.id.tv_history_start_over })
            if (item.video.author.url.isNotBlank())
                menu.addView(
                    d.panel
                        .option(getString(R.string.tv_channel_title), item.video.author.name) {
                            d.dismiss()
                            restore = true
                            host.openChannel(
                                item.video.author.url, item.video.author.name, item.video.author.thumbnail)
                        }.apply { id = R.id.tv_history_channel })
            menu.addView(
                d.panel
                    .option(getString(R.string.tv_history_remove)) {
                        d.dismiss()
                        confirm(
                            getString(R.string.tv_history_remove),
                            getString(R.string.tv_history_remove_confirm, item.video.name),
                            getString(R.string.tv_history_remove)) {
                            val index = items.indexOfFirst { it.video.url == item.video.url }
                            selectedUrl = items.getOrNull(index + 1)?.video?.url ?: items.getOrNull(index - 1)?.video?.url
                            change { store.remove(item.video.url) }
                        }
                    }.apply { id = R.id.tv_history_remove })
            d.show()
        }
    }

    private fun clear() {
        if (changing) return
        dialog = TvMenuDialog(requireContext()).also { d ->
            d.panel.title(getString(R.string.tv_history_clear))
            val menu = d.panel.menu()
            menu.addView(
                TvUi.text(requireContext(), getString(R.string.tv_history_clear_hint), 15f, true).apply {
                    setPadding(context.dp(12), 0, context.dp(12), context.dp(20))
                })
            listOf(
                R.string.tv_history_hour to 60L,
                R.string.tv_history_day to 1440L,
                R.string.tv_history_week to 10080L,
                R.string.tv_history_month to 43200L,
                R.string.tv_history_forever to -1L)
                .forEach { (label, minutes) ->
                    menu.addView(
                        d.panel
                            .option(getString(label)) {
                                d.dismiss()
                                confirm(
                                    getString(R.string.tv_history_clear),
                                    getString(R.string.tv_history_clear_confirm, getString(label)),
                                    getString(R.string.tv_history_clear)) {
                                    selectedUrl = null
                                    change { store.clear(minutes) }
                                }
                            }.apply { tag = "range:$minutes" })
                }
            menu.getChildAt(1).requestFocus()
            d.show()
        }
    }

    private fun confirm(title: String, description: String, action: String, confirmed: () -> Unit) {
        dialog = TvMenuDialog(requireContext()).also {
            it.confirm(title, description, action, confirmed)
            it.show()
        }
    }

    private fun change(action: suspend () -> Unit) {
        if (changing) return
        changing = true
        job?.cancel()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(NonCancellable + Dispatchers.IO) { action() }
                changing = false
                restore = true
                load(true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w("TvHistory", "History change failed", error)
                showError()
            } finally {
                changing = false
            }
        }
    }

    private fun showError() {
        dialog = TvMenuDialog(requireContext()).also {
            it.panel.title(getString(R.string.history))
            it.panel.message(getString(R.string.tv_history_change_error))
            it.show()
        }
    }
}
