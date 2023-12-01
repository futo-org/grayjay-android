package com.futo.platformplayer.states

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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.system.measureTimeMillis

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
        if(index.obj == null) throw IllegalStateException("Can only update history with a deserialized db item");
        val historyVideo = index.obj!!;

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

                //A unrecovered item
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

    fun getHistoryLegacy(): List<HistoryVideo> {
        return _historyStore.getItems();
    }
    fun getHistory() : List<HistoryVideo> {
        return _historyDBStore.getAllObjects();
        //return _historyStore.getItems().sortedByDescending { it.date };
    }
    fun getHistoryPager(): IPager<HistoryVideo> {
        return _historyDBStore.getObjectPager();
    }
    fun getHistorySearchPager(query: String): IPager<HistoryVideo> {
        return _historyDBStore.queryLikeObjectPager(DBHistory.Index::url, "%${query}%", 10);
    }
    fun getHistoryIndexByUrl(url: String): DBHistory.Index? {
        return historyIndex[url];
    }
    fun getHistoryByVideo(video: IPlatformVideo, create: Boolean = false, watchDate: OffsetDateTime? = null): DBHistory.Index? {
        val existing = historyIndex[video.url];
        if(existing != null)
            return _historyDBStore.get(existing.id!!);
        else if(create) {
            val newHistItem = HistoryVideo(SerializedPlatformVideo.fromVideo(video), 0, watchDate ?: OffsetDateTime.now());
            val id = _historyDBStore.insert(newHistItem);
            return _historyDBStore.get(id);
        }
        return null;
    }

    fun removeHistory(url: String) {
        val hist = getHistoryIndexByUrl(url);
        if(hist != null)
            _historyDBStore.delete(hist.id!!);
        /*
        val hist = _historyStore.findItem { it.video.url == url };
        if(hist != null)
            _historyStore.delete(hist);*/
    }

    fun removeHistoryRange(minutesToDelete: Long) {
        val now = OffsetDateTime.now().toEpochSecond();
        val toDelete = _historyDBStore.getAllIndexes().filter { minutesToDelete == -1L || (now - it.datetime) < minutesToDelete * 60 };
        for(item in toDelete)
            _historyDBStore.delete(item);
        /*
        val now = OffsetDateTime.now();
        val toDelete = _historyStore.findItems { minutesToDelete == -1L || ChronoUnit.MINUTES.between(it.date, now) < minutesToDelete };

        for(item in toDelete)
            _historyStore.delete(item);*/
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


    fun testHistoryDB(count: Int) {
        Logger.i(TAG, "TEST: Starting tests");
        _historyDBStore.deleteAll();

        val testHistoryItem = getHistoryLegacy().first();
        val testItemJson = testHistoryItem.video.toJson();
        val now = OffsetDateTime.now();

        val testSet = (0..count).map { HistoryVideo(Json.decodeFromString<SerializedPlatformVideo>(testItemJson.replace(testHistoryItem.video.url, UUID.randomUUID().toString())), it.toLong(), now.minusHours(it.toLong())) }


        Logger.i(TAG, "TEST: Inserting (${testSet.size})");
        val insertMS = measureTimeMillis {
            for(item in testSet)
                _historyDBStore.insert(item);
        };
        Logger.i(TAG, "TEST: Inserting in ${insertMS}ms");

        var fetched: List<DBHistory.Index>? = null;
        val fetchMS = measureTimeMillis {
            fetched = _historyDBStore.getAll();
            Logger.i(TAG, "TEST: Fetched: ${fetched?.size}");
        };
        Logger.i(TAG, "TEST: Fetch speed ${fetchMS}MS");
        val deserializeMS = measureTimeMillis {
            val deserialized = _historyDBStore.convertObjects(fetched!!);
            Logger.i(TAG, "TEST: Deserialized: ${deserialized.size}");
        };
        Logger.i(TAG, "TEST: Deserialize speed ${deserializeMS}MS");

        var fetchedIndex: List<DBHistory.Index>? = null;
        val fetchIndexMS = measureTimeMillis {
            fetchedIndex = _historyDBStore.getAllIndexes();
            Logger.i(TAG, "TEST: Fetched Index: ${fetchedIndex!!.size}");
        };
        Logger.i(TAG, "TEST: Fetched Index speed ${fetchIndexMS}ms");
        val fetchFromIndex = measureTimeMillis {
            for(preItem in testSet) {
                val item = historyIndex[preItem.video.url];
                if(item == null)
                    throw IllegalStateException("Missing item [${preItem.video.url}]");
                if(item.url != preItem.video.url)
                    throw IllegalStateException("Mismatch item [${preItem.video.url}]");
            }
        };
        Logger.i(TAG, "TEST: Index Lookup speed ${fetchFromIndex}ms");

        val page1 = _historyDBStore.getPage(0, 20);
        val page2 = _historyDBStore.getPage(1, 20);
        val page3 = _historyDBStore.getPage(2, 20);
    }


}