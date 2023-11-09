package com.futo.platformplayer.api.media.platforms.js

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SourceAuth(val cookieMap: HashMap<String, HashMap<String, String>>? = null, val headers: Map<String, Map<String, String>> = mapOf()) {
    override fun toString(): String {
        return "(headers: '$headers', cookieString: '$cookieMap')";
    }

    fun toEncrypted(): String{
        return SourceEncrypted.fromDecrypted { serialize() }.toJson();
    }

    private fun serialize(): String {
        return Json.encodeToString(SerializedAuth(cookieMap, headers));
    }

    companion object {
        val TAG = "SourceAuth";

        fun fromEncrypted(encrypted: String?): SourceAuth? {
            return SourceEncrypted.decryptEncrypted(encrypted) { deserialize(it) };
        }

        private fun deserialize(str: String): SourceAuth {
            val data = Json.decodeFromString<SerializedAuth>(str);
            return SourceAuth(data.cookieMap, data.headers);
        }
    }

    @Serializable
    data class SerializedAuth(val cookieMap: HashMap<String, HashMap<String, String>>?,
                              val headers: Map<String, Map<String, String>> = mapOf())
}