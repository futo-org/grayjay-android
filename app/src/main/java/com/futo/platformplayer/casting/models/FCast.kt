package com.futo.platformplayer.casting.models

import kotlinx.serialization.Serializable

@Serializable
data class FCastPlayMessage(
    val container: String,
    val url: String? = null,
    val content: String? = null,
    val time: Double? = null,
    val speed: Double? = null
) { }

@Serializable
data class FCastSeekMessage(
    val time: Double
) { }

@Serializable
data class FCastPlaybackUpdateMessage(
    val generationTime: Long,
    val time: Double,
    val duration: Double,
    val state: Int,
    val speed: Double
) { }


@Serializable
data class FCastVolumeUpdateMessage(
    val generationTime: Long,
    val volume: Double
)

@Serializable
data class FCastSetVolumeMessage(
    val volume: Double
)

@Serializable
data class FCastSetSpeedMessage(
    val speed: Double
)

@Serializable
data class FCastPlaybackErrorMessage(
    val message: String
)

@Serializable
data class FCastVersionMessage(
    val version: Long
)