package com.futo.platformplayer.models

data class DiskUsage (
    val usage: Long,
    val available: Long
) {
    val percentage: Double = if((available + usage) > 0) usage.toDouble() / (usage + available) else 0.0;
}