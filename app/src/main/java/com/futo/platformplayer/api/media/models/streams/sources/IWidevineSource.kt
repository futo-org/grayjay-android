package com.futo.platformplayer.api.media.models.streams.sources

import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor

interface IWidevineSource {
    val licenseUri: String
    val hasLicenseRequestExecutor: Boolean
    // Widevine application certificate; when set the CDM uses privacy mode (encrypted client id).
    val serviceCertificate: ByteArray?
        get() = null
    fun getLicenseRequestExecutor(): JSRequestExecutor?
}