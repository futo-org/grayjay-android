package com.futo.platformplayer.api.media.models.channels

import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.Serializer
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.structures.IPager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@kotlinx.serialization.Serializable
class SerializedChannel(
    override val id: PlatformID,
    override val name: String,
    override val thumbnail: String?,
    override val banner: String?,
    override val subscribers: Long,
    override val description: String?,
    override val url: String,
    override val links: Map<String, String>,
    override val urlAlternatives: List<String> = listOf()
) : IPlatformChannel {

    fun toJson(): String {
        return Json.encodeToString(this);
    }

    fun fromJson(str: String): SerializedChannel {
        return Serializer.json.decodeFromString<SerializedChannel>(str);
    }
    fun fromJsonArray(str: String): Array<SerializedChannel> {
        return Serializer.json.decodeFromString<Array<SerializedChannel>>(str);
    }

    override fun getContents(client: IPlatformClient): IPager<IPlatformContent> {
        TODO("Not yet implemented")
    }

    companion object {
        fun fromChannel(channel: IPlatformChannel): SerializedChannel {
            return SerializedChannel(
                channel.id,
                channel.name,
                channel.thumbnail,
                channel.banner,
                channel.subscribers,
                channel.description,
                channel.url,
                channel.links,
                channel.urlAlternatives
            );
        }
    }
}