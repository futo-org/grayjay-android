package com.futo.platformplayer.api.media.models.ratings

enum class RatingType(val value : Int) {
    UNKNOWN(0),
    LIKES(1),
    LIKEDISLIKES(2),
    SCALE(3);

    companion object{
        fun fromInt(value : Int) : RatingType{
            return RatingType.entries.first { it.value == value };
        }
    }
}