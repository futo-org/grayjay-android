package com.futo.platformplayer.api.media.models.streams.sources

interface IWidevineSource {
    val licenseUri: String
    val licenseHeaders: Map<String, String>?
    /**
     * Set this to true if the license response is Base64 encoded
     */
    val decodeLicenseResponse: Boolean
}