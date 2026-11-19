package com.futo.platformplayer.tv.sync

import android.graphics.Color
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.sync.internal.LinkType
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvUi.lines

internal class TvSyncDevicesAdapter(
    private val open: (TvSyncDevice) -> Unit,
    private val focused: (String) -> Unit,
    private val focusPairing: () -> Unit
) : ListAdapter<TvSyncDevice, TvSyncDevicesAdapter.Device>(DeviceDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        Device(TvUi.row(parent.context)).apply {
            val animation = root.onFocusChangeListener
            root.setOnFocusChangeListener { view, hasFocus ->
                animation?.onFocusChange(view, hasFocus)
                if (hasFocus) currentList.getOrNull(bindingAdapterPosition)?.let { focused(it.key) }
            }
            root.setOnClickListener { currentList.getOrNull(bindingAdapterPosition)?.let(open) }
            root.setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (event.action == KeyEvent.ACTION_DOWN) focusPairing()
                    true
                } else false
            }
        }

    override fun onBindViewHolder(holder: Device, position: Int) {
        val device = getItem(position)
        holder.name.text = device.name
        holder.status.text = holder.root.context.getString(
            when {
                !device.connected -> R.string.tv_sync_disconnected
                device.link == LinkType.Relayed -> R.string.tv_sync_connected_relay
                else -> R.string.tv_sync_connected_local
            })
        holder.status.setTextColor(if (device.connected) 0xFF91D5A4.toInt() else TvUi.muted)
        holder.root.contentDescription = "${device.name}, ${holder.status.text}"
        holder.root.tag = device.key
    }

    class Device(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val name = TvUi.text(root.context, "", 17f).apply { lines(1) }
        val status = TvUi.text(root.context, "", 13f, true)

        init {
            val c = root.context
            root.setPadding(c.dp(16), c.dp(18), c.dp(16), c.dp(18))
            root.addView(
                ImageView(c).apply {
                    setImageResource(R.drawable.ic_device)
                    setColorFilter(Color.WHITE)
                },
                LinearLayout.LayoutParams(c.dp(32), c.dp(32)).apply { marginEnd = c.dp(18) })
            root.addView(
                TvUi.column(c).apply {
                    addView(name)
                    addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = c.dp(8) })
                },
                LinearLayout.LayoutParams(0, -2, 1f))
            TvUi.focusable(root)
            root.setBackgroundResource(R.drawable.tv_panel_item)
            root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            root.layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = c.dp(12) }
        }
    }

    private class DeviceDiffCallback : DiffUtil.ItemCallback<TvSyncDevice>() {
        override fun areItemsTheSame(old: TvSyncDevice, new: TvSyncDevice) = old.key == new.key

        override fun areContentsTheSame(old: TvSyncDevice, new: TvSyncDevice) = old == new
    }
}
