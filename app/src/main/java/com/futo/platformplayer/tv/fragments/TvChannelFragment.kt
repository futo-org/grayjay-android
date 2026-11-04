package com.futo.platformplayer.tv.fragments

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Parcelable
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.structures.IAsyncPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.tv.TvActivity
import com.futo.platformplayer.tv.channel.TvChannelCapabilities
import com.futo.platformplayer.tv.channel.TvChannelSource
import com.futo.platformplayer.tv.views.TvChannelHeader
import com.futo.platformplayer.tv.views.TvPlayerPanel
import com.futo.platformplayer.tv.views.TvPlaylistAdapter
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvVideoActions
import com.futo.platformplayer.tv.views.TvVideoAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvChannelFragment : Fragment() {
    private var channel: IPlatformChannel? = null
    private var capabilities: TvChannelCapabilities? = null
    private var metadataJob: Job? = null
    private var metadataFailed = false
    private var metadataLoaded = false
    private var active = Tab.VIDEOS
    private var sort: String? = null
    private var query = ""
    private val pages = mutableMapOf<Tab, Page>()
    private lateinit var header: TvChannelHeader
    private lateinit var tabs: LinearLayout
    private lateinit var body: FrameLayout
    private lateinit var grid: RecyclerView
    private lateinit var videos: TvVideoAdapter
    private lateinit var playlists: TvPlaylistAdapter
    private lateinit var search: LinearLayout
    private lateinit var input: EditText
    private lateinit var sortButton: TextView
    private lateinit var message: LinearLayout
    private lateinit var footer: LinearLayout
    private var dialog: Dialog? = null
    private var restoreCard = false

    internal var source: TvChannelSource = TvChannelSource.platform

    private val host
        get() = requireActivity() as TvActivity

    private val url
        get() = requireArguments().getString("url")!!

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        state?.let {
            active = Tab.entries.firstOrNull { tab -> tab.name == it.getString("tab") } ?: Tab.VIDEOS
            sort = it.getString("sort")
            query = it.getString("query").orEmpty()
            pages.getOrPut(active, ::Page).selected = it.getInt("selected")
            restoreCard = it.getBoolean("card")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val root = TvUi.column(context).apply { setPadding(context.dp(28), context.dp(24), context.dp(32), context.dp(14)) }
        header = TvChannelHeader(context, ::subscribe)
        root.addView(header)
        val bar = TvUi.row(context)
        tabs = TvUi.row(context)
        bar.addView(
            HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(tabs)
            },
            LinearLayout.LayoutParams(0, context.dp(52), 1f))
        sortButton = TvUi.button(context, getString(R.string.tv_sort), ::showSort).apply { id = R.id.tv_channel_sort }
        bar.addView(sortButton)
        root.addView(bar)
        root.addView(
            View(context).apply { setBackgroundColor(0xFF292929.toInt()) },
            LinearLayout.LayoutParams(-1, context.dp(1)))
        search = TvUi.row(context).apply { visibility = View.GONE }
        input = EditText(context).apply {
            id = R.id.tv_query
            setSingleLine(true)
            textSize = 16f
            hint = getString(R.string.tv_search_channel)
            setTextColor(Color.WHITE)
            setHintTextColor(TvUi.muted)
            setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(8))
            setBackgroundResource(R.drawable.tv_channel_input)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = InputType.TYPE_CLASS_TEXT
            setText(query)
            setOnEditorActionListener { _, action, event ->
                if (action == EditorInfo.IME_ACTION_SEARCH ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) {
                    submitSearch()
                    true
                } else false
            }
        }
        search.addView(input, LinearLayout.LayoutParams(0, context.dp(40), 1f).apply { marginEnd = context.dp(12) })
        search.addView(TvUi.button(context, getString(R.string.tv_search), ::submitSearch))
        root.addView(search)
        body = FrameLayout(context)
        videos = TvVideoAdapter(
            {
                restoreCard = true
                host.openVideo(it.url)
            },
            host::focusNavigation,
            false,
            { video ->
                TvVideoActions.show(this, video) {
                    restoreCard = true
                    host.openChannel(video.author.url, video.author.name, video.author.thumbnail)
                }
            },
            ::cardFocused)
        playlists = TvPlaylistAdapter(::openPlaylist, host::focusNavigation, ::cardFocused)
        grid = object : RecyclerView(context) {
            override fun focusSearch(focused: View, direction: Int): View? {
                if (direction == View.FOCUS_UP &&
                    findContainingViewHolder(focused)?.bindingAdapterPosition in 0..2) {
                    return if (active == Tab.SEARCH) input else tabs.findViewById<View>(active.viewId)
                }
                return super.focusSearch(focused, direction)
            }
        }.apply {
            id = R.id.tv_grid
            layoutManager = GridLayoutManager(context, 3)
            itemAnimator = null
            clipToPadding = false
            setPadding(0, context.dp(12), 0, context.dp(6))
        }
        body.addView(grid, FrameLayout.LayoutParams(-1, -1))
        message = TvUi.column(context).apply { gravity = Gravity.CENTER }
        body.addView(message, FrameLayout.LayoutParams(-1, -1))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        footer = TvUi.row(context)
        root.addView(footer)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (channel == null) channel = StateSubscriptions.instance.getSubscription(url)?.channel
        bindHeader()
        buildTabs()
        showPage(restoreCard)
        if (!metadataLoaded || metadataFailed) loadMetadata()
        if (capabilities == null)
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    host.awaitReady()
                    capabilities = withContext(Dispatchers.IO) { source.capabilities(url) }
                    if (!isAdded || this@TvChannelFragment.view == null) return@launch
                    if (sort == null)
                        sort = capabilities?.sorts?.firstOrNull { it == ResultCapabilities.ORDER_CHONOLOGICAL }
                    buildTabs()
                    bindHeader()
                    showPage(restoreCard)
                    load()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Logger.w("TvChannel", "Failed to load channel capabilities", error)
                    load()
                }
            }
        else load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("tab", active.name)
        outState.putString("sort", sort)
        outState.putString("query", query)
        outState.putInt("selected", pages[active]?.selected ?: 0)
        outState.putBoolean("card", grid.hasFocus())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        saveScroll()
        restoreCard = restoreCard || grid.hasFocus()
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
        metadataJob?.cancel()
        metadataJob = null
        pages.values.forEach {
            it.job?.cancel()
            it.job = null
        }
        grid.adapter = null
        super.onDestroyView()
    }

    private fun bindHeader() {
        if (!isAdded || view == null) return
        header.bind(
            channel,
            arguments?.getString("name"),
            arguments?.getString("thumbnail"),
            capabilities?.sourceName.orEmpty())
        header.subscription.apply {
            isEnabled = channel != null || metadataFailed
            text = getString(
                when {
                    channel == null && metadataFailed -> R.string.tv_retry_details
                    channel?.let { StateSubscriptions.instance.isSubscribed(it) } == true -> R.string.tv_subscribed
                    else -> R.string.tv_subscribe
                })
        }
    }

    private fun loadMetadata() {
        if (metadataJob?.isActive == true) return
        metadataFailed = false
        bindHeader()
        metadataJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                host.awaitReady()
                channel = withContext(Dispatchers.IO) { source.channel(url) }
                metadataLoaded = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                metadataFailed = true
                Logger.w("TvChannel", "Failed to load channel details", error)
            }
            if (!isAdded || view == null) return@launch
            bindHeader()
            if (active == Tab.ABOUT) showAbout()
        }
    }

    private fun subscribe() {
        val creator = channel ?: return loadMetadata()
        val subscriptions = StateSubscriptions.instance
        if (subscriptions.isSubscribed(creator)) {
            val subscription = (listOf(creator.url) + creator.urlAlternatives).firstNotNullOfOrNull {
                subscriptions.getSubscription(it)
            }
            subscription?.let { subscriptions.removeSubscription(it.channel.url, true) }
        } else subscriptions.addSubscription(creator)
        bindHeader()
    }

    private fun availableTabs(): List<Tab> {
        val caps = capabilities
        return Tab.entries.filter { tab ->
            when (tab) {
                Tab.VIDEOS,
                Tab.ABOUT -> true
                Tab.PLAYLISTS -> caps?.playlists == true
                Tab.SEARCH -> caps?.search == true
                else -> caps?.types?.contains(tab.type) == true
            }
        }
    }

    private fun buildTabs() {
        val focusedId = tabs.findFocus()?.id
        tabs.removeAllViews()
        val available = availableTabs()
        if (capabilities != null && active !in available) active = Tab.VIDEOS
        available.forEach { tab ->
            tabs.addView(
                TvUi.button(requireContext(), getString(tab.label)) { select(tab) }.apply {
                    id = tab.viewId
                    textSize = 14f
                    setPadding(context.dp(14), context.dp(8), context.dp(14), context.dp(8))
                    setBackgroundResource(R.drawable.tv_channel_tab)
                    isSelected = active == tab
                    val animation = onFocusChangeListener
                    setOnFocusChangeListener { target, focused ->
                        animation?.onFocusChange(target, focused)
                        if (focused) header.collapseBanner(false)
                    }
                })
        }
        focusedId?.let { tabs.findViewById<View>(it)?.requestFocus() }
    }

    private fun select(tab: Tab) {
        if (active == tab) return
        saveScroll()
        active = tab
        restoreCard = false
        for (i in 0 until tabs.childCount) tabs.getChildAt(i).isSelected = tabs.getChildAt(i).id == tab.viewId
        showPage()
        load()
    }

    private fun saveScroll() {
        pages[active]?.scroll = grid.layoutManager?.onSaveInstanceState()
    }

    private fun showPage(focusCard: Boolean = false) {
        search.visibility = if (active == Tab.SEARCH) View.VISIBLE else View.GONE
        sortButton.visibility = if (active.type != null && (capabilities?.sorts?.size ?: 0) > 1) View.VISIBLE else View.GONE
        sortButton.text = sort?.let(::sortLabel) ?: getString(R.string.tv_sort)
        if (active == Tab.ABOUT) {
            showAbout()
            return
        }
        body.removeViews(2, body.childCount - 2)
        grid.visibility = View.VISIBLE
        val page = pages.getOrPut(active, ::Page)
        if (active == Tab.PLAYLISTS) {
            playlists.playlists.clear()
            playlists.playlists.addAll(page.items.filterIsInstance<IPlatformPlaylist>())
            grid.adapter = playlists
        } else {
            videos.videos.clear()
            videos.videos.addAll(page.items.filterIsInstance<IPlatformVideo>())
            grid.adapter = videos
        }
        page.scroll?.let { grid.layoutManager?.onRestoreInstanceState(it) }
        updateStatus(page)
        if (focusCard && page.items.isNotEmpty()) {
            grid.scrollToPosition(page.selected)
            grid.doOnNextLayout { grid.findViewHolderForAdapterPosition(page.selected)?.itemView?.requestFocus() }
        }
    }

    private fun load(next: Boolean = false, refresh: Boolean = false) {
        if (!isAdded || view == null) return
        if (active == Tab.ABOUT || (active == Tab.SEARCH && query.isBlank())) return
        val tab = active
        val page = pages.getOrPut(tab, ::Page)
        if (page.job?.isActive == true || (!next && !refresh && page.pager != null)) return
        page.error = false
        val requestedSort = sort
        val requestedQuery = query
        page.job = viewLifecycleOwner.lifecycleScope.launch {
            try {
                host.awaitReady()
                val result = withContext(Dispatchers.IO) {
                    val pager = if (next)
                        checkNotNull(page.pager).also {
                            if (it is IAsyncPager<*>) it.nextPageAsync() else it.nextPage()
                        }
                    else if (tab == Tab.PLAYLISTS) source.playlists(url)
                    else
                        source.content(
                            url,
                            tab.type?.takeIf { capabilities?.types?.contains(it) == true },
                            requestedSort,
                            if (tab == Tab.SEARCH) requestedQuery else "")
                    pager to
                        pager.getResults().filter {
                            if (tab == Tab.PLAYLISTS) it is IPlatformPlaylist else it is IPlatformVideo
                        }
                }
                page.pager = result.first
                if (!next) page.items.clear()
                val existing = page.items.mapTo(hashSetOf()) { it.url }
                val added = result.second.filter { existing.add(it.url) }
                val start = page.items.size
                page.items.addAll(added)
                if (isAdded && view != null && active == tab && pages[tab] === page) {
                    if (tab == Tab.PLAYLISTS) {
                        playlists.playlists.clear()
                        playlists.playlists.addAll(page.items.filterIsInstance<IPlatformPlaylist>())
                    } else {
                        videos.videos.clear()
                        videos.videos.addAll(page.items.filterIsInstance<IPlatformVideo>())
                    }
                    if (next) grid.adapter?.notifyItemRangeInserted(start, added.size)
                    else grid.adapter?.notifyDataSetChanged()
                    if (restoreCard && !next) {
                        grid.scrollToPosition(page.selected)
                        grid.doOnNextLayout {
                            grid.findViewHolderForAdapterPosition(page.selected)?.itemView?.requestFocus()
                        }
                        restoreCard = false
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                page.error = true
                Logger.w("TvChannel", "Failed to load ${tab.name}", error)
            } finally {
                page.job = null
                if (isAdded && view != null && active == tab && pages[tab] === page) updateStatus(page)
            }
        }
        updateStatus(page)
    }

    private fun updateStatus(page: Page) {
        message.removeAllViews()
        footer.removeAllViews()
        message.visibility = if (page.items.isEmpty()) View.VISIBLE else View.GONE
        footer.visibility = View.GONE
        val loading = page.job?.isActive == true
        if (page.items.isEmpty()) {
            val label = when {
                loading -> R.string.tv_loading
                page.error -> R.string.tv_channel_content_error
                active == Tab.SEARCH && query.isBlank() -> R.string.tv_search_channel_hint
                active == Tab.SEARCH -> R.string.tv_channel_no_results
                active == Tab.PLAYLISTS -> R.string.tv_no_playlists
                else -> R.string.tv_empty
            }
            message.addView(
                TvUi.text(requireContext(), getString(label), 16f, true).apply { id = R.id.tv_channel_status })
        }
        if (!loading && (page.error || (page.items.isEmpty() && page.pager?.hasMorePages() == true))) {
            val target = if (page.items.isEmpty()) message else footer.also { it.visibility = View.VISIBLE }
            target.addView(
                TvUi.button(requireContext(), getString(if (page.error) R.string.tv_retry else R.string.tv_more)) {
                    load(next = page.pager != null, refresh = true)
                }.apply { id = R.id.tv_channel_retry })
        }
    }

    private fun cardFocused(position: Int) {
        val page = pages.getOrPut(active, ::Page)
        page.selected = position
        if (position >= 3) header.collapseBanner(true)
        if (position >= page.items.size - 6 && !page.error && page.pager?.hasMorePages() == true) load(next = true)
    }

    private fun submitSearch() {
        query = input.text.toString().trim()
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
            input.windowToken, 0)
        pages.remove(Tab.SEARCH)?.job?.cancel()
        showPage()
        restoreCard = true
        load()
    }

    private fun showAbout() {
        grid.visibility = View.GONE
        message.visibility = View.GONE
        footer.visibility = View.GONE
        body.removeViews(2, body.childCount - 2)
        val context = requireContext()
        val content = TvUi.column(context).apply { setPadding(context.dp(10), context.dp(22), context.dp(24), context.dp(30)) }
        content.addView(TvUi.heading(context, getString(R.string.tv_about_channel)).apply { textSize = 20f })
        content.addView(
            TvUi.text(
                context,
                channel?.description
                    ?.takeIf { it.isNotBlank() }
                    ?.let { HtmlCompat.fromHtml(it.replace("\n", "<br>"), 0) }
                    ?: getString(
                        if (metadataFailed) R.string.tv_channel_details_error
                        else if (channel == null) R.string.tv_loading else R.string.tv_no_description),
                16f,
                true).apply {
                setLineSpacing(context.dp(5).toFloat(), 1f)
                setPadding(0, context.dp(16), 0, context.dp(20))
            })
        if (metadataFailed) content.addView(TvUi.button(context, getString(R.string.tv_retry_details), ::loadMetadata))
        channel?.links?.forEach { (label, link) ->
            content.addView(TvUi.text(context, label, 15f).apply { setPadding(0, context.dp(16), 0, context.dp(6)) })
            content.addView(TvUi.text(context, link, 13f, true))
        }
        body.addView(
            ScrollView(context).apply {
                id = R.id.tv_channel_description
                isFocusable = true
                isVerticalScrollBarEnabled = true
                setOnFocusChangeListener { _, focused -> if (focused) header.collapseBanner(true) }
                setBackgroundResource(R.drawable.tv_channel_reading)
                addView(content)
            },
            FrameLayout.LayoutParams(-1, -1))
    }

    private fun showSort() {
        val options = capabilities?.sorts.orEmpty()
        if (options.size < 2) return
        dialog = Dialog(requireContext()).also { window ->
            val panel = TvPlayerPanel(requireContext()) { window.dismiss() }
            panel.title(getString(R.string.tv_sort))
            val menu = panel.menu()
            options.forEach { option ->
                menu.addView(
                    panel.option(sortLabel(option), checked = option == sort) {
                        saveScroll()
                        sort = option
                        pages.filterKeys { it.type != null }.values.forEach { it.job?.cancel() }
                        pages.keys.removeAll { it.type != null }
                        window.dismiss()
                        showPage()
                        load()
                    })
            }
            window.setContentView(panel)
            window.setOnDismissListener {
                sortButton.requestFocus()
                dialog = null
            }
            window.show()
            window.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(requireContext().dp(360), requireContext().dp(340))
            }
            menu.getChildAt(options.indexOf(sort).coerceAtLeast(0))?.requestFocus()
        }
    }

    private fun sortLabel(value: String): String =
        when (value.uppercase()) {
            "CHRONOLOGICAL" -> getString(R.string.tv_latest)
            "POPULAR",
            "POPULARITY" -> getString(R.string.tv_popular)
            "OLDEST",
            "CHRONOLOGICAL_ASC",
            "REVERSE_CHRONOLOGICAL" -> getString(R.string.tv_oldest)
            else -> value.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }
        }

    private fun openPlaylist(playlist: IPlatformPlaylist) {
        restoreCard = true
        host.navigate(
            TvPlaylistFragment().also {
                it.source = source
                it.arguments = Bundle().apply {
                    putString("url", playlist.url)
                    putString("name", playlist.name)
                    putString("author", playlist.author.name)
                }
            })
    }

    private enum class Tab(val label: Int, val viewId: Int, val type: String? = null) {
        VIDEOS(R.string.videos, R.id.tv_channel_videos, ResultCapabilities.TYPE_VIDEOS),
        SHORTS(R.string.shorts, R.id.tv_channel_shorts, ResultCapabilities.TYPE_SHORTS),
        STREAMS(R.string.tv_streams, R.id.tv_channel_streams, ResultCapabilities.TYPE_STREAMS),
        LIVE(R.string.tv_live, R.id.tv_channel_live, ResultCapabilities.TYPE_LIVE),
        PLAYLISTS(R.string.playlists, R.id.tv_channel_playlists),
        SEARCH(R.string.tv_search, R.id.tv_channel_search),
        ABOUT(R.string.tv_about, R.id.tv_channel_about)
    }

    private class Page {
        val items = mutableListOf<IPlatformContent>()
        var pager: IPager<IPlatformContent>? = null
        var job: Job? = null
        var error = false
        var selected = 0
        var scroll: Parcelable? = null
    }
}
