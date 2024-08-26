package com.futo.platformplayer.api.media.models.post

import com.futo.platformplayer.api.media.exceptions.UnknownPlatformException

enum class TextType(val value: Int) {
    RAW(0),
    HTML(1),
    MARKUP(2);

    companion object {
        fun fromInt(value: Int): TextType
        {
            val result = TextType.entries.firstOrNull { it.value == value };
            if(result == null)
                throw IllegalArgumentException("Unknown Texttype: $value");
            return result;
        }
    }
}