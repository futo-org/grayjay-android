package com.futo.platformplayer.api.media.platforms.js

import com.futo.platformplayer.encryption.EncryptionProvider
import com.futo.platformplayer.logging.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


data class SourceAuth(val cookieMap: HashMap<String, HashMap<String, String>>? = null, val headers: Map<String, Map<String, String>> = mapOf()) {
    override fun toString(): String {
        return "(headers: '$headers', cookieString: '$cookieMap')";
    }

    fun toEncrypted(): String{
        return EncryptionProvider.instance.encrypt(serialize());
    }

    private fun serialize(): String {
        return Json.encodeToString(SerializedAuth(cookieMap, headers));
    }

    companion object {
        val TAG = "SourceAuth";

        fun fromEncrypted(encrypted: String?): SourceAuth? {
            if(encrypted == null)
                return null;

            val decrypted = EncryptionProvider.instance.decrypt(encrypted);
            try {
                return deserialize(decrypted);
            }
            catch(ex: Throwable) {
                Logger.e(TAG, "Failed to deserialize authentication", ex);
                return null;
            }
        }

        fun deserialize(str: String): SourceAuth {
            val data = Json.decodeFromString<SerializedAuth>(str);
            return SourceAuth(data.cookieMap, data.headers);
        }
    }

    @Serializable
    data class SerializedAuth(val cookieMap: HashMap<String, HashMap<String, String>>?,
                              val headers: Map<String, Map<String, String>> = mapOf())
}