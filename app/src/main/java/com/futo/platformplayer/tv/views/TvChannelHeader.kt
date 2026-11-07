package com.futo.platformplayer.tv.views

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.text.HtmlCompat
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvUi.lines

internal class TvChannelHeader(context: Context, subscribe: () -> Unit) : LinearLayout(context) {
    private val banner = TvUi.image(context)
    private val artwork = FrameLayout(context)
    private val avatar = TvUi.image(context).apply {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) =
                outline.setOval(0, 0, view.width, view.height)
        }
    }
    private val title = TvUi.heading(context, "").apply {
        textSize = 30f
        lines(1)
    }
    private val metadata = TvUi.text(context, "", 13f, true).apply { lines(1) }
    private val description = TvUi.text(context, "", 13f, true).apply { lines(1) }
    val subscription = TvUi.button(context, context.getString(R.string.tv_subscribe), subscribe).apply {
        id = R.id.tv_channel_subscribe
        minWidth = context.dp(120)
        isEnabled = false
    }

    init {
        orientation = VERTICAL
        artwork.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF242C32.toInt(), 0xFF171B20.toInt()))
            .apply { cornerRadius = context.dp(12).toFloat() }
        artwork.clipToOutline = true
        artwork.addView(banner, FrameLayout.LayoutParams(-1, -1))
        artwork.addView(
            View(context).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0x55000000))
            },
            FrameLayout.LayoutParams(-1, -1))
        addView(artwork, LayoutParams(-1, context.dp(104)))
        val identity = TvUi.row(context).apply { setPadding(context.dp(4), context.dp(16), 0, context.dp(12)) }
        identity.addView(avatar, LayoutParams(context.dp(72), context.dp(72)).apply { marginEnd = context.dp(18) })
        val info = TvUi.column(context)
        info.addView(title)
        info.addView(metadata, LayoutParams(-1, -2).apply { topMargin = context.dp(4) })
        info.addView(description, LayoutParams(-1, -2).apply { topMargin = context.dp(6) })
        identity.addView(info, LayoutParams(0, -2, 1f).apply { marginEnd = context.dp(20) })
        identity.addView(subscription)
        addView(identity)
    }

    fun bind(channel: IPlatformChannel?, name: String?, thumbnail: String?, source: String) {
        title.text = channel?.name ?: name ?: context.getString(R.string.tv_channel_title)
        metadata.text = listOfNotNull(
            source.takeIf { it.isNotBlank() },
            channel?.subscribers
                ?.takeIf { it > 0 }
                ?.let { context.getString(R.string.tv_subscriber_count, it.toHumanNumber()) })
            .joinToString("  ·  ")
        description.text = channel?.description
            ?.let { HtmlCompat.fromHtml(it, 0).toString().replace('\n', ' ') }
            .orEmpty()
        description.visibility = if (description.text.isBlank()) GONE else VISIBLE
        TvUi.load(banner, channel?.banner)
        Glide.with(avatar)
            .load(channel?.thumbnail ?: thumbnail)
            .placeholder(R.drawable.placeholder_channel_thumbnail)
            .circleCrop()
            .into(avatar)
        artwork.visibility = if (channel?.banner.isNullOrBlank()) GONE else VISIBLE
    }

    fun collapseBanner(collapse: Boolean) {
        if (banner.drawable != null) artwork.visibility = if (collapse) GONE else VISIBLE
    }
}
