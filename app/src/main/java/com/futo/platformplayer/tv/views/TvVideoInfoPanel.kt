package com.futo.platformplayer.tv.views

import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.text.HtmlCompat
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvUi.lines
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal class TvVideoInfoPanel(
    context: Context,
    video: IPlatformVideoDetails,
    openChannel: () -> Unit,
    close: () -> Unit
) : TvPlayerPanel(context, close) {
    init {
        id = R.id.tv_info_panel
        title(context.getString(R.string.tv_video_info))
        val column = TvUi.column(context).apply { setPadding(context.dp(16), 0, context.dp(16), context.dp(16)) }
        body.addView(column, FrameLayout.LayoutParams(-1, -1))
        column.addView(TvUi.heading(context, video.name).apply {
            textSize = 20f
            lines(3)
        })
        column.addView(TvUi.text(context, video.author.name, 14f, true).apply {
            lines(1)
            setPadding(0, context.dp(8), 0, 0)
        })
        val metadata = listOfNotNull(
            if (video.isLive) context.getString(R.string.tv_live) else video.duration.takeIf { it > 0 }?.let { TvUi.duration(it) },
            if (video.viewCount >= 0) context.getString(R.string.tv_view_count, TvUi.count(video.viewCount)) else null,
            video.datetime?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
        column.addView(TvUi.text(context, metadata.joinToString(" · "), 12f, true).apply {
            setPadding(0, context.dp(8), 0, context.dp(8))
        })
        val actions = TvUi.row(context)
        if (video.author.url.isNotBlank())
            actions.addView(TvUi.button(context, context.getString(R.string.tv_channel), openChannel))
        val saved = TvUi.button(context, "") {}.apply { id = R.id.tv_save }
        fun updateSaved() {
            saved.setText(if (StatePlaylists.instance.getWatchLaterFromUrl(video.url) != null) R.string.tv_saved else R.string.tv_save)
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
        val description = ScrollView(context).apply {
            id = R.id.tv_info_description
            isFocusable = true
            isFillViewport = true
            contentDescription = context.getString(R.string.tv_description)
            setBackgroundResource(R.drawable.tv_channel_reading)
            addView(TvUi.text(context,
                if (video.description.isBlank()) context.getString(R.string.tv_no_description)
                else HtmlCompat.fromHtml(video.description, HtmlCompat.FROM_HTML_MODE_COMPACT), 15f).apply {
                setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(20))
                setLineSpacing(context.dp(4).toFloat(), 1f)
            })
        }
        column.addView(description, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = context.dp(8) })
    }
}
