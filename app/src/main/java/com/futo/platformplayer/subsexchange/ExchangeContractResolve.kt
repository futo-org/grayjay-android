package com.futo.platformplayer.subsexchange

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExchangeContractResolve(
    @SerialName("PublicKey")
    val publicKey: String,
    @SerialName("Signature")
    val signature: String,
    @SerialName("Data")
    val data: String
)