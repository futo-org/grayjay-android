package com.futo.platformplayer.states

import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.HistoryVideo
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.db.ManagedDBStore
import com.futo.platformplayer.stores.db.types.DBHistory
import com.futo.platformplayer.stores.v2.ReconstructStore
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class StateHistory {
    //Legacy
    private val _historyStore = FragmentedStorage.storeJson<HistoryVideo>("history")
        .withRestore(object: ReconstructStore<HistoryVideo>() {
            override fun toReconstruction(obj: HistoryVideo): String = obj.toReconString();
            override suspend fun toObject(id: String, backup: String, reconstructionBuilder: Builder): HistoryVideo
                    = HistoryVideo.fromReconString(backup, null);
        })
        .load();

    private val historyIndex: ConcurrentMap<Any, DBHistory.Index> = ConcurrentHashMap();
    val _historyDBStore = ManagedDBStore.create("history", DBHistory.Descriptor())
        .withIndex({ it.url }, historyIndex, false, true)
        .load();

    var onHistoricVideoChanged = Event2<IPlatformVideo, Long>();

    fun shouldMigrateLegacyHistory(): Boolean {
        return _historyDBStore.count() == 0 && _historyStore.count() > 0;
    }
    fun migrateLegacyHistory() {
        Logger.i(StatePlaylists.TAG, "Migrating legacy history");
        _historyDBStore.deleteAll();
        val allHistory = _historyStore.getItems();
        Logger.i(StatePlaylists.TAG, "Migrating legacy history (${allHistory.size}) items");
        for(item in allHistory) {
            _historyDBStore.insert(item);
        }
        _historyStore.deleteAll();
    }


    fun getHistoryPosition(url: String): Long {
        return historyIndex[url]?.position ?: 0;
    }


    fun updateHistoryPosition(liveObj: IPlatformVideo, index: DBHistory.Index, updateExisting: Boolean, position: Long = -1L): Long {
        val pos = if(position < 0) 0 else position;
        val historyVideo = index.obj;

        val positionBefore = historyVideo.position;
        if (updateExisting) {
            var shouldUpdate = false;
            if (positionBefore < 30) {
                shouldUpdate = true;
            } else {
                if (position > 30) {
                    shouldUpdate = true;
                }
            }

            if (shouldUpdate) {
                if(historyVideo.video.author.id.value == null && historyVideo.video.duration == 0L)
                    historyVideo.video = SerializedPlatformVideo.fromVideo(liveObj);

                historyVideo.position = pos;
                historyVideo.date = OffsetDateTime.now();
                _historyDBStore.update(index.id!!, historyVideo);
                onHistoricVideoChanged.emit(liveObj, pos);
            }

            return positionBefore;
        }

        return positionBefore;
    }
    fun getHistoryPager(): IPager<HistoryVideo> {
        return _historyDBStore.getObjectPager();
    }
    fun getHistorySearchPager(query: String): IPager<HistoryVideo> {
        return _historyDBStore.queryLikeObjectPager(DBHistory.Index::name, "%${query}%", 10);
    }
    fun getHistoryIndexByUrl(url: String): DBHistory.Index? {
        return historyIndex[url];
    }
    fun getHistoryByVideo(video: IPlatformVideo, create: Boolean = false, watchDate: OffsetDateTime? = null): DBHistory.Index? {
        val existing = historyIndex[video.url];
        var result: DBHistory.Index? = null;
        if(existing != null) {
            result = _historyDBStore.getOrNull(existing.id!!);
            if(result == null)
                UIDialogs.toast("History item null?\nNo history tracking..");
        }
        else if(create) {
            val newHistItem = HistoryVideo(SerializedPlatformVideo.fromVideo(video), 0, watchDate ?: OffsetDateTime.now());
            val id = _historyDBStore.insert(newHistItem);
            result = _historyDBStore.getOrNull(id);
            if(result == null)
                UIDialogs.toast("History creation failed?\nNo history tracking..");
        }
        return null;
    }

    fun removeHistory(url: String) {
        val hist = getHistoryIndexByUrl(url);
        if(hist != null)
            _historyDBStore.delete(hist.id!!);
    }

    fun removeHistoryRange(minutesToDelete: Long) {
        val now = OffsetDateTime.now().toEpochSecond();
        val toDelete = _historyDBStore.getAllIndexes().filter { minutesToDelete == -1L || (now - it.datetime) < minutesToDelete * 60 };
        for(item in toDelete)
            _historyDBStore.delete(item);
    }


    companion object {
        val TAG = "StateHistory";
        private var _instance : StateHistory? = null;
        val instance : StateHistory
            get(){
                if(_instance == null)
                    _instance = StateHistory();
                return _instance!!;
            };

        fun finish() {
            _instance?.let {
                _instance = null;
            }
        }
    }
}