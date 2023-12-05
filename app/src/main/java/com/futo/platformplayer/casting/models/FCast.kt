package com.futo.platformplayer.casting.models

import kotlinx.serialization.Serializable

@kotlinx.serialization.Serializable
data class FCastPlayMessage(
    val container: String,
    val url: String? = null,
    val content: String? = null,
    val time: Int? = null
) { }

@kotlinx.serialization.Serializable
data class FCastSeekMessage(
    val time: Int
) { }

@kotlinx.serialization.Serializable
data class FCastPlaybackUpdateMessage(
    val time: Int,
    val state: Int
) { }


@Serializable
data class FCastVolumeUpdateMessage(
    val volume: Double
)

@Serializable
data class FCastSetVolumeMessage(
    val volume: Double
)