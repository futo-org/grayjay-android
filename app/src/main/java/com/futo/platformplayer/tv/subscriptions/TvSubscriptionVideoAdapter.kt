package com.futo.platformplayer.tv.subscriptions

import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.tv.views.TvVideoAdapter

internal class TvSubscriptionVideoAdapter(
    open: (IPlatformVideo) -> Unit,
    focusRail: () -> Unit,
    options: (IPlatformVideo) -> Unit,
    focused: (Int) -> Unit
) : RecyclerView.Adapter<TvVideoAdapter.Card>() {
    private val cards = TvVideoAdapter(open, focusRail, options = options, focused = focused)
    val videos
        get() = cards.videos

    override fun getItemCount() = cards.itemCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = cards.onCreateViewHolder(parent, viewType)

    override fun onBindViewHolder(holder: TvVideoAdapter.Card, position: Int) {
        cards.onBindViewHolder(holder, position)
        val video = videos[position]
        val published = video.datetime?.let {
            DateUtils.getRelativeTimeSpanString(
                it.toInstant().toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        }
        holder.author.text = listOfNotNull(video.author.name, published).joinToString(" · ")
        holder.duration.visibility = if (video.isLive || video.duration > 0) View.VISIBLE else View.GONE
    }

    override fun onViewRecycled(holder: TvVideoAdapter.Card) = cards.onViewRecycled(holder)
}
