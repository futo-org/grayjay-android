package com.futo.platformplayer.tv.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.structures.IAsyncPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.tv.TvActivity
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvVideoActions
import com.futo.platformplayer.tv.views.TvVideoAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class TvFeedFragment : Fragment() {
    private lateinit var more: TextView
    private lateinit var retry: TextView
    private lateinit var adapter: TvVideoAdapter
    private var pager: IPager<IPlatformContent>? = null
    private val videos = mutableListOf<IPlatformVideo>()
    private var loadJob: Job? = null
    private var selected = 0
    private var retryNext = false

    protected lateinit var header: LinearLayout
    protected lateinit var actions: LinearLayout
    protected lateinit var status: TextView
    protected lateinit var title: TextView
    protected lateinit var grid: RecyclerView
    protected open val hint: Int = 0
    protected open val separateActions = false
    protected open val autoLoad = true

    protected val host
        get() = requireActivity() as TvActivity

    protected abstract val heading: Int

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val root = TvUi.column(context).apply { setPadding(context.dp(24), context.dp(24), context.dp(30), context.dp(16)) }
        header = TvUi.column(context)
        title = TvUi.heading(context, getString(heading))
        actions = TvUi.row(context)
        if (separateActions) {
            header.addView(title)
            if (hint != 0) header.addView(TvUi.text(context, getString(hint), 14f, true))
            header.addView(actions)
        } else {
            val toolbar = TvUi.row(context)
            toolbar.addView(title, LinearLayout.LayoutParams(0, context.dp(48), 1f))
            toolbar.addView(actions)
            header.addView(toolbar)
        }
        root.addView(header)
        status = TvUi.text(context, "", 15f, true).apply { visibility = View.GONE }
        root.addView(status)
        retry = TvUi.button(context, getString(R.string.tv_retry)) { load(next = retryNext) }
        retry.visibility = View.GONE
        root.addView(retry)
        grid = RecyclerView(context).apply {
            id = R.id.tv_grid
            layoutManager = GridLayoutManager(context, 3)
            clipToPadding = false
            setPadding(0, context.dp(10), 0, context.dp(6))
            itemAnimator = null
        }
        adapter = TvVideoAdapter(
            { host.openVideo(it.url) },
            { host.focusNavigation() },
            options = { video ->
                TvVideoActions.show(this, video) {
                    host.openChannel(video.author.url, video.author.name, video.author.thumbnail)
                }
            }) {
            selected = it
            if (it >= videos.size - 6 && pager?.hasMorePages() == true) load(next = true)
        }
        adapter.videos.addAll(videos)
        grid.adapter = adapter
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        more = TvUi.button(context, getString(R.string.tv_more)) { load(next = true) }
        more.textSize = 13f
        more.visibility = if (videos.isEmpty() && pager?.hasMorePages() == true) View.VISIBLE else View.GONE
        root.addView(more)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        selected = savedInstanceState?.getInt("selected") ?: selected
        configureHeader()
        if (autoLoad && (videos.isEmpty() || isFeedStale(videos))) load()
        else if (videos.isNotEmpty()) {
            grid.scrollToPosition(selected)
            grid.post { grid.findViewHolderForAdapterPosition(selected)?.itemView?.requestFocus() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selected", selected)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        loadJob = null
        grid.adapter = null
        super.onDestroyView()
    }

    protected open fun isFeedStale(videos: List<IPlatformVideo>) = false

    protected abstract suspend fun fetch(): IPager<IPlatformContent>

    protected open fun configureHeader() {
        actions.addView(
            TvUi.iconButton(requireContext(), R.drawable.ic_refresh, getString(R.string.tv_refresh)) { load() })
    }

    protected open fun onLoaded() = Unit

    protected open fun emptyText() = getString(R.string.tv_empty)

    protected fun load(next: Boolean = false) {
        if (next && loadJob?.isActive == true) return
        loadJob?.cancel()
        if (!next) {
            status.text = getString(R.string.tv_loading)
            status.visibility = View.VISIBLE
        }
        retry.visibility = View.GONE
        more.isEnabled = false
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                host.awaitReady()
                val page = withContext(Dispatchers.IO) {
                    val current = if (next)
                        checkNotNull(pager).also {
                            if (it is IAsyncPager<*>)
                                it.nextPageAsync()
                            else it.nextPage()
                        }
                    else fetch()
                    current to current.getResults().filterIsInstance<IPlatformVideo>()
                }
                pager = page.first
                val previousCount = videos.size
                if (!next) {
                    videos.clear()
                    selected = 0
                }
                val existing = videos.mapTo(HashSet()) { it.url }
                videos.addAll(page.second.filter { existing.add(it.url) })
                if (next) {
                    adapter.videos.addAll(videos.drop(previousCount))
                    adapter.notifyItemRangeInserted(previousCount, videos.size - previousCount)
                } else {
                    adapter.videos.clear()
                    adapter.videos.addAll(videos)
                    adapter.notifyDataSetChanged()
                }
                status.text = if (videos.isEmpty()) emptyText() else ""
                status.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
                onLoaded()
                more.visibility = if (videos.isEmpty() && pager?.hasMorePages() == true) View.VISIBLE else View.GONE
                if (!next && videos.isNotEmpty() && !host.currentFocusInRail()) {
                    grid.post { grid.findViewHolderForAdapterPosition(selected)?.itemView?.requestFocus() }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!isAdded || view == null) return@launch
                Logger.e("TvFeed", "Failed to load feed", error)
                retryNext = next
                status.visibility = View.VISIBLE
                status.text = getString(R.string.tv_error)
                retry.visibility = View.VISIBLE
                if (!host.currentFocusInRail()) retry.requestFocus()
            } finally {
                more.isEnabled = true
            }
        }
    }
}
