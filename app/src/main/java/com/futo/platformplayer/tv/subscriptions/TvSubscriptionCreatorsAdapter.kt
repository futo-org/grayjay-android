package com.futo.platformplayer.tv.subscriptions

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
import com.futo.platformplayer.api.media.models.channels.SerializedChannel
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvUi.lines

internal class TvSubscriptionCreatorsAdapter(
    private val open: (SerializedChannel) -> Unit,
    private val focused: (String) -> Unit,
    private val navigate: (Int) -> Unit
) : RecyclerView.Adapter<TvSubscriptionCreatorsAdapter.Creator>() {
    var channels = listOf<SerializedChannel>()

    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        Creator(TvUi.column(parent.context)).apply {
            val animation = root.onFocusChangeListener
            root.setOnFocusChangeListener { view, hasFocus ->
                animation?.onFocusChange(view, hasFocus)
                if (hasFocus) channels.getOrNull(bindingAdapterPosition)?.let { focused(it.url) }
            }
            root.setOnClickListener { channels.getOrNull(bindingAdapterPosition)?.let(open) }
            root.setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_DPAD_UP ||
                    key == KeyEvent.KEYCODE_DPAD_DOWN ||
                    (key == KeyEvent.KEYCODE_DPAD_LEFT && bindingAdapterPosition == 0)) {
                    if (event.action == KeyEvent.ACTION_DOWN) navigate(key)
                    true
                } else false
            }
        }

    override fun getItemCount() = channels.size

    override fun onBindViewHolder(holder: Creator, position: Int) {
        val channel = channels[position]
        holder.name.text = channel.name
        holder.root.contentDescription = channel.name
        holder.requests
            .load(channel.thumbnail)
            .placeholder(R.drawable.ic_person)
            .error(R.drawable.ic_person)
            .circleCrop()
            .into(holder.avatar)
    }

    override fun onViewRecycled(holder: Creator) {
        holder.requests.clear(holder.avatar)
    }

    class Creator(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val requests = Glide.with(root)
        val avatar = TvUi.image(root.context).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setOval(0, 0, view.width, view.height)
            }
        }
        val name = TvUi.text(root.context, "", 13f).apply {
            lines(1)
            gravity = Gravity.CENTER
        }

        init {
            val c = root.context
            root.gravity = Gravity.CENTER
            root.setPadding(c.dp(8), c.dp(8), c.dp(8), c.dp(8))
            root.addView(avatar, LinearLayout.LayoutParams(c.dp(60), c.dp(60)))
            root.addView(name, LinearLayout.LayoutParams(-1, -2).apply { topMargin = c.dp(8) })
            root.layoutParams = RecyclerView.LayoutParams(c.dp(104), c.dp(108)).apply { marginEnd = c.dp(8) }
            TvUi.focusable(root)
            root.setBackgroundResource(R.drawable.tv_card_focus)
            root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    }
}
