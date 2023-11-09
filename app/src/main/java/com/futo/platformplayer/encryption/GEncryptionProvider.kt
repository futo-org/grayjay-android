package com.futo.platformplayer.encryption

class GEncryptionProvider {
    companion object {
        val instance: GEncryptionProviderV1 = GEncryptionProviderV1.instance;
        val version = 1;
    }
}