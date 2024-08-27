package com.futo.platformplayer.api.media.models.contents

import com.futo.platformplayer.api.media.exceptions.UnknownPlatformException

enum class ContentType(val value: Int) {
    UNKNOWN(0),
    MEDIA(1),
    POST(2),
    ARTICLE(3),
    PLAYLIST(4),

    URL(9),

    NESTED_VIDEO(11),

    LOCKED(70),

    PLACEHOLDER(90),
    DEFERRED(91);

    companion object {
        fun fromInt(value: Int): ContentType
        {
            val result = ContentType.entries.firstOrNull { it.value == value };
            if(result == null)
                throw UnknownPlatformException(value.toString());
            return result;
        }
    }
}