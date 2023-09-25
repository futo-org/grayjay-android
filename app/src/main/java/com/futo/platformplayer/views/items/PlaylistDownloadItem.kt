package com.futo.platformplayer.views.items

import android.content.Context
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.models.PlaylistDownloaded

class PlaylistDownloadItem(context: Context, val playlist: PlaylistDownloaded): LinearLayout(context) {
    init { inflate(context, R.layout.list_downloaded_playlist, this) }

    var imageView: ImageView = findViewById(R.id.downloaded_playlist_image);
    var imageText: TextView = findViewById(R.id.downloaded_playlist_name);

    init {
        imageText.text = playlist.playlist.name;
        Glide.with(imageView)
            .load(playlist.playlist.videos.firstOrNull()?.thumbnails?.getHQThumbnail())
            .crossfade()
            .into(imageView);
    }
}