package com.futo.platformplayer.tv.playlists

import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvUi.lines

internal class TvSavedVideosAdapter(
    private val play: (SerializedPlatformVideo, Int) -> Unit,
    private val options: (SerializedPlatformVideo, Int) -> Unit,
    private val focusActions: () -> Unit,
    private val focused: (String, Int) -> Unit
) : RecyclerView.Adapter<TvSavedVideosAdapter.Row>() {
    val videos = mutableListOf<SerializedPlatformVideo>()

    override fun getItemCount() = videos.size

    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Row(TvUi.row(parent.context))

    override fun onBindViewHolder(holder: Row, position: Int) {
        val video = videos[position]
        holder.content.tag = video.url
        holder.more.tag = "options:${video.url}"
        holder.number.text = (position + 1).toString()
        holder.title.text = video.name
        holder.author.text = video.author.name
        holder.duration.visibility = if (video.isLive || video.duration > 0) View.VISIBLE else View.GONE
        holder.duration.text = if (video.isLive) holder.root.context.getString(R.string.tv_live) else TvUi.duration(video.duration)
        if (video.isLive) holder.duration.setBackgroundResource(R.drawable.background_thumbnail_live)
        else holder.duration.setBackgroundColor(0xCC000000.toInt())
        holder.duration.setPadding(holder.root.context.dp(4), holder.root.context.dp(2), holder.root.context.dp(4), holder.root.context.dp(2))
        TvUi.load(holder.image, video.thumbnails.getHQThumbnail())
        holder.content.contentDescription = "${video.name}, ${video.author.name}"
        holder.more.contentDescription = holder.root.context.getString(R.string.tv_watch_later_video_options, video.name)
        holder.content.nextFocusRightId = holder.more.id
        holder.more.nextFocusLeftId = holder.content.id
        holder.content.setOnClickListener { play(video, holder.bindingAdapterPosition) }
        holder.content.setOnLongClickListener {
            options(video, holder.bindingAdapterPosition)
            true
        }
        holder.more.setOnClickListener { options(video, holder.bindingAdapterPosition) }
        val focus = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) focused(video.url, holder.bindingAdapterPosition)
        }
        holder.content.onFocusChangeListener = focus
        holder.more.onFocusChangeListener = focus
        holder.content.setOnKeyListener { _, key, event ->
            when (key) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) focusActions()
                    true
                }
                KeyEvent.KEYCODE_MENU -> {
                    if (event.action == KeyEvent.ACTION_UP) options(video, holder.bindingAdapterPosition)
                    true
                }
                else -> false
            }
        }
    }

    override fun onViewRecycled(holder: Row) = holder.requests.clear(holder.image)

    class Row(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val requests = Glide.with(root)
        val content = TvUi.row(root.context)
        val number = TvUi.text(root.context, "", 12f, true).apply { gravity = Gravity.CENTER }
        val image = TvUi.image(root.context)
        val title = TvUi.text(root.context, "", 15f).apply { lines(2) }
        val author = TvUi.text(root.context, "", 12f, true).apply { lines(1) }
        val duration = TvUi.text(root.context, "", 11f).apply {
            setBackgroundColor(0xCC000000.toInt())
            setPadding(context.dp(4), context.dp(2), context.dp(4), context.dp(2))
        }
        val more = TvUi.iconButton(
            root.context, R.drawable.ic_more, root.context.getString(R.string.tv_watch_later_options)) {}

        init {
            val c = root.context
            content.id = View.generateViewId()
            content.isFocusable = true
            content.isClickable = true
            content.setBackgroundResource(R.drawable.tv_panel_item)
            content.setPadding(c.dp(4), c.dp(8), c.dp(8), c.dp(8))
            content.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            content.addView(number, LinearLayout.LayoutParams(c.dp(24), -1))
            val picture = FrameLayout(c)
            picture.addView(image, FrameLayout.LayoutParams(-1, -1))
            picture.addView(
                duration,
                FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
                    setMargins(0, 0, c.dp(4), c.dp(4))
                })
            content.addView(picture, LinearLayout.LayoutParams(c.dp(144), c.dp(81)))
            val text = TvUi.column(c)
            text.addView(title)
            text.addView(author, LinearLayout.LayoutParams(-1, -2).apply { topMargin = c.dp(8) })
            content.addView(text, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = c.dp(14) })
            root.addView(content, LinearLayout.LayoutParams(0, -2, 1f))
            root.addView(more, LinearLayout.LayoutParams(c.dp(32), c.dp(36)).apply { marginStart = c.dp(6) })
            root.layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = c.dp(8) }
        }
    }
}
