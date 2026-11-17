package com.futo.platformplayer.tv.fragments

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.tv.channel.TvChannelSource
import com.futo.platformplayer.tv.search.TvChannelSearchSource
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvUi.lines

class TvChannelSearchFragment : TvSearchFragment() {
    internal var channelSource: TvChannelSource = TvChannelSource.platform

    override val channelUrl: String get() = requireArguments().getString("url")!!

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        source = TvChannelSearchSource(channelUrl, channelSource)
    }

    override fun createHeading(context: Context): View {
        val row = TvUi.row(context)
        val avatar = TvUi.image(context).apply { background = null }
        Glide.with(this)
            .load(arguments?.getString("thumbnail"))
            .placeholder(R.drawable.placeholder_channel_thumbnail)
            .circleCrop()
            .into(avatar)
        row.addView(avatar, LinearLayout.LayoutParams(context.dp(56), context.dp(56)).apply {
            marginEnd = context.dp(18)
        })
        val identity = TvUi.column(context)
        identity.addView(TvUi.text(context, getString(R.string.tv_search_channel), 13f, true))
        identity.addView(TvUi.heading(context, arguments?.getString("name") ?: getString(R.string.tv_channel_title)).apply {
            textSize = 28f
            lines(1)
        })
        row.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }
}
