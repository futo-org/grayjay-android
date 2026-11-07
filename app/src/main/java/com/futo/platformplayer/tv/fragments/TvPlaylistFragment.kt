package com.futo.platformplayer.tv.fragments

import android.text.TextUtils
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.tv.channel.TvChannelSource
import com.futo.platformplayer.tv.playlists.TvPlaylistQueue
import com.futo.platformplayer.tv.views.TvUi

class TvPlaylistFragment : TvFeedFragment() {
    override val heading = R.string.playlists
    internal var source: TvChannelSource = TvChannelSource.platform

    override suspend fun fetch() = source.playlist(requireArguments().getString("url")!!)

    override fun openVideo(video: IPlatformVideo) {
        val queue = TvPlaylistQueue.start(
            videos, videos.indexOf(video), false,
            name = title.text.toString(), playlistUrl = requireArguments().getString("url"))
        host.navigate(TvPlayerFragment().apply {
            arguments = queue
        })
    }

    override fun configureHeader() {
        title.text = requireArguments().getString("name") ?: getString(heading)
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        header.addView(TvUi.text(requireContext(), requireArguments().getString("author").orEmpty(), 14f, true))
        super.configureHeader()
    }
}
