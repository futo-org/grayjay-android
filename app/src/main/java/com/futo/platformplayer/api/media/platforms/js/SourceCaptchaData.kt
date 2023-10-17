package com.futo.platformplayer.api.media.platforms.js

import com.futo.platformplayer.encryption.EncryptionProvider
import com.futo.platformplayer.logging.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SourceCaptchaData(val cookieMap: HashMap<String, HashMap<String, String>>? = null, val headers: Map<String, Map<String, String>> = mapOf()) {
    override fun toString(): String {
        return "(headers: '$headers', cookieString: '$cookieMap')";
    }

    fun toEncrypted(): String{
        return EncryptionProvider.instance.encrypt(serialize());
    }

    private fun serialize(): String {
        return Json.encodeToString(SerializedCaptchaData(cookieMap, headers));
    }

    companion object {
        val TAG = "SourceAuth";

        fun fromEncrypted(encrypted: String?): SourceCaptchaData? {
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

        fun deserialize(str: String): SourceCaptchaData {
            val data = Json.decodeFromString<SerializedCaptchaData>(str);
            return SourceCaptchaData(data.cookieMap, data.headers);
        }
    }

    @Serializable
    data class SerializedCaptchaData(val cookieMap: HashMap<String, HashMap<String, String>>?,
                              val headers: Map<String, Map<String, String>> = mapOf())
}