package com.futo.platformplayer.tv.fragments

import android.content.Context
import android.graphics.Color
import android.net.Uri
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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnNextLayout
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.IPlatformChannelContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.structures.IAsyncPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.tv.TvActivity
import com.futo.platformplayer.tv.search.*
import com.futo.platformplayer.tv.views.TvPlaylistAdapter
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvVideoActions
import com.futo.platformplayer.tv.views.TvVideoAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvSearchFragment : Fragment() {
    private var catalog: List<TvSearchProvider>? = null
    private var catalogError = false
    private var catalogJob: Job? = null
    private var suggestionJob: Job? = null
    private var urlJob: Job? = null
    private val pages = mutableMapOf<TvSearchKind, Page>()
    private var kind = TvSearchKind.VIDEOS
    private var options = TvSearchOptions()
    private var submitted = ""
    private var draft = ""
    private var editing = true
    private var generation = 0
    private var restoreCard = false
    private var urlError = false
    private var urlPending = false
    private var suppressInput = false
    private lateinit var input: EditText
    private lateinit var clear: View
    private lateinit var searchButton: View
    private lateinit var tabs: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var filterButton: TextView
    private lateinit var summary: TextView
    private lateinit var suggestions: TvSearchSuggestions
    private lateinit var keyboard: TvSearchKeyboard
    private lateinit var results: LinearLayout
    private lateinit var grid: RecyclerView
    private lateinit var message: LinearLayout
    private lateinit var footer: LinearLayout
    private lateinit var videos: TvVideoAdapter
    private lateinit var channels: TvSearchChannelAdapter
    private lateinit var playlists: TvPlaylistAdapter
    private var filtersDialog: TvSearchFilters? = null

    internal var source: TvSearchSource = TvSearchSource.platform

    private val host
        get() = requireActivity() as TvActivity

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        state?.let {
            submitted = it.getString("query").orEmpty()
            draft = it.getString("draft") ?: submitted
            editing = it.getBoolean("editing", submitted.isEmpty())
            kind = TvSearchKind.entries.firstOrNull { type -> type.name == it.getString("kind") } ?: TvSearchKind.VIDEOS
            val filters = it.getBundle("filters")
                ?.let { bundle ->
                    bundle.keySet().associateWith { key -> bundle.getStringArrayList(key).orEmpty() }
                }
                .orEmpty()
            options = TvSearchOptions(it.getStringArrayList("sources")?.toSet(), it.getString("sort"), filters)
            pages.getOrPut(kind, ::Page).selected = it.getInt("selected")
            restoreCard = it.getBoolean("card")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val root = TvUi.column(context).apply { setPadding(context.dp(28), context.dp(24), context.dp(32), context.dp(16)) }
        root.addView(
            TvUi.heading(context, getString(R.string.tv_search)).apply { textSize = 28f },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(18) })
        val workspace = TvUi.row(context).apply { gravity = Gravity.TOP }
        keyboard = TvSearchKeyboard(
            context,
            ::insertText,
            ::deleteText,
            { submit(input.text.toString()) },
            host::focusNavigation,
            { if (!suggestions.focusFirst()) input.requestFocus() },
            { input.dispatchKeyEvent(it) })
        workspace.addView(keyboard, LinearLayout.LayoutParams(context.dp(292), -2).apply { marginEnd = context.dp(32) })
        val search = TvUi.column(context)
        workspace.addView(search, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = TvUi.row(context)
        input = EditText(context).apply {
            id = R.id.tv_query
            isSaveEnabled = false
            setSingleLine(true)
            hint = getString(R.string.tv_search_input_hint)
            textSize = 18f
            typeface = ResourcesCompat.getFont(context, R.font.inter_regular)
            setTextColor(Color.WHITE)
            setHintTextColor(TvUi.muted)
            setPadding(context.dp(22), context.dp(12), context.dp(22), context.dp(12))
            setBackgroundResource(R.drawable.tv_search_input)
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            inputType = InputType.TYPE_CLASS_TEXT
            showSoftInputOnFocus = false
            setText(draft)
            setOnClickListener { editQuery() }
            setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_DPAD_CENTER) {
                    if (event.action == KeyEvent.ACTION_UP) editQuery()
                    true
                } else false
            }
            setOnEditorActionListener { _, action, event ->
                if (action == EditorInfo.IME_ACTION_SEARCH ||
                    action == EditorInfo.IME_ACTION_DONE ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) {
                    submit(text.toString())
                    true
                } else false
            }
            doAfterTextChanged { value ->
                if (!suppressInput) {
                    draft = value.toString()
                    clear.visibility = if (draft.isEmpty()) View.GONE else View.VISIBLE
                    editing = true
                    urlJob?.cancel()
                    urlPending = false
                    urlError = false
                    showMode()
                    loadSuggestions()
                }
            }
        }
        bar.addView(input, LinearLayout.LayoutParams(0, context.dp(52), 1f))
        clear = TvUi.iconButton(context, R.drawable.ic_close_thin, getString(R.string.tv_search_clear)) { clearQuery() }
            .apply {
                id = R.id.tv_search_clear
                visibility = if (draft.isEmpty()) View.GONE else View.VISIBLE
            }
        bar.addView(clear)
        searchButton = TvUi.iconButton(context, R.drawable.ic_search, getString(R.string.tv_search)) {
            submit(input.text.toString())
        }.apply {
            id = R.id.tv_search_submit
        }
        bar.addView(
            searchButton,
            LinearLayout.LayoutParams(context.dp(48), context.dp(48)).apply { marginStart = context.dp(12) })
        search.addView(bar)
        val body = FrameLayout(context)
        suggestions = TvSearchSuggestions(context, ::submit, { keyboard.focusLast() }) { value ->
            val before = source.recent()
            source.forget(value)
            val remaining = source.recent()
            suggestions.show(remaining, true, before.indexOf(value).coerceAtMost(remaining.lastIndex))
            if (remaining.isEmpty()) keyboard.focusLast()
        }
        body.addView(suggestions, FrameLayout.LayoutParams(-1, -1))
        results = TvUi.column(context)
        toolbar = TvUi.row(context)
        tabs = TvUi.row(context)
        toolbar.addView(tabs, LinearLayout.LayoutParams(0, context.dp(54), 1f))
        filterButton = TvUi.button(context, getString(R.string.tv_search_filters), ::showFilters).apply {
            id = R.id.tv_search_filters
        }
        toolbar.addView(filterButton)
        results.addView(toolbar)
        summary = TvUi.text(context, "", 13f, true).apply { setPadding(context.dp(8), 0, 0, context.dp(6)) }
        results.addView(summary)
        val content = FrameLayout(context)
        videos = TvVideoAdapter(
            { openVideo(it) },
            host::focusNavigation,
            options = { video ->
                TvVideoActions.show(this, video) {
                    restoreCard = true
                    hideKeyboard()
                    host.openChannel(video.author.url, video.author.name, video.author.thumbnail)
                }
            },
            focused = ::cardFocused)
        channels = TvSearchChannelAdapter({ openChannel(it) }, host::focusNavigation, ::cardFocused)
        playlists = TvPlaylistAdapter({ openPlaylist(it) }, host::focusNavigation, ::cardFocused)
        grid = object : RecyclerView(context) {
            override fun focusSearch(focused: View, direction: Int): View? {
                if (direction == View.FOCUS_UP &&
                    findContainingViewHolder(focused)?.bindingAdapterPosition in 0..2)
                    return tabs.findViewById<View>(kind.viewId)
                return super.focusSearch(focused, direction)
            }
        }.apply {
            id = R.id.tv_grid
            layoutManager = GridLayoutManager(context, 3)
            itemAnimator = null
            clipToPadding = false
            setPadding(0, context.dp(8), 0, context.dp(8))
        }
        content.addView(grid, FrameLayout.LayoutParams(-1, -1))
        message = TvUi.column(context).apply { gravity = Gravity.CENTER }
        content.addView(message, FrameLayout.LayoutParams(-1, -1))
        results.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        footer = TvUi.row(context)
        results.addView(footer)
        body.addView(results, FrameLayout.LayoutParams(-1, -1))
        search.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        buildTabs()
        bindPage(restoreCard)
        showMode()
        if (editing) {
            loadSuggestions()
            keyboard.focusLast()
        }
        if (catalog == null) loadCatalog() else if (!editing) load()
        host.onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (editing && submitted.isNotBlank()) {
                        setInput(submitted)
                        editing = false
                        urlError = false
                        suggestionJob?.cancel()
                        showMode()
                        bindPage(true)
                    } else if (draft.isNotBlank() || !editing) clearQuery()
                    else {
                        isEnabled = false
                        host.onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("query", submitted)
        outState.putString("draft", draft)
        outState.putString("kind", kind.name)
        outState.putBoolean("editing", editing)
        outState.putBoolean("card", restoreCard || grid.hasFocus())
        outState.putInt("selected", pages[kind]?.selected ?: 0)
        options.sourceIds?.let { outState.putStringArrayList("sources", ArrayList(it)) }
        outState.putString("sort", options.sort)
        outState.putBundle(
            "filters",
            Bundle().apply { options.filters.forEach { (key, values) -> putStringArrayList(key, ArrayList(values)) } })
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        saveScroll()
        restoreCard = restoreCard || grid.hasFocus()
        catalogJob?.cancel()
        suggestionJob?.cancel()
        urlJob?.cancel()
        pages.values.forEach {
            it.job?.cancel()
            it.job = null
        }
        filtersDialog?.setOnDismissListener(null)
        filtersDialog?.dismiss()
        filtersDialog = null
        grid.adapter = null
        super.onDestroyView()
    }

    private fun loadCatalog() {
        catalogJob?.cancel()
        catalogError = false
        catalogJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                host.awaitReady()
                val loaded = withContext(Dispatchers.IO) { source.providers() }
                if (!isAdded || view == null) return@launch
                catalog = loaded
                if (loaded.none { kind in it.kinds }) kind = TvSearchKind.VIDEOS
                options = options.sanitized(loaded, TvSearchKind.VIDEOS)
                buildTabs()
                bindPage(restoreCard)
                if (!editing) load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                catalogError = true
                Logger.w("TvSearch", "Failed to load search sources", error)
                if (isAdded && view != null) updateStatus()
            }
        }
    }

    private fun buildTabs() {
        val focus = tabs.findFocus()?.id
        tabs.removeAllViews()
        TvSearchKind.entries
            .filter { type -> type == TvSearchKind.VIDEOS || catalog?.any { type in it.kinds } == true }
            .forEach { type ->
                tabs.addView(
                    TvUi.button(requireContext(), getString(type.label)) {
                        if (kind != type) {
                            saveScroll()
                            kind = type
                            restoreCard = false
                            buildTabs()
                            bindPage()
                            load()
                            tabs.findViewById<View>(type.viewId).requestFocus()
                        }
                    }.apply {
                        id = type.viewId
                        isSelected = type == kind
                        setBackgroundResource(R.drawable.tv_channel_tab)
                    })
            }
        focus?.let { tabs.findViewById<View>(it)?.requestFocus() }
    }

    private fun showMode() {
        keyboard.visibility = if (editing && !urlPending) View.VISIBLE else View.GONE
        suggestions.visibility = if (editing && !urlPending) View.VISIBLE else View.GONE
        results.visibility = if (editing && !urlPending) View.GONE else View.VISIBLE
    }

    private fun editQuery() {
        editing = true
        urlJob?.cancel()
        urlPending = false
        urlError = false
        hideKeyboard()
        showMode()
        loadSuggestions()
        keyboard.focusLast()
    }

    private fun insertText(value: String) {
        val start = minOf(input.selectionStart, input.selectionEnd).coerceAtLeast(0)
        val end = maxOf(input.selectionStart, input.selectionEnd).coerceAtLeast(start)
        input.text.replace(start, end, value)
        input.setSelection(start + value.length)
    }

    private fun deleteText() {
        val start = minOf(input.selectionStart, input.selectionEnd).coerceAtLeast(0)
        val end = maxOf(input.selectionStart, input.selectionEnd).coerceAtLeast(start)
        if (start != end) input.text.delete(start, end)
        else if (start > 0) input.text.delete(Character.offsetByCodePoints(input.text, start, -1), start)
    }

    private fun loadSuggestions() {
        suggestionJob?.cancel()
        val text = draft.trim()
        if (text.isBlank()) {
            suggestions.show(source.recent(), true)
            return
        }
        suggestions.show(emptyList(), false)
        if (isUrl(text)) return
        suggestionJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(250)
            try {
                host.awaitReady()
                val values = withContext(Dispatchers.IO) { source.suggestions(text) }
                if (isAdded && view != null && editing && draft.trim() == text)
                    suggestions.show(values.distinct().filter { it.isNotBlank() }, false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w("TvSearch", "Suggestions unavailable", error)
            }
        }
    }

    private fun setInput(value: String) {
        suppressInput = true
        input.setText(value)
        input.setSelection(value.length)
        draft = value
        clear.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
        suppressInput = false
    }

    private fun hideKeyboard() {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
            input.windowToken, 0)
    }

    private fun submit(value: String) {
        val query = value.trim()
        if (query.isEmpty()) {
            clearQuery()
            return
        }
        hideKeyboard()
        suggestionJob?.cancel()
        setInput(query)
        source.remember(query)
        urlJob?.cancel()
        urlPending = false
        if (isUrl(query)) {
            openUrl(query)
            return
        }
        if (submitted != query) {
            clearPages()
            submitted = query
        }
        editing = false
        urlError = false
        showMode()
        bindPage()
        tabs.findViewById<View>(kind.viewId)?.requestFocus()
        if (catalogError) loadCatalog() else load()
    }

    private fun clearQuery() {
        clearPages()
        submitted = ""
        setInput("")
        editing = true
        urlError = false
        urlJob?.cancel()
        urlPending = false
        showMode()
        loadSuggestions()
        keyboard.focusLast()
    }

    private fun openUrl(url: String) {
        urlJob?.cancel()
        urlPending = true
        editing = false
        urlError = false
        showMode()
        toolbar.visibility = View.GONE
        summary.visibility = View.GONE
        footer.visibility = View.GONE
        message.visibility = View.VISIBLE
        grid.visibility = View.GONE
        message.removeAllViews()
        message.addView(TvUi.text(requireContext(), getString(R.string.tv_loading), 16f, true))
        urlJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                host.awaitReady()
                val target = withContext(Dispatchers.IO) { source.resolveUrl(url) }
                if (!isAdded || view == null || draft != url) return@launch
                urlPending = false
                editing = target != TvSearchUrl.UNSUPPORTED
                when (target) {
                    TvSearchUrl.CHANNEL -> host.openChannel(url)
                    TvSearchUrl.PLAYLIST -> navigatePlaylist(url, null, null)
                    TvSearchUrl.VIDEO -> host.openVideo(url)
                    TvSearchUrl.UNSUPPORTED -> showUrlError()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w("TvSearch", "Failed to open search URL", error)
                if (isAdded && view != null) showUrlError()
            }
        }
    }

    private fun showUrlError() {
        urlPending = false
        urlError = true
        toolbar.visibility = View.GONE
        summary.visibility = View.GONE
        footer.visibility = View.GONE
        message.removeAllViews()
        message.addView(TvUi.text(requireContext(), getString(R.string.tv_search_unsupported_url), 18f))
        message.addView(TvUi.button(requireContext(), getString(R.string.tv_search_edit), ::editQuery))
        message.requestFocus()
    }

    private fun bindPage(focusCard: Boolean = false) {
        if (urlPending || urlError) return
        toolbar.visibility = View.VISIBLE
        summary.visibility = View.VISIBLE
        grid.visibility = View.VISIBLE
        val page = pages.getOrPut(kind, ::Page)
        when (kind) {
            TvSearchKind.VIDEOS -> {
                videos.videos.clear()
                videos.videos.addAll(page.items.filterIsInstance<IPlatformVideo>())
                grid.adapter = videos
            }
            TvSearchKind.CHANNELS -> {
                channels.channels.clear()
                channels.channels.addAll(page.items.filterIsInstance<IPlatformChannelContent>())
                grid.adapter = channels
            }
            TvSearchKind.PLAYLISTS -> {
                playlists.playlists.clear()
                playlists.playlists.addAll(page.items.filterIsInstance<IPlatformPlaylist>())
                grid.adapter = playlists
            }
        }
        page.scroll?.let { grid.layoutManager?.onRestoreInstanceState(it) }
        updateStatus()
        if (focusCard && page.items.isNotEmpty()) {
            grid.scrollToPosition(page.selected)
            grid.doOnNextLayout {
                if (!editing) grid.findViewHolderForAdapterPosition(page.selected)?.itemView?.requestFocus()
            }
        }
    }

    private fun load(next: Boolean = false, refresh: Boolean = false) {
        val sources = catalog ?: return
        if (urlPending || urlError) return
        if (submitted.isBlank() || options.providers(sources, kind).isEmpty()) {
            updateStatus()
            return
        }
        val type = kind
        val page = pages.getOrPut(type, ::Page)
        if (page.job?.isActive == true || (!next && !refresh && page.pager != null)) return
        val requestGeneration = generation
        val query = submitted
        val requestOptions = options.sanitized(sources, type)
        page.error = false
        page.job = viewLifecycleOwner.lifecycleScope.launch {
            try {
                host.awaitReady()
                val result = withContext(Dispatchers.IO) {
                    val loaded = if (next)
                        TvSearchResults(
                            checkNotNull(page.pager).also {
                                if (it is IAsyncPager<*>) it.nextPageAsync() else it.nextPage()
                            },
                            page.failedSources)
                    else source.search(query, type, requestOptions)
                    loaded to loaded.pager.getResults().filter { type.accepts(it) && source.visible(it) }
                }
                if (generation != requestGeneration || pages[type] !== page) return@launch
                page.pager = result.first.pager
                page.failedSources = result.first.failedSources
                if (!next) page.items.clear()
                val start = page.items.size
                val existing = page.items.mapTo(hashSetOf()) { it.url }
                page.items.addAll(result.second.filter { existing.add(it.url) })
                if (isAdded && view != null && kind == type) {
                    when (type) {
                        TvSearchKind.VIDEOS -> {
                            videos.videos.clear()
                            videos.videos.addAll(page.items.filterIsInstance<IPlatformVideo>())
                        }
                        TvSearchKind.CHANNELS -> {
                            channels.channels.clear()
                            channels.channels.addAll(page.items.filterIsInstance<IPlatformChannelContent>())
                        }
                        TvSearchKind.PLAYLISTS -> {
                            playlists.playlists.clear()
                            playlists.playlists.addAll(page.items.filterIsInstance<IPlatformPlaylist>())
                        }
                    }
                    if (next) grid.adapter?.notifyItemRangeInserted(start, page.items.size - start)
                    else grid.adapter?.notifyDataSetChanged()
                    if (!next && restoreCard && !editing) {
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
                Logger.w("TvSearch", "Search request failed", error)
            } finally {
                page.job = null
                if (isAdded &&
                    view != null &&
                    generation == requestGeneration &&
                    kind == type &&
                    pages[type] === page)
                    updateStatus()
            }
        }
        updateStatus()
    }

    private fun updateStatus() {
        if (urlError || urlPending) return
        val page = pages.getOrPut(kind, ::Page)
        val selected = options.providers(catalog.orEmpty(), kind)
        summary.text = buildList {
            add(
                if (options.sourceIds == null) getString(R.string.tv_search_all_sources)
                else selected.joinToString { it.name })
            if (kind == TvSearchKind.VIDEOS)
                options.sort?.let { add(TvSearchFilters.sortLabel(requireContext(), it)) }
            options.capabilities(catalog.orEmpty(), kind).filters.forEach { group ->
                group.filters
                    .filter { it.idOrName in options.filters[group.idOrName].orEmpty() }
                    .forEach { add(it.name) }
            }
        }
            .joinToString("  ·  ")
        filterButton.isEnabled = !catalog.isNullOrEmpty()
        val effective = options.sanitized(catalog.orEmpty(), kind)
        filterButton.text = getString(R.string.tv_search_filters) + if (effective != TvSearchOptions()) " •" else ""
        message.removeAllViews()
        footer.removeAllViews()
        message.visibility = if (page.items.isEmpty()) View.VISIBLE else View.GONE
        footer.visibility = View.GONE
        val loading = page.job?.isActive == true || (catalog == null && !catalogError)
        if (page.items.isEmpty()) {
            val label = when {
                catalogError || page.error -> R.string.tv_search_error
                loading -> R.string.tv_loading
                catalog.isNullOrEmpty() -> R.string.tv_no_sources
                selected.isEmpty() -> R.string.tv_search_no_compatible_source
                else -> R.string.tv_search_no_results
            }
            message.addView(
                TvUi.text(requireContext(), getString(label), 18f).apply {
                    id = R.id.tv_search_status
                    gravity = Gravity.CENTER
                })
            if (!loading && !page.error && !catalogError && selected.isNotEmpty())
                message.addView(
                    TvUi.text(requireContext(), getString(R.string.tv_search_try_another), 14f, true).apply {
                        setPadding(0, context.dp(12), 0, 0)
                    })
            if (!loading && catalog?.isEmpty() == true)
                message.addView(TvUi.button(requireContext(), getString(R.string.tv_sources)) { host.selectTab(4) })
        }
        if (loading) return
        if (catalogError || page.error || page.failedSources.isNotEmpty()) {
            val target = if (page.items.isEmpty()) message else footer.also { it.visibility = View.VISIBLE }
            if (page.items.isNotEmpty())
                target.addView(
                    TvUi.text(
                        requireContext(),
                        getString(
                            if (page.error) R.string.tv_search_more_error else R.string.tv_search_partial_results),
                        13f,
                        true),
                    LinearLayout.LayoutParams(0, -2, 1f))
            target.addView(
                TvUi.button(requireContext(), getString(R.string.tv_retry)) {
                    if (catalogError) loadCatalog()
                    else load(next = page.error && page.pager != null, refresh = true)
                }.apply { id = R.id.tv_search_retry })
        } else if (page.items.isEmpty() && page.pager?.hasMorePages() == true) {
            message.addView(TvUi.button(requireContext(), getString(R.string.tv_more)) { load(next = true) })
        }
    }

    private fun cardFocused(position: Int) {
        val page = pages.getOrPut(kind, ::Page)
        page.selected = position
        if (position >= page.items.size - 6 && !page.error && page.pager?.hasMorePages() == true) load(next = true)
    }

    private fun showFilters() {
        hideKeyboard()
        filtersDialog = TvSearchFilters(requireContext(), catalog.orEmpty(), kind, options.sanitized(catalog.orEmpty(), kind)) {
            val updated = if (kind == TvSearchKind.VIDEOS) it
            else options.copy(sourceIds = it.sourceIds).sanitized(catalog.orEmpty(), TvSearchKind.VIDEOS)
            if (updated != options) {
                options = updated
                clearPages()
                bindPage()
                load()
            }
        }.also { dialog ->
            dialog.setOnDismissListener {
                filtersDialog = null
                filterButton.requestFocus()
            }
            dialog.show()
        }
    }

    private fun openVideo(video: IPlatformVideo) {
        restoreCard = true
        hideKeyboard()
        host.openVideo(video.url)
    }

    private fun openChannel(channel: IPlatformChannelContent) {
        restoreCard = true
        hideKeyboard()
        host.openChannel(channel.url, channel.name, channel.thumbnail)
    }

    private fun openPlaylist(playlist: IPlatformPlaylist) {
        restoreCard = true
        hideKeyboard()
        navigatePlaylist(playlist.url, playlist.name, playlist.author.name)
    }

    private fun navigatePlaylist(url: String, name: String?, author: String?) =
        host.navigate(
            TvPlaylistFragment().apply {
                arguments = Bundle().apply {
                    putString("url", url)
                    putString("name", name)
                    putString("author", author)
                }
            })

    private fun saveScroll() {
        pages[kind]?.scroll = grid.layoutManager?.onSaveInstanceState()
    }

    private fun clearPages() {
        generation++
        pages.values.forEach { it.job?.cancel() }
        pages.clear()
        restoreCard = false
    }

    private fun isUrl(value: String) = Uri.parse(value).scheme?.lowercase() in listOf("http", "https")

    private class Page {
        val items = mutableListOf<IPlatformContent>()
        var pager: IPager<IPlatformContent>? = null
        var job: Job? = null
        var error = false
        var failedSources = emptyList<String>()
        var selected = 0
        var scroll: Parcelable? = null
    }
}
