package com.futo.platformplayer.tv.views

import android.content.Context
import android.os.Parcelable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.structures.IAsyncPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.views.adapters.CommentViewHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class TvCommentsPanel(
    context: Context,
    private val scope: CoroutineScope,
    loader: suspend () -> IPager<IPlatformComment>,
    private val replies: suspend (IPlatformComment) -> IPager<IPlatformComment>,
    private val channel: (IPlatformComment) -> Unit,
    close: () -> Unit
) : TvPlayerPanel(context, close) {
    private val pages = mutableListOf(Page(loader))
    private var grid: RecyclerView? = null
    private var expanded = false
    private var disposed = false
    private var footer: TextView? = null

    private val page
        get() = pages.last()

    init {
        id = R.id.tv_comments_panel
        title(context.getString(R.string.tv_comments))
        load()
    }

    override fun back(): Boolean {
        if (expanded) {
            showList()
            return true
        }
        if (pages.size > 1) {
            pages.removeAt(pages.lastIndex).job?.cancel()
            showList()
            return true
        }
        return false
    }

    fun dispose() {
        disposed = true
        pages.forEach { it.job?.cancel() }
    }

    private fun load(next: Boolean = false) {
        val current = page
        if (next && current.job?.isActive == true) return
        current.job?.cancel()
        if (!next) message(context.getString(R.string.tv_loading))
        footer?.apply {
            text = context.getString(R.string.tv_loading)
            isEnabled = false
            visibility = View.VISIBLE
        }
        current.job = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val pager = if (next)
                        checkNotNull(current.pager).also {
                            if (it is IAsyncPager<*>) it.nextPageAsync() else it.nextPage()
                        }
                    else current.loader()
                    Triple(pager, pager.getResults(), pager.hasMorePages())
                }
                if (disposed) return@launch
                current.pager = result.first
                current.more = result.third
                val start = current.comments.size
                current.comments.addAll(result.second)
                if (current !== page || expanded) return@launch
                if (next) {
                    grid?.adapter?.notifyItemRangeInserted(start, result.second.size)
                    updateFooter()
                } else showList()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (disposed || current !== page || expanded) return@launch
                if (next)
                    footer?.apply {
                        text = context.getString(R.string.tv_retry)
                        isEnabled = true
                        visibility = View.VISIBLE
                    }
                else
                    message(
                        context.getString(R.string.tv_comments_error),
                        context.getString(R.string.tv_comments_error_hint)) {
                        load()
                    }
            }
        }
    }

    private fun showList() {
        expanded = false
        title(context.getString(if (pages.size > 1) R.string.tv_replies else R.string.tv_comments), pages.size > 1)
        if (page.comments.isEmpty()) {
            message(
                context.getString(if (pages.size > 1) R.string.tv_no_replies else R.string.tv_no_comments),
                if (pages.size > 1) "" else context.getString(R.string.tv_no_comments_hint))
            findViewById<View>(R.id.tv_panel_close).requestFocus()
            return
        }
        body.removeAllViews()
        val current = page
        val column = TvUi.column(context)
        val list = RecyclerView(context).apply {
            id = R.id.tv_comments_list
            layoutManager = LinearLayoutManager(context)
            itemAnimator = null
            clipToPadding = false
            setPadding(context.dp(12), 0, context.dp(12), context.dp(12))
        }
        grid = list
        list.adapter = object : RecyclerView.Adapter<CommentViewHolder>() {
            override fun getItemCount() = current.comments.size

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = CommentViewHolder(parent)

            override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
                val comment = current.comments[position]
                holder.bind(comment, true)
                styleComment(holder.itemView, compact = true)
                holder.itemView.apply {
                    isFocusable = true
                    (this as ViewGroup).descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setOnClickListener { showComment(holder.comment ?: comment) }
                    setOnFocusChangeListener { _, focused ->
                        if (focused) {
                            current.selected = holder.bindingAdapterPosition.coerceAtLeast(0)
                            if (current.selected >= current.comments.size - 3 && current.more) load(next = true)
                        }
                    }
                }
            }
        }
        column.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        footer = TvUi.button(context, context.getString(R.string.tv_more)) { load(next = true) }.apply {
            id = R.id.tv_comments_more
            gravity = Gravity.CENTER
        }
        column.addView(
            footer,
            LinearLayout.LayoutParams(-1, context.dp(40)).apply {
                setMargins(context.dp(16), 0, context.dp(16), context.dp(12))
            })
        body.addView(column, FrameLayout.LayoutParams(-1, -1))
        updateFooter()
        current.scroll?.let { list.layoutManager?.onRestoreInstanceState(it) }
        if (current.scroll == null) list.scrollToPosition(current.selected)
        list.post { if (!disposed) list.findViewHolderForAdapterPosition(current.selected)?.itemView?.requestFocus() }
    }

    private fun updateFooter() {
        footer?.apply {
            visibility = View.GONE
            text = context.getString(R.string.tv_more)
            isEnabled = true
        }
    }

    private fun showComment(comment: IPlatformComment) {
        page.scroll = grid?.layoutManager?.onSaveInstanceState()
        expanded = true
        title(context.getString(R.string.tv_comments), true)
        val column = menu()
        val holder = CommentViewHolder(column)
        holder.bind(comment, true)
        styleComment(holder.itemView, compact = false)
        holder.itemView.findViewById<View>(R.id.button_replies).visibility = View.GONE
        holder.itemView.findViewById<View>(R.id.text_author).apply {
            isFocusable = comment.author.url.isNotBlank()
            setBackgroundResource(R.drawable.tv_player_timeline_focus)
            setOnClickListener { channel(comment) }
        }
        holder.onAuthorClick.subscribe { channel(it) }
        holder.itemView.findViewById<View>(R.id.button_show)?.apply {
            isFocusable = true
            setBackgroundResource(R.drawable.tv_panel_item)
        }
        column.addView(holder.itemView)
        if ((comment.replyCount ?: 0) > 0)
            column.addView(
                option(context.getString(R.string.tv_view_replies, comment.replyCount)) {
                    pages.add(Page { replies(comment) })
                    expanded = false
                    title(context.getString(R.string.tv_replies), true)
                    load()
                }.apply { id = R.id.tv_comment_replies })
        (column.parent as ScrollView).apply {
            isFocusable = true
            requestFocus()
        }
    }

    private fun styleComment(view: View, compact: Boolean) {
        view.layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = context.dp(10) }
        view.setPadding(context.dp(12), context.dp(14), context.dp(12), context.dp(14))
        view.setBackgroundResource(R.drawable.tv_panel_item)
        view.findViewById<TextView>(R.id.text_body).apply {
            textSize = 14f
            maxLines = if (compact) 5 else Int.MAX_VALUE
            ellipsize = TextUtils.TruncateAt.END
            movementMethod = null
            isFocusable = false
            setLineSpacing(context.dp(3).toFloat(), 1f)
        }
        view.findViewById<TextView>(R.id.text_author).apply {
            textSize = 13f
            maxWidth = context.dp(146)
        }
        view.findViewById<TextView>(R.id.text_metadata).textSize = 11f
        view.findViewById<View>(R.id.image_copy).visibility = View.GONE
        view.findViewById<View>(R.id.button_delete).visibility = View.GONE
    }

    private class Page(val loader: suspend () -> IPager<IPlatformComment>) {
        val comments = mutableListOf<IPlatformComment>()
        var pager: IPager<IPlatformComment>? = null
        var more = false
        var selected = 0
        var scroll: Parcelable? = null
        var job: Job? = null
    }
}
