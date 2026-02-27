package com.futo.platformplayer.api.media.platforms.js

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SourceAuth(val cookieMap: HashMap<String, HashMap<String, String>>? = null, val headers: Map<String, Map<String, String>> = mapOf(), val userAgent: String? = null) {
    override fun toString(): String {
        return "(headers: '$headers', cookieString: '$cookieMap', userAgent: '$userAgent')";
    }

    fun toEncrypted(): String{
        return SourceEncrypted.fromDecrypted { serialize() }.toJson();
    }

    private fun serialize(): String {
        return Json.encodeToString(SerializedAuth(cookieMap, headers, userAgent));
    }

    companion object {
        val TAG = "SourceAuth";
        private val _json = Json { ignoreUnknownKeys = true };

        fun fromEncrypted(encrypted: String?): SourceAuth? {
            return SourceEncrypted.decryptEncrypted(encrypted) { deserialize(it) };
        }

        private fun deserialize(str: String): SourceAuth {
            val data = _json.decodeFromString<SerializedAuth>(str);
            return SourceAuth(data.cookieMap, data.headers, data.userAgent);
        }
    }

    @Serializable
    data class SerializedAuth(val cookieMap: HashMap<String, HashMap<String, String>>?,
                              val headers: Map<String, Map<String, String>> = mapOf(),
                              val userAgent: String? = null)
}