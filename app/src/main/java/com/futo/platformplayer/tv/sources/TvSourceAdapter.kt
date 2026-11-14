package com.futo.platformplayer.tv.sources

import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvUi.lines

internal class TvSourceAdapter(
    private val open: (TvSourceInfo) -> Unit,
    private val rail: () -> Unit,
    private val focused: (Int) -> Unit
) : RecyclerView.Adapter<TvSourceAdapter.Card>() {
    val sources = mutableListOf<TvSourceInfo>()

    override fun getItemCount() = sources.size

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
                    if (event.action == KeyEvent.ACTION_DOWN) rail()
                    true
                } else false
            }
        }

    override fun onBindViewHolder(holder: Card, position: Int) {
        val item = sources[position]
        holder.name.text = item.name
        holder.status.text = holder.root.context.getString(
            when {
                !item.installed -> R.string.tv_source_available
                item.enabled -> R.string.tv_enabled
                else -> R.string.tv_disabled
            })
        item.icon?.setImageView(holder.icon, R.drawable.ic_sources)
            ?: holder.icon.setImageResource(R.drawable.ic_sources)
        holder.root.contentDescription = "${item.name}, ${holder.status.text}"
        holder.root.setOnClickListener { open(item) }
    }

    override fun onViewRecycled(holder: Card) {
        holder.requests.clear(holder.icon)
    }

    class Card(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val icon = ImageView(root.context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val requests = Glide.with(icon)
        val name = TvUi.text(root.context, "", 20f).apply {
            lines(1)
            gravity = Gravity.CENTER
        }
        val status = TvUi.text(root.context, "", 13f, true).apply {
            lines(1)
            gravity = Gravity.CENTER
        }

        init {
            val c = root.context
            root.gravity = Gravity.CENTER_HORIZONTAL
            root.setPadding(c.dp(18), c.dp(24), c.dp(18), c.dp(20))
            root.addView(icon, LinearLayout.LayoutParams(c.dp(64), c.dp(64)))
            root.addView(name, LinearLayout.LayoutParams(-1, -2).apply { topMargin = c.dp(16) })
            root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = c.dp(6) })
            root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            TvUi.focusable(root)
            root.setBackgroundResource(R.drawable.tv_panel_item)
            root.layoutParams = RecyclerView.LayoutParams(-1, -2).apply { setMargins(c.dp(6), c.dp(6), c.dp(10), c.dp(12)) }
        }
    }
}
