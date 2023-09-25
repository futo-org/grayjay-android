package com.futo.platformplayer.models

@kotlinx.serialization.Serializable
data class Telemetry(
    val id: String,
    val applicationId: String,
    val versionCode: String,
    val versionName: String,
    val buildType: String,
    val debug: Boolean,
    val isUnstableBuild: Boolean,
    val time: Long,
    val brand: String,
    val manufacturer: String,
    val model: String
) { }