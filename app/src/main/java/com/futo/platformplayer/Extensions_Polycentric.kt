package com.futo.platformplayer

import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.states.AnnouncementType
import com.futo.platformplayer.states.StateAnnouncement
import com.futo.platformplayer.states.StatePlatform
import com.futo.polycentric.core.ProcessHandle
import com.futo.polycentric.core.Store
import com.futo.polycentric.core.SystemState
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

fun Protocol.Claim.resolveChannelUrls(): List<String> {
    return StatePlatform.instance.resolveChannelUrlsByClaimTemplates(this.claimType.toInt(), this.claimFieldsList.associate { Pair(it.key.toInt(), it.value) })
}

suspend fun ProcessHandle.fullyBackfillServersAnnounceExceptions() {
    val systemState = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(system))
    if (!systemState.servers.contains(PolycentricCache.SERVER)) {
        Logger.w("Backfill", "Polycentric prod server not added, adding it.")
        addServer(PolycentricCache.SERVER)
    }

    val exceptions = fullyBackfillServers()
    for (pair in exceptions) {
        val server = pair.key
        val exception = pair.value

        StateAnnouncement.instance.registerAnnouncement(
            "backfill-failed",
            "Backfill failed",
            "Failed to backfill server $server. $exception",
            AnnouncementType.SESSION_RECURRING
        );

        Logger.e("Backfill", "Failed to backfill server $server.", exception)
    }
}