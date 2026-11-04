package com.futo.platformplayer.tv.history

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.models.HistoryVideo
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvVideoAdapter
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal class TvHistoryAdapter(
    private val play: (HistoryVideo) -> Unit,
    private val options: (HistoryVideo) -> Unit,
    private val focusRail: () -> Unit,
    private val focused: (HistoryVideo, Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val entries = mutableListOf<Entry>()

    override fun getItemCount() = entries.size

    override fun getItemViewType(position: Int) = if (isHeader(position)) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
        val c = parent.context
        return if (type == 0)
            Header(
                TvUi.heading(c, "").apply {
                    textSize = 18f
                    setPadding(c.dp(8), c.dp(16), 0, c.dp(12))
                    layoutParams = RecyclerView.LayoutParams(-1, -2)
                })
        else Video(TvVideoAdapter.Card(TvUi.column(c)))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val entry = entries[position]
        val c = holder.itemView.context
        if (holder is Header) {
            holder.text.text = when (entry.date) {
                LocalDate.now() -> c.getString(R.string.tv_history_today)
                LocalDate.now().minusDays(1) -> c.getString(R.string.tv_history_yesterday)
                else -> entry.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            }
            return
        }
        holder as Video
        val item = checkNotNull(entry.item)
        val video = item.video
        val card = holder.card
        card.title.text = video.name
        card.author.text = video.author.name
        card.duration.visibility = if (video.isLive || video.duration > 0) View.VISIBLE else View.GONE
        card.duration.text = if (video.isLive) c.getString(R.string.tv_live) else TvUi.duration(video.duration)
        TvUi.load(card.image, video.thumbnails.getHQThumbnail())
        holder.progress.visibility = if (item.position > 0 && video.duration > 0) View.VISIBLE else View.GONE
        holder.progress.progress =
            if (video.duration > 0) (item.position.toDouble() / video.duration * 1000).toInt().coerceIn(0, 1000) else 0
        val time = item.date
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        holder.watched.text = time +
            when {
                TvHistoryStore.watched(item) -> "  ·  ${c.getString(R.string.tv_history_watched)}"
                item.position > 0 -> "  ·  ${c.getString(R.string.tv_history_resume, TvUi.duration(item.position))}"
                else -> ""
            }
        card.root.contentDescription = "${video.name}, ${video.author.name}, ${holder.watched.text}"
        card.root.setOnClickListener { play(item) }
        card.root.setOnLongClickListener {
            options(item)
            true
        }
        card.root.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) focused(item, holder.bindingAdapterPosition)
        }
        card.root.setOnKeyListener { _, key, event ->
            when {
                key == KeyEvent.KEYCODE_DPAD_LEFT && entry.column == 0 -> {
                    if (event.action == KeyEvent.ACTION_DOWN) focusRail()
                    true
                }
                key == KeyEvent.KEYCODE_MENU -> {
                    if (event.action == KeyEvent.ACTION_UP) options(item)
                    true
                }
                else -> false
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is Video) holder.card.requests.clear(holder.card.image)
    }

    fun submit(items: List<HistoryVideo>) {
        entries.clear()
        var previous: LocalDate? = null
        var column = 0
        items.forEach { item ->
            val date = item.date.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
            if (date != previous) {
                entries.add(Entry(date))
                column = 0
                previous = date
            }
            entries.add(Entry(date, item, column++ % 3))
        }
        notifyDataSetChanged()
    }

    fun indexOf(url: String?) = entries.indexOfFirst { it.item?.video?.url == url && it.item != null }

    fun firstVideo() = entries.indexOfFirst { it.item != null }

    fun isHeader(position: Int) = entries.getOrNull(position)?.item == null

    fun inFirstRow(position: Int) = position in 1..3

    private data class Entry(val date: LocalDate, val item: HistoryVideo? = null, val column: Int = 0)

    private class Header(val text: TextView) : RecyclerView.ViewHolder(text)

    private class Video(val card: TvVideoAdapter.Card) : RecyclerView.ViewHolder(card.root) {
        val progress = ProgressBar(card.root.context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(0xFFF1F1F1.toInt())
            progressBackgroundTintList = ColorStateList.valueOf(0xCC555555.toInt())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val watched = TvUi.text(card.root.context, "", 12f, true).apply {
            setPadding(context.dp(4), context.dp(5), context.dp(4), 0)
        }

        init {
            (card.image.parent as FrameLayout).addView(
                progress, FrameLayout.LayoutParams(-1, card.root.context.dp(3), Gravity.BOTTOM))
            card.root.addView(watched)
        }
    }
}
