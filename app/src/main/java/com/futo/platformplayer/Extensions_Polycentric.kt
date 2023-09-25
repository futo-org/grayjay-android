package com.futo.platformplayer

import com.futo.platformplayer.states.StatePlatform
import userpackage.Protocol
import kotlin.math.abs
import kotlin.math.min

fun Protocol.ImageBundle?.selectBestImage(desiredPixelCount: Int): Protocol.ImageManifest? {
    if (this == null) {
        return null
    }

    val maximumFileSize = min(10 * desiredPixelCount, 5 * 1024 * 1024)
    return imageManifestsList.mapNotNull { if (it.byteCount > maximumFileSize) null else it }
        .minByOrNull { abs(it.width * it.height - desiredPixelCount) }
}

fun Protocol.ImageBundle?.selectLowestResolutionImage(): Protocol.ImageManifest? {
    if (this == null) {
        return null
    }

    val maximumFileSize = 5 * 1024 * 1024;
    return imageManifestsList.filter { it.byteCount < maximumFileSize }.minByOrNull { abs(it.width * it.height) }
}

fun Protocol.ImageBundle?.selectHighestResolutionImage(): Protocol.ImageManifest? {
    if (this == null) {
        return null
    }

    val maximumFileSize = 5 * 1024 * 1024;
    return imageManifestsList.filter { it.byteCount < maximumFileSize }.maxByOrNull { abs(it.width * it.height) }
}

fun Protocol.Claim.resolveChannelUrl(): String? {
    return StatePlatform.instance.resolveChannelUrlByClaimTemplates(this.claimType.toInt(), this.claimFieldsList.associate { Pair(it.key.toInt(), it.value) })
}