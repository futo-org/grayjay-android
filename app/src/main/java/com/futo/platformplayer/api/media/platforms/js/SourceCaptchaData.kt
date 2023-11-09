package com.futo.platformplayer.api.media.platforms.js

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SourceCaptchaData(val cookieMap: HashMap<String, HashMap<String, String>>? = null, val headers: Map<String, Map<String, String>> = mapOf()) {
    override fun toString(): String {
        return "(headers: '$headers', cookieString: '$cookieMap')";
    }

    fun toEncrypted(): String{
        return SourceEncrypted.fromDecrypted { serialize() }.toJson();
    }

    private fun serialize(): String {
        return Json.encodeToString(SerializedCaptchaData(cookieMap, headers));
    }

    companion object {
        val TAG = "SourceCaptchaData";

        fun fromEncrypted(encrypted: String?): SourceCaptchaData? {
            return SourceEncrypted.decryptEncrypted(encrypted) { deserialize(it) };
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