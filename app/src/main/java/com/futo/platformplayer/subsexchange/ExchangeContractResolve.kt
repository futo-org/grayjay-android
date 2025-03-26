package com.futo.platformplayer.subsexchange

import kotlinx.serialization.Serializable

@Serializable
data class ExchangeContractResolve(
    val publicKey: String,
    val signature: String,
    val data: String
)