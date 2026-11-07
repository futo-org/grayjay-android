package com.futo.platformplayer.tv.playlists

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvVideoAdapter

internal class TvSavedPlaylistsAdapter(
    private val open: (Playlist) -> Unit,
    private val options: (Playlist) -> Unit,
    private val focusRail: () -> Unit,
    private val focused: (String) -> Unit
) : RecyclerView.Adapter<TvVideoAdapter.Card>() {
    val playlists = mutableListOf<Playlist>()

    override fun getItemCount() = playlists.size

    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        TvVideoAdapter.Card(TvUi.column(parent.context)).also { holder ->
            val animation = holder.root.onFocusChangeListener
            holder.root.setOnFocusChangeListener { view, hasFocus ->
                animation?.onFocusChange(view, hasFocus)
                if (hasFocus) playlists.getOrNull(holder.bindingAdapterPosition)?.let { focused(it.id) }
            }
            holder.root.setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_DPAD_LEFT && holder.bindingAdapterPosition % 3 == 0) {
                    if (event.action == KeyEvent.ACTION_DOWN) focusRail()
                    true
                } else if (key == KeyEvent.KEYCODE_MENU) {
                    if (event.action == KeyEvent.ACTION_UP)
                        playlists.getOrNull(holder.bindingAdapterPosition)?.let(options)
                    true
                } else false
            }
            holder.root.setOnLongClickListener {
                playlists.getOrNull(holder.bindingAdapterPosition)?.let(options)
                true
            }
        }

    override fun onBindViewHolder(holder: TvVideoAdapter.Card, position: Int) {
        val playlist = playlists[position]
        val c = holder.root.context
        holder.root.tag = playlist.id
        holder.title.text = playlist.name
        holder.duration.text = c.resources.getQuantityString(R.plurals.tv_watch_later_count, playlist.videos.size, playlist.videos.size)
        holder.author.text = c.getString(R.string.tv_view_playlist)
        val thumbnail = playlist.videos.firstOrNull()?.thumbnails?.getHQThumbnail()
        if (thumbnail != null) {
            holder.image.scaleType = ImageView.ScaleType.CENTER_CROP
            holder.image.setPadding(0, 0, 0, 0)
            TvUi.load(holder.image, thumbnail)
        } else {
            holder.requests.clear(holder.image)
            holder.image.scaleType = ImageView.ScaleType.FIT_CENTER
            val padding = c.dp(42)
            holder.image.setPadding(padding, padding, padding, padding)
            holder.image.setImageResource(R.drawable.ic_playlist)
        }
        holder.root.contentDescription = "${playlist.name}, ${holder.duration.text}"
        holder.root.setOnClickListener { open(playlist) }
    }

    override fun onViewRecycled(holder: TvVideoAdapter.Card) = holder.requests.clear(holder.image)
}
