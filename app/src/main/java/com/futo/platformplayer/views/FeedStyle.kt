package com.futo.platformplayer.views

import com.futo.platformplayer.api.media.exceptions.UnknownPlatformException
import com.futo.platformplayer.api.media.models.contents.ContentType

enum class FeedStyle(val value: Int) {
    UNKNOWN(-1),
    THUMBNAIL(1),
    PREVIEW(2);



    companion object {
        val THUMBNAIL_HEIGHT = 115;
        val PREVIEW_HEIGHT = 310;

        fun fromInt(value: Int): FeedStyle
        {
            val result = FeedStyle.values().firstOrNull { it.value == value };
            if(result == null)
                throw UnknownPlatformException(value.toString());
            return result;
        }
    }
}