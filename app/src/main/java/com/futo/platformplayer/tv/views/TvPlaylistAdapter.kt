package com.futo.platformplayer.tv.views

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist

internal class TvPlaylistAdapter(
    private val open: (IPlatformPlaylist) -> Unit,
    private val focusRail: () -> Unit,
    private val focused: (Int) -> Unit
) : RecyclerView.Adapter<TvVideoAdapter.Card>() {
    val playlists = mutableListOf<IPlatformPlaylist>()

    override fun getItemCount() = playlists.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        TvVideoAdapter.Card(TvUi.column(parent.context)).also { holder ->
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

    override fun onBindViewHolder(holder: TvVideoAdapter.Card, position: Int) {
        val playlist = playlists[position]
        holder.title.text = playlist.name
        holder.author.visibility = View.GONE
        holder.metadata.visibility = View.GONE
        holder.duration.text = if (playlist.videoCount >= 0) holder.root.context.getString(R.string.tv_playlist_count, playlist.videoCount)
        else holder.root.context.getString(R.string.playlists)
        TvUi.load(holder.image, playlist.thumbnail)
        holder.root.contentDescription =
            "${playlist.name}, ${holder.duration.text}, ${holder.root.context.getString(R.string.tv_view_playlist)}"
        holder.root.setOnClickListener { open(playlist) }
    }

    override fun onViewRecycled(holder: TvVideoAdapter.Card) = holder.requests.clear(holder.image)
}
