package com.futo.platformplayer.tv.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.channels.SerializedChannel
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.structures.IAsyncPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.tv.TvActivity
import com.futo.platformplayer.tv.subscriptions.*
import com.futo.platformplayer.tv.views.TvMenuDialog
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvVideoActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvSubscriptionsFragment : Fragment() {
    private var channels = listOf<SerializedChannel>()
    private var catalogLoaded = false
    private var filters = TvSubscriptionFilters()
    private val videos = mutableListOf<IPlatformVideo>()
    private var pager: IPager<IPlatformContent>? = null
    private var job: Job? = null
    private var unsubscribe: (() -> Unit)? = null
    private var dialog: Dialog? = null
    private var generation = 0
    private var loading = false
    private var selectedVideo: String? = null
    private var selectedCreator: String? = null
    private var returnTo: String? = null
    private var collapsed = false
    private var enabledSources = true
    private lateinit var grid: RecyclerView
    private lateinit var adapter: TvSubscriptionVideoAdapter
    private lateinit var creators: RecyclerView
    private lateinit var creatorAdapter: TvSubscriptionCreatorsAdapter
    private lateinit var filtersButton: View
    private lateinit var refreshButton: View
    private lateinit var subtitle: TextView
    private lateinit var feedLabel: TextView
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var statusAction: TextView
    private lateinit var message: LinearLayout

    internal var store: TvSubscriptionsStore = TvSubscriptionsStore.platform()

    private val host
        get() = requireActivity() as TvActivity

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        selectedVideo = state?.getString("video")
        selectedCreator = state?.getString("creator")
        returnTo = state?.getString("focus")
        collapsed = state?.getBoolean("collapsed") ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val c = requireContext()
        val root = TvUi.column(c).apply { setPadding(c.dp(28), c.dp(24), c.dp(32), c.dp(12)) }
        val header = TvUi.row(c)
        val title = TvUi.column(c)
        title.addView(TvUi.heading(c, getString(R.string.tv_subscriptions)).apply { textSize = 28f })
        subtitle = TvUi.text(c, getString(R.string.tv_subscriptions_hint), 14f, true).apply { setPadding(0, c.dp(7), 0, 0) }
        title.addView(subtitle)
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        filtersButton = TvUi.button(c, getString(R.string.filters), ::showFilters).apply {
            id = R.id.tv_subscriptions_filters
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_filter_settings_25dp, 0, 0, 0)
            compoundDrawablePadding = c.dp(8)
            compoundDrawableTintList = textColors
        }
        header.addView(filtersButton)
        refreshButton = TvUi.iconButton(c, R.drawable.ic_refresh, getString(R.string.tv_refresh)) { refresh() }.apply {
            id = R.id.tv_subscriptions_refresh
        }
        header.addView(refreshButton)
        root.addView(header)
        creatorAdapter = TvSubscriptionCreatorsAdapter(
            { channel ->
                returnTo = "creator"
                host.openChannel(channel.url, channel.name, channel.thumbnail)
            },
            {
                selectedCreator = it
                returnTo = "creator"
            }) { key ->
            when (key) {
                KeyEvent.KEYCODE_DPAD_LEFT -> host.focusNavigation()
                KeyEvent.KEYCODE_DPAD_UP -> filtersButton.requestFocus()
                KeyEvent.KEYCODE_DPAD_DOWN -> focusVideo(0)
            }
        }
        creators = RecyclerView(c).apply {
            id = R.id.tv_subscriptions_creators
            layoutManager = LinearLayoutManager(c, RecyclerView.HORIZONTAL, false)
            adapter = creatorAdapter
            itemAnimator = null
            clipToPadding = false
            setPadding(c.dp(4), c.dp(4), c.dp(4), c.dp(4))
        }
        root.addView(creators, LinearLayout.LayoutParams(-1, c.dp(116)).apply { topMargin = c.dp(16) })
        feedLabel = TvUi.text(c, getString(R.string.tv_subscriptions_latest), 16f).apply {
            setPadding(c.dp(8), c.dp(12), 0, c.dp(6))
        }
        root.addView(feedLabel)
        val body = FrameLayout(c)
        adapter = TvSubscriptionVideoAdapter(
            { video ->
                returnTo = "video"
                host.openVideo(video.url)
            },
            host::focusNavigation,
            { video ->
                TvVideoActions.show(this, video) {
                    returnTo = "video"
                    host.openChannel(video.author.url, video.author.name, video.author.thumbnail)
                }
            }) { index ->
            selectedVideo = adapter.videos.getOrNull(index)?.url
            returnTo = "video"
            if (index >= 3) setCollapsed(true)
            if (index >= adapter.itemCount - 6) loadMore()
        }
        grid = object : RecyclerView(c) {
            override fun focusSearch(focused: View, direction: Int): View? {
                val position = findContainingViewHolder(focused)?.bindingAdapterPosition ?: -1
                if (direction == View.FOCUS_UP && position in 0..2) {
                    setCollapsed(false)
                    return focusCreator() ?: filtersButton
                }
                return super.focusSearch(focused, direction)
            }
        }.apply {
            id = R.id.tv_grid
            layoutManager = GridLayoutManager(c, 3)
            adapter = this@TvSubscriptionsFragment.adapter
            itemAnimator = null
            clipToPadding = false
        }
        body.addView(grid, FrameLayout.LayoutParams(-1, -1))
        message = TvUi.column(c).apply { gravity = Gravity.CENTER }
        body.addView(message, FrameLayout.LayoutParams(-1, -1))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = TvUi.row(c)
        progress = ProgressBar(c).apply { visibility = View.GONE }
        footer.addView(progress, LinearLayout.LayoutParams(c.dp(16), c.dp(16)).apply { marginEnd = c.dp(10) })
        status = TvUi.text(c, "", 12f, true).apply { id = R.id.tv_subscriptions_status }
        footer.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        statusAction = TvUi.button(c, "") {}.apply {
            id = R.id.tv_subscriptions_more
            visibility = View.GONE
        }
        footer.addView(statusAction, LinearLayout.LayoutParams(-2, c.dp(32)))
        root.addView(footer, LinearLayout.LayoutParams(-1, c.dp(36)))
        return root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        val scope = viewLifecycleOwner.lifecycleScope
        unsubscribe = store.observe(
            { scope.launch { initialize() } },
            { done, total ->
                scope.launch {
                    if (loading && total > 0)
                        status.text = getString(R.string.tv_subscriptions_progress, done, total)
                }
            })
        initialize()
    }

    override fun onSaveInstanceState(state: Bundle) {
        state.putString("video", selectedVideo)
        state.putString("creator", selectedCreator)
        state.putString("focus", returnTo)
        state.putBoolean("collapsed", collapsed)
        super.onSaveInstanceState(state)
    }

    override fun onDestroyView() {
        generation++
        job?.cancel()
        unsubscribe?.invoke()
        unsubscribe = null
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
        grid.adapter = null
        creators.adapter = null
        super.onDestroyView()
    }

    private fun initialize() {
        generation++
        job?.cancel()
        val request = generation
        setLoading(true)
        job = viewLifecycleOwner.lifecycleScope.launch {
            try {
                host.awaitReady()
                val current = withContext(Dispatchers.IO) { store.channels() }
                val changed = current.map { it.url }.toSet() != channels.map { it.url }.toSet()
                val refreshNeeded = store.refreshOnOpen(catalogLoaded && changed)
                catalogLoaded = true
                channels = current
                filters = store.filters()
                enabledSources = withContext(Dispatchers.IO) { store.hasEnabledSources() }
                creatorAdapter.channels = channels
                creatorAdapter.notifyDataSetChanged()
                subtitle.text = if (channels.isEmpty()) getString(R.string.tv_subscriptions_hint)
                else
                    resources.getQuantityString(
                        R.plurals.tv_subscriptions_creator_count, channels.size, channels.size)
                setCollapsed(collapsed)
                filtersButton.visibility = if (channels.isEmpty()) View.GONE else View.VISIBLE
                refreshButton.visibility = filtersButton.visibility
                refreshButton.isEnabled = enabledSources
                refreshButton.alpha = if (enabledSources) 1f else .4f
                if (channels.isEmpty()) {
                    videos.clear()
                    pager = null
                    status.text = ""
                    render()
                    return@launch
                }
                if (changed || pager == null) {
                    val cached = withContext(Dispatchers.IO) { store.cached() }
                    replace(cached)
                } else render()
                restoreFocus()
                if (enabledSources && refreshNeeded) fetch(false)
                else
                    status.text = getString(
                        if (enabledSources) R.string.tv_subscriptions_cached
                        else R.string.tv_subscriptions_sources_disabled)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failure(error)
            } finally {
                if (request == generation) finishLoading()
            }
        }
    }

    private fun refresh(bypass: Boolean = false) {
        if (loading) return
        val request = ++generation
        setLoading(true)
        job = viewLifecycleOwner.lifecycleScope.launch {
            try {
                fetch(bypass)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failure(error)
            } finally {
                if (request == generation) finishLoading()
            }
        }
    }

    private suspend fun fetch(bypass: Boolean) {
        status.text = getString(R.string.tv_subscriptions_refreshing)
        val result = withContext(Dispatchers.IO) { store.refresh(bypass) }
        val keepFocus = grid.hasFocus()
        replace(result.pager, keepOnEmpty = result.failures > 0)
        status.text = if (result.failures > 0) getString(R.string.tv_subscriptions_partial)
        else getString(R.string.tv_subscriptions_updated)
        if (result.failures > 0) action(getString(R.string.tv_retry)) { refresh() }
        if (keepFocus) restoreFocus()
    }

    private suspend fun replace(next: IPager<IPlatformContent>, keepOnEmpty: Boolean = false) {
        val incoming = read(next, first = true)
        if (keepOnEmpty && incoming.isEmpty() && videos.isNotEmpty()) return
        pager = next
        videos.clear()
        videos.addAll(incoming)
        render()
    }

    private suspend fun read(current: IPager<IPlatformContent>, first: Boolean): List<IPlatformVideo> =
        withContext(Dispatchers.IO) {
            val incoming = linkedMapOf<String, IPlatformVideo>()
            val preferences = filters
            var initial = first
            var pages = 0
            do {
                ensureActive()
                if (!initial) {
                    if (current is IAsyncPager<*>) current.nextPageAsync() else current.nextPage()
                }
                current.getResults().filterIsInstance<IPlatformVideo>().forEach { incoming[it.url] = it }
                initial = false
                pages++
            } while (current.hasMorePages() &&
                pages < 5 &&
                incoming.values.count { preferences.includes(it, store::watched) } < 9)
            incoming.values.toList()
        }

    private fun loadMore() {
        val current = pager ?: return
        if (loading || !current.hasMorePages()) return
        val request = ++generation
        setLoading(true)
        job = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val incoming = read(current, first = false)
                val urls = videos.map { it.url }.toMutableSet()
                videos.addAll(incoming.filter { urls.add(it.url) })
                render()
                status.text = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.w("TvSubscriptions", "Next page failed", error)
                status.text = getString(R.string.tv_subscriptions_more_error)
                action(getString(R.string.tv_retry), ::loadMore)
            } finally {
                if (request == generation) finishLoading()
            }
        }
    }

    private fun render() {
        val keepFocus = grid.hasFocus()
        adapter.videos.clear()
        adapter.videos.addAll(
            videos.filter { video ->
                filters.includes(video, store::watched)
            })
        adapter.notifyDataSetChanged()
        feedLabel.text = getString(
            if (filters == TvSubscriptionFilters()) R.string.tv_subscriptions_latest
            else R.string.tv_subscriptions_filtered)
        grid.visibility = if (adapter.itemCount == 0) View.GONE else View.VISIBLE
        feedLabel.visibility = if (channels.isEmpty()) View.GONE else View.VISIBLE
        message.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        message.removeAllViews()
        if (adapter.itemCount == 0) {
            val title = when {
                channels.isEmpty() -> R.string.tv_subscriptions_empty
                !enabledSources -> R.string.tv_subscriptions_sources_title
                filters != TvSubscriptionFilters() -> R.string.tv_subscriptions_no_matches
                else -> R.string.tv_subscriptions_no_videos
            }
            val hint = when {
                channels.isEmpty() -> R.string.tv_no_subscriptions
                !enabledSources -> R.string.tv_subscriptions_sources_disabled
                filters != TvSubscriptionFilters() -> R.string.tv_subscriptions_filter_hint
                else -> R.string.tv_subscriptions_no_videos_hint
            }
            message.addView(
                TvUi.heading(requireContext(), getString(title)).apply {
                    textSize = 23f
                    gravity = Gravity.CENTER
                })
            message.addView(
                TvUi.text(requireContext(), getString(hint), 14f, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(context.dp(32), context.dp(12), context.dp(32), context.dp(16))
                })
            if (channels.isEmpty())
                message.addView(
                    TvUi.button(requireContext(), getString(R.string.tv_search)) { host.selectTab(1) }
                        .apply { tag = "subscriptions:empty-action" })
            else if (!enabledSources)
                message.addView(
                    TvUi.button(requireContext(), getString(R.string.tv_sources)) { host.selectTab(4) }
                        .apply { tag = "subscriptions:empty-action" })
            else if (filters != TvSubscriptionFilters())
                message.addView(TvUi.button(requireContext(), getString(R.string.filters), ::showFilters))
        }
        if (keepFocus) restoreFocus()
    }

    private fun setLoading(value: Boolean) {
        loading = value
        progress.visibility = if (value) View.VISIBLE else View.GONE
        if (value) {
            status.text = getString(R.string.tv_loading)
            statusAction.visibility = View.GONE
        }
    }

    private fun finishLoading() {
        setLoading(false)
        if (statusAction.visibility != View.VISIBLE && pager?.hasMorePages() == true)
            action(getString(R.string.tv_more), ::loadMore)
    }

    private fun action(label: String, run: () -> Unit) {
        statusAction.text = label
        statusAction.visibility = View.VISIBLE
        statusAction.setOnClickListener { run() }
    }

    private fun failure(error: Throwable) {
        Logger.w("TvSubscriptions", "Subscription feed failed", error)
        render()
        status.text = getString(R.string.tv_subscriptions_error)
        action(getString(R.string.tv_retry)) { if (channels.isEmpty()) initialize() else refresh() }
        if (error is TvSubscriptionRateLimit) {
            dialog = TvMenuDialog(requireContext()).also { d ->
                d.confirm(
                    getString(R.string.rate_limit_warning),
                    getString(R.string.tv_subscriptions_rate_limit, error.sources.joinToString(", ")),
                    getString(R.string.tv_subscriptions_refresh_anyway)) {
                    refresh(true)
                }
                d.show()
            }
        }
    }

    private fun showFilters() {
        dialog = TvMenuDialog(requireContext()).also { d ->
            d.panel.title(getString(R.string.filters))
            fun menu(focus: String) {
                val rows = d.panel.menu()
                rows.addView(
                    TvUi.text(requireContext(), getString(R.string.tv_subscriptions_filters_hint), 14f, true).apply {
                        setPadding(context.dp(16), 0, context.dp(16), context.dp(18))
                    })
                val options = listOf(
                    Triple("videos", R.string.videos, filters.videos),
                    Triple("live", R.string.live, filters.live),
                    Triple("planned", R.string.planned, filters.planned),
                    Triple("watched", R.string.watched, filters.watched))
                options.forEach { (key, label, enabled) ->
                    rows.addView(
                        d.panel
                            .option(
                                getString(label),
                                getString(if (enabled) R.string.enabled else R.string.disabled),
                                enabled) {
                                filters = when (key) {
                                    "videos" -> filters.copy(videos = !enabled)
                                    "live" -> filters.copy(live = !enabled)
                                    "planned" -> filters.copy(planned = !enabled)
                                    else -> filters.copy(watched = !enabled)
                                }
                                store.saveFilters(filters)
                                render()
                                menu(key)
                            }.apply { tag = key })
                }
                rows.findViewWithTag<View>(focus).requestFocus()
            }
            menu("videos")
            d.setOnDismissListener { if (view != null && adapter.itemCount < 9) loadMore() }
            d.show()
        }
    }

    private fun setCollapsed(value: Boolean) {
        collapsed = value
        creators.visibility = if (value || channels.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun focusCreator(): View? {
        if (creatorAdapter.itemCount == 0) return null
        val index = channels.indexOfFirst { it.url == selectedCreator }.coerceAtLeast(0)
        creators.scrollToPosition(index)
        val target = creators.findViewHolderForAdapterPosition(index)?.itemView
        if (target == null)
            creators.doOnNextLayout { creators.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus() }
        return target ?: filtersButton
    }

    private fun focusVideo(index: Int) {
        if (adapter.itemCount == 0) {
            statusAction.takeIf { it.isShown }?.requestFocus()
            return
        }
        val target = index.coerceIn(0, adapter.itemCount - 1)
        grid.scrollToPosition(target)
        grid.doOnNextLayout { grid.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus() }
        grid.requestLayout()
    }

    private fun restoreFocus() {
        if (dialog?.isShowing == true) return
        when (returnTo) {
            "creator" -> {
                setCollapsed(false)
                focusCreator()?.requestFocus()
            }
            "video" -> focusVideo(adapter.videos.indexOfFirst { it.url == selectedVideo }.coerceAtLeast(0))
        }
    }
}
