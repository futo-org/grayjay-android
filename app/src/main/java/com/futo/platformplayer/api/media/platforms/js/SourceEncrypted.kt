package com.futo.platformplayer.api.media.platforms.js

import com.futo.platformplayer.encryption.GEncryptionProvider
import com.futo.platformplayer.encryption.GEncryptionProviderV0
import com.futo.platformplayer.logging.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.lang.Exception

@Serializable
data class SourceEncrypted(
    val encrypted: String,
    val version: Int = GEncryptionProvider.version
) {
    fun toJson(): String {
        return Json.encodeToString(this);
    }

    companion object {
        fun fromDecrypted(serializer: () -> String): SourceEncrypted {
            return SourceEncrypted(GEncryptionProvider.instance.encrypt(serializer()));
        }

        fun <T> decryptEncrypted(encrypted: String?, deserializer: (decrypted: String) -> T): T? {
            if(encrypted == null)
                return null;

            try {
                val encryptedSourceAuth = Json.decodeFromString<SourceEncrypted>(encrypted)
                if (encryptedSourceAuth.version != GEncryptionProvider.version) {
                    throw Exception("Invalid encryption version.");
                }

                val decrypted = GEncryptionProvider.instance.decrypt(encryptedSourceAuth.encrypted);
                try {
                    return deserializer(decrypted);
                } catch(ex: Throwable) {
                    Logger.e(SourceAuth.TAG, "Failed to deserialize SourceEncrypted<T>", ex);
                    return null;
                }
            } catch (e: Throwable) {
                //Try to fall back to old mechanism, remove this eventually
                if (!encrypted.contains("version")) {
                    val decrypted = GEncryptionProviderV0.instance.decrypt(encrypted);
                    try {
                        return deserializer(decrypted);
                    } catch (ex: Throwable) {
                        Logger.e(SourceAuth.TAG, "Failed to deserialize SourceEncrypted<T>", ex);
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }
    }
}