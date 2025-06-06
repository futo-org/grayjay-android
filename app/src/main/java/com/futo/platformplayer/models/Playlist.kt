package com.futo.platformplayer.models

import com.caoccao.javet.values.reference.V8ValueArray
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.platforms.js.models.JSVideo
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.serializers.OffsetDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.util.*

@Serializable
class Playlist {
    var id: String = UUID.randomUUID().toString();
    var name: String = "";
    var videos: ArrayList<SerializedPlatformVideo> = arrayListOf();

    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var dateCreation: OffsetDateTime = OffsetDateTime.now();
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var dateUpdate: OffsetDateTime = OffsetDateTime.now();
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var datePlayed: OffsetDateTime = OffsetDateTime.MIN;

    constructor(){}
    constructor(name: String, list: List<SerializedPlatformVideo>) {
        this.name = name;
        this.videos = ArrayList(list);
    }
    constructor(id: String, name: String, list: List<SerializedPlatformVideo>) {
        this.id = id;
        this.name = name;
        this.videos = ArrayList(list);
    }

    fun makeCopy(): Playlist {
        return Playlist("$name (Copy)", videos)
    }

    companion object {
        fun fromV8(config: SourcePluginConfig, obj: V8ValueObject?): Playlist? {
            if(obj == null)
                return null;

            val contextName = "Playlist";

            val id = obj.getOrThrow<String>(config, "id", contextName);
            val name = obj.getOrThrow<String>(config, "name", contextName);
            val videoObjs = obj.getOrThrow<V8ValueArray>(config, "videos", contextName);

            val videos = mutableListOf<JSVideo>();

            for(videoKey in videoObjs.keys) {
                val videoObj = videoObjs.get<V8ValueObject>(videoKey);
                val jVideo = JSVideo(config, videoObj);
                videos.add(jVideo);
            }

            return Playlist(id, name, videos.map { SerializedPlatformVideo.fromVideo(it) });
        }
    }
}