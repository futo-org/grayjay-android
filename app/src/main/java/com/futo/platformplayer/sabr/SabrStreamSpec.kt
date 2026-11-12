package com.futo.platformplayer.sabr

import com.futo.platformplayer.api.media.models.modifier.IRequestModifier
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.sabr.proto.ClientInfo

class SabrStreamSpec(
    val httpClientFactory: () -> ManagedHttpClient,
    val ownsHttpClient: Boolean = false,
    val serverAbrStreamingUrl: String,
    val ustreamerConfig: ByteArray,
    val videoId: String,
    val isLive: Boolean,
    val durationUs: Long,
    val videoFormats: List<SabrFormat>,
    val audioFormats: List<SabrFormat>,
    val poToken: String?,
    val clientName: Int,
    val clientVersion: String,
    val osName: String,
    val osVersion: String
) {
    fun buildClientInfo(): ClientInfo = ClientInfo.newBuilder()
        .setClientName(clientName)
        .setClientVersion(clientVersion)
        .setOsName(osName)
        .setOsVersion(osVersion)
        .build()

    fun createSession(): SabrSession = SabrSession(
        httpClientFactory(),
        serverAbrStreamingUrl,
        ustreamerConfig,
        videoId,
        buildClientInfo(),
        poToken,
        isLive,
        durationUs,
        ownsHttpClient
    )
}
