package com.futo.platformplayer.casting.models

import kotlinx.serialization.Serializable

@kotlinx.serialization.Serializable
data class FastCastPlayMessage(
    val container: String,
    val url: String? = null,
    val content: String? = null,
    val time: Int? = null
) { }

@kotlinx.serialization.Serializable
data class FastCastSeekMessage(
    val time: Int
) { }

@kotlinx.serialization.Serializable
data class FastCastPlaybackUpdateMessage(
    val time: Int,
    val state: Int
) { }


@Serializable
data class FastCastVolumeUpdateMessage(
    val volume: Double
)

@Serializable
data class FastCastSetVolumeMessage(
    val volume: Double
)