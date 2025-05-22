package com.futo.platformplayer.api.media.platforms.local.models

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.provider.MediaStore.Video

class MediaStoreVideo {


    companion object {
        val URI = MediaStore.Files.getContentUri("external");
        val PROJECTION = arrayOf(Video.Media._ID, Video.Media.TITLE, Video.Media.DURATION, Video.Media.HEIGHT, Video.Media.WIDTH, Video.Media.MIME_TYPE);
        val ORDER = MediaStore.Video.Media.TITLE;

        fun readMediaStoreVideo(cursor: Cursor) {

        }

        fun query(context: Context, selection: String, args: Array<String>, order: String? = null): Cursor? {
            val cursor = context.contentResolver.query(URI, PROJECTION, selection, args, order ?: ORDER, null);
            return cursor;
        }
    }
}