package com.futo.platformplayer.stores.db.types

import androidx.room.ColumnInfo
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.futo.platformplayer.api.media.models.video.SerializedPlatformContent
import com.futo.platformplayer.models.HistoryVideo
import com.futo.platformplayer.stores.db.ManagedDBIndex

class DBChannelCache {
    companion object {
        const val TABLE_NAME = "channelCache";
    }

    class Index: ManagedDBIndex<SerializedPlatformContent> {
        @PrimaryKey(true)
        override var id: Long? = null;

        var feedType: String? = null;
        var channelUrl: String? = null;


        constructor() {}
        constructor(sCache: SerializedPlatformContent) {
            id = null;
            serialized = null;
            channelUrl = sCache.author.url;
        }
    }
}