package com.futo.platformplayer.views.items

import android.content.Context
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.futo.platformplayer.R
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.models.PlaylistDownloaded
import com.futo.platformplayer.withMaxSizePx

class PlaylistDownloadItem(context: Context, playlistName: String, playlistThumbnail: String?, val obj: Any): LinearLayout(context) {
    init { inflate(context, R.layout.list_downloaded_playlist, this) }

    var imageView: ImageView = findViewById(R.id.downloaded_playlist_image);
    var imageText: TextView = findViewById(R.id.downloaded_playlist_name);

    init {
        imageText.text = playlistName;
        Glide.with(imageView)
            .load(playlistThumbnail)
            .withMaxSizePx()
            .crossfade()
            .into(imageView);
    }
}