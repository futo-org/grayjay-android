package com.futo.platformplayer.api.media.models.chapters

import com.futo.platformplayer.api.media.exceptions.UnknownPlatformException
import com.futo.platformplayer.api.media.models.contents.ContentType

interface IChapter {
    val name: String;
    val type: ChapterType;
    val timeStart: Double;
    val timeEnd: Double;
}

enum class ChapterType(val value: Int) {
    NORMAL(0),

    SKIPPABLE(5),
    SKIP(6);




    companion object {
        fun fromInt(value: Int): ChapterType
        {
            val result = ChapterType.values().firstOrNull { it.value == value };
            if(result == null)
                throw UnknownPlatformException(value.toString());
            return result;
        }
    }
}