package com.futo.platformplayer.tv.views

import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvUi.lines

internal class TvVideoAdapter(
    private val open: (IPlatformVideo) -> Unit,
    private val focusRail: () -> Unit,
    private val showAuthor: Boolean = true,
    private val options: (IPlatformVideo) -> Unit,
    private val focused: (Int) -> Unit
) : RecyclerView.Adapter<TvVideoAdapter.Card>() {
    val videos = mutableListOf<IPlatformVideo>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Card(TvUi.column(parent.context))

    override fun getItemCount() = videos.size

    override fun onBindViewHolder(holder: Card, position: Int) {
        val video = videos[position]
        holder.title.text = video.name
        holder.author.text = video.author.name
        holder.author.visibility = if (showAuthor) View.VISIBLE else View.GONE
        holder.duration.text = if (video.isLive) holder.root.context.getString(R.string.tv_live) else TvUi.duration(video.duration)
        holder.duration.visibility = if (video.isLive || video.duration > 0) View.VISIBLE else View.GONE
        TvUi.load(holder.image, video.thumbnails.getHQThumbnail())
        holder.requests.clear(holder.sourceIcon)
        val sourceIcon = StatePlatform.instance.getPlatformIcon(video.id.pluginId)
        holder.sourceIcon.visibility = if (sourceIcon == null) View.GONE else View.VISIBLE
        sourceIcon?.setImageView(holder.sourceIcon)
        holder.root.contentDescription = "${video.name}, ${video.author.name}"
        holder.root.setOnClickListener { open(video) }
        holder.root.setOnLongClickListener {
            options(video)
            true
        }
        val animation = holder.root.onFocusChangeListener
        // Hold only the original animation listener when a recycled card is bound again.
        if (holder.root.getTag(R.id.tv_card_focus) == null) {
            holder.root.setTag(R.id.tv_card_focus, animation)
            holder.root.setOnFocusChangeListener { view, hasFocus ->
                animation?.onFocusChange(view, hasFocus)
                if (hasFocus && holder.bindingAdapterPosition != RecyclerView.NO_POSITION)
                    focused(holder.bindingAdapterPosition)
            }
        }
        holder.root.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_DPAD_LEFT && holder.bindingAdapterPosition % 3 == 0) {
                if (event.action == KeyEvent.ACTION_DOWN) focusRail()
                true
            } else if (key == KeyEvent.KEYCODE_MENU) {
                if (event.action == KeyEvent.ACTION_UP) options(video)
                true
            } else false
        }
    }

    override fun onViewRecycled(holder: Card) {
        holder.requests.clear(holder.image)
        holder.requests.clear(holder.sourceIcon)
    }

    class Card(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val requests = Glide.with(root)
        val image = TvUi.image(root.context)
        val sourceIcon = ImageView(root.context).apply {
            id = R.id.tv_video_source_icon
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
        }
        val title = TvUi.text(root.context, "", 16f).apply {
            lines(2)
            minLines = 2
        }
        val author = TvUi.text(root.context, "", 13f, true).apply { lines(1) }
        val duration = TvUi.text(root.context, "", 12f).apply {
            setBackgroundColor(0xCC000000.toInt())
            setPadding(context.dp(5), context.dp(2), context.dp(5), context.dp(2))
        }

        init {
            val context = root.context
            root.setPadding(context.dp(3), context.dp(3), context.dp(3), context.dp(8))
            val picture = object : FrameLayout(context) {
                override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                    val width = MeasureSpec.getSize(widthSpec)
                    super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY))
                }
            }.apply {
                isDuplicateParentStateEnabled = true
                foreground = ContextCompat.getDrawable(context, R.drawable.tv_card_focus)
                setPadding(context.dp(3), context.dp(3), context.dp(3), context.dp(3))
            }
            picture.addView(image, FrameLayout.LayoutParams(-1, -1))
            picture.addView(
                sourceIcon,
                FrameLayout.LayoutParams(context.dp(28), context.dp(28), Gravity.BOTTOM or Gravity.START).apply {
                    setMargins(context.dp(7), 0, 0, context.dp(7))
                })
            picture.addView(
                duration,
                FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
                    setMargins(0, 0, context.dp(7), context.dp(7))
                })
            root.addView(picture, LinearLayout.LayoutParams(-1, -2))
            root.addView(
                title,
                LinearLayout.LayoutParams(-1, -2).apply { setMargins(context.dp(4), context.dp(9), context.dp(4), 0) })
            root.addView(
                author,
                LinearLayout.LayoutParams(-1, -2).apply { setMargins(context.dp(4), context.dp(3), context.dp(4), 0) })
            root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            TvUi.focusable(root)
            root.background = null
            root.layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                setMargins(context.dp(6), context.dp(6), context.dp(10), context.dp(12))
            }
        }
    }
}
