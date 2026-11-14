package com.futo.platformplayer.tv.views

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvUi.lines
import com.futo.platformplayer.views.others.CreatorThumbnail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class TvVideoInfoPanel(
    context: Context,
    video: IPlatformVideoDetails,
    openChannel: () -> Unit,
    close: () -> Unit
) : TvPlayerPanel(context, close) {
    private var subscriptionJob: Job? = null

    init {
        id = R.id.tv_info_panel
        title(context.getString(R.string.tv_video_info))
        val column = TvUi.column(context).apply { setPadding(context.dp(16), 0, context.dp(16), context.dp(16)) }
        body.addView(column, FrameLayout.LayoutParams(-1, -1))
        column.addView(TvUi.heading(context, video.name).apply {
            textSize = 20f
            lines(3)
        })
        val metadata = listOfNotNull(
            if (video.isLive) context.getString(R.string.tv_live) else video.duration.takeIf { it > 0 }?.let { TvUi.duration(it) },
            if (video.viewCount >= 0) context.getString(R.string.tv_view_count, TvUi.count(video.viewCount)) else null,
            TvUi.publishedDate(context, video.datetime))
        column.addView(TvUi.text(context, metadata.joinToString(" · "), 12f, true).apply {
            setPadding(0, context.dp(8), 0, context.dp(8))
        })
        val ratings = TvUi.row(context)
        when (val rating = video.rating) {
            is RatingLikes -> addRating(ratings, R.drawable.ic_thumb_up, R.string.tv_likes, rating.likes)
            is RatingLikeDislikes -> {
                addRating(ratings, R.drawable.ic_thumb_up, R.string.tv_likes, rating.likes)
                addRating(ratings, R.drawable.ic_thumb_down, R.string.tv_dislikes, rating.dislikes)
            }
        }
        val actions = TvUi.row(context)
        actions.addView(ratings, LinearLayout.LayoutParams(0, -2, 1f))
        val saved = TvUi.iconButton(context, R.drawable.ic_schedule, context.getString(R.string.tv_save)) {}.apply {
            id = R.id.tv_save
        }
        fun updateSaved() {
            val isSaved = StatePlaylists.instance.getWatchLaterFromUrl(video.url) != null
            saved.setImageResource(if (isSaved) R.drawable.ic_check else R.drawable.ic_schedule)
            saved.isSelected = isSaved
            saved.contentDescription = context.getString(if (isSaved) R.string.tv_saved else R.string.tv_save)
            saved.tooltipText = saved.contentDescription
        }
        updateSaved()
        saved.setOnClickListener {
            if (StatePlaylists.instance.getWatchLaterFromUrl(video.url) != null)
                StatePlaylists.instance.removeFromWatchLater(video.url, true)
            else StatePlaylists.instance.addToWatchLater(SerializedPlatformVideo.fromVideo(video), true)
            updateSaved()
        }
        actions.addView(saved)
        column.addView(actions)
        column.addView(View(context).apply { setBackgroundColor(0xFF333333.toInt()) },
            LinearLayout.LayoutParams(-1, context.dp(1)).apply { topMargin = context.dp(8); bottomMargin = context.dp(12) })
        column.addView(channelRow(video, openChannel))
        val descriptionTitle = TvUi.heading(context, context.getString(R.string.tv_description)).apply {
            textSize = 14f
            setTextColor(TvUi.muted)
        }
        column.addView(descriptionTitle, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = context.dp(20)
            bottomMargin = context.dp(8)
        })
        val description = ScrollView(context).apply {
            id = R.id.tv_info_description
            isFocusable = true
            isFillViewport = true
            contentDescription = context.getString(R.string.tv_description)
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(context.dp(12))
            setOnFocusChangeListener { _, focused ->
                descriptionTitle.setTextColor(if (focused) ContextCompat.getColor(context, R.color.colorPrimary) else TvUi.muted)
            }
            addView(TvUi.text(context,
                if (video.description.isBlank()) context.getString(R.string.tv_no_description)
                else HtmlCompat.fromHtml(video.description, HtmlCompat.FROM_HTML_MODE_COMPACT), 15f).apply {
                setPadding(0, 0, context.dp(12), context.dp(20))
                setLineSpacing(context.dp(4).toFloat(), 1f)
            })
        }
        column.addView(description, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    override fun onDetachedFromWindow() {
        subscriptionJob?.cancel()
        super.onDetachedFromWindow()
    }

    private fun channelRow(video: IPlatformVideoDetails, openChannel: () -> Unit): LinearLayout {
        val author = video.author
        val row = TvUi.row(context)
        val identity = TvUi.row(context).apply {
            id = R.id.tv_video_open_channel
            setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            if (author.url.isNotBlank()) {
                TvUi.focusable(this)
                setBackgroundResource(R.drawable.tv_card_focus)
                setOnClickListener { openChannel() }
            }
        }
        identity.addView(CreatorThumbnail(context).apply {
            if (author.thumbnail.isNullOrBlank()) setChannelImageResource(R.drawable.placeholder_channel_thumbnail, false)
            else setThumbnail(author.thumbnail, false)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }, LinearLayout.LayoutParams(context.dp(40), context.dp(40)))
        val names = TvUi.column(context)
        names.addView(TvUi.heading(context, author.name).apply { textSize = 15f; lines(2) })
        val subtitle = author.subscribers?.takeIf { it > 0 }?.let {
            context.getString(R.string.tv_subscriber_count, it.toHumanNumber())
        } ?: context.getString(R.string.tv_channel)
        if (author.url.isNotBlank())
            names.addView(TvUi.text(context, subtitle, 12f, true).apply {
                lines(1)
                setPadding(0, context.dp(4), 0, 0)
            })
        identity.addView(names, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(10) })
        identity.contentDescription = listOfNotNull(author.name, subtitle.takeIf { author.url.isNotBlank() }).joinToString(", ")
        row.addView(identity, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = context.dp(12) })
        if (author.url.isNotBlank()) {
            val subscribe = TvUi.button(context, "") {}.apply {
                id = R.id.tv_channel_subscribe
                minWidth = context.dp(120)
            }
            updateSubscribe(subscribe, author.url)
            subscribe.setOnClickListener { toggleSubscription(subscribe, author.url) }
            row.addView(subscribe)
        }
        return row
    }

    private fun updateSubscribe(button: TextView, url: String) {
        button.setText(if (StateSubscriptions.instance.isSubscribed(url)) R.string.tv_subscribed else R.string.tv_subscribe)
    }

    private fun toggleSubscription(button: TextView, url: String) {
        if (subscriptionJob?.isActive == true) return
        val owner = findViewTreeLifecycleOwner() ?: return
        subscriptionJob = owner.lifecycleScope.launch {
            try {
                val subscriptions = StateSubscriptions.instance
                val existing = subscriptions.getSubscription(url)
                if (existing != null) subscriptions.removeSubscription(existing.channel.url, true)
                else {
                    button.setText(R.string.tv_loading)
                    val channel = withContext(Dispatchers.IO) { StatePlatform.instance.getChannelLive(url) }
                    subscriptions.addSubscription(channel)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w("TvVideoInfo", "Failed to change subscription", error)
                UIDialogs.toast(context, context.getString(R.string.tv_video_action_error))
            } finally {
                updateSubscribe(button, url)
            }
        }
    }

    private fun addRating(row: LinearLayout, icon: Int, label: Int, count: Long) {
        if (count < 0) return
        val item = TvUi.row(context).apply {
            contentDescription = "${context.getString(label)}: ${TvUi.count(count)}"
        }
        item.addView(ImageView(context).apply {
            setImageResource(icon)
            setColorFilter(TvUi.muted)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(20), context.dp(20)))
        item.addView(TvUi.text(context, count.toHumanNumber(), 14f, true).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = context.dp(6) })
        row.addView(item, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = context.dp(24) })
    }
}
