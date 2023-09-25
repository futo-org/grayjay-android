package com.futo.platformplayer.api.media.models.live

enum class LiveEventType(val value : Int) {
    UNKNOWN(0),
    COMMENT(1),
    EMOJIS(4),
    DONATION(5),
    VIEWCOUNT(10),
    RAID(100);

    companion object{
        fun fromInt(value : Int) : LiveEventType{
            return LiveEventType.values().first { it.value == value };
        }
    }
}