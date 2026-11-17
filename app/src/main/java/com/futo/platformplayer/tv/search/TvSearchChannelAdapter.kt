package com.futo.platformplayer.tv.search

import android.graphics.Outline
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.IPlatformChannelContent
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvUi.lines

internal class TvSearchChannelAdapter(
    private val open: (IPlatformChannelContent) -> Unit,
    private val focusRail: () -> Unit,
    private val focused: (Int) -> Unit
) : RecyclerView.Adapter<TvSearchChannelAdapter.Card>() {
    val channels = mutableListOf<IPlatformChannelContent>()

    override fun getItemCount() = channels.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Card(TvUi.column(parent.context)).also { holder ->
            val animation = holder.root.onFocusChangeListener
            holder.root.setOnFocusChangeListener { view, hasFocus ->
                animation?.onFocusChange(view, hasFocus)
                if (hasFocus && holder.bindingAdapterPosition != RecyclerView.NO_POSITION)
                    focused(holder.bindingAdapterPosition)
            }
            holder.root.setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_DPAD_LEFT && holder.bindingAdapterPosition % 3 == 0) {
                    if (event.action == KeyEvent.ACTION_DOWN) focusRail()
                    true
                } else false
            }
        }

    override fun onBindViewHolder(holder: Card, position: Int) {
        val channel = channels[position]
        holder.name.text = channel.name
        holder.metadata.text = channel.subscribers
            ?.takeIf { it > 0 }
            ?.let {
                holder.root.context.getString(R.string.tv_subscriber_count, it.toHumanNumber())
            } ?: channel.id.platform
        Glide.with(holder.image)
            .load(channel.thumbnail)
            .placeholder(R.drawable.placeholder_channel_thumbnail)
            .circleCrop()
            .into(holder.image)
        holder.root.contentDescription = "${channel.name}, ${holder.metadata.text}"
        holder.root.setOnClickListener { open(channel) }
    }

    override fun onViewRecycled(holder: Card) {
        Glide.with(holder.image).clear(holder.image)
    }

    class Card(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val image = TvUi.image(root.context).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setOval(0, 0, view.width, view.height)
            }
        }
        val name = TvUi.text(root.context, "", 18f).apply {
            gravity = Gravity.CENTER
            lines(2)
            minLines = 2
        }
        val metadata = TvUi.text(root.context, "", 13f, true).apply {
            gravity = Gravity.CENTER
            lines(1)
        }

        init {
            val context = root.context
            root.gravity = Gravity.CENTER_HORIZONTAL
            root.setPadding(context.dp(16), context.dp(20), context.dp(16), context.dp(18))
            root.addView(image, LinearLayout.LayoutParams(context.dp(112), context.dp(112)))
            root.addView(name, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(16) })
            root.addView(metadata, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(6) })
            root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            TvUi.focusable(root)
            root.setBackgroundResource(R.drawable.tv_panel_item)
            root.layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                setMargins(context.dp(6), context.dp(6), context.dp(10), context.dp(12))
            }
        }
    }
}
