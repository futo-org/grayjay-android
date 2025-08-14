package com.futo.platformplayer.states

import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.HistoryVideo
import com.futo.platformplayer.models.ImportCache
import com.futo.platformplayer.states.StateApp.Companion
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringDateMapStorage
import com.futo.platformplayer.stores.db.ManagedDBStore
import com.futo.platformplayer.stores.db.types.DBHistory
import com.futo.platformplayer.stores.v2.ReconstructStore
import com.futo.platformplayer.sync.internal.GJSyncOpcodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class StateHistory {
    //Legacy
    private val _historyStore = FragmentedStorage.storeJson<HistoryVideo>("history")
        .withRestore(object: ReconstructStore<HistoryVideo>() {
            override fun toReconstruction(obj: HistoryVideo): String = obj.toReconString();
            override suspend fun toObject(id: String, backup: String, reconstructionBuilder: Builder, cache: ImportCache?): HistoryVideo
                    = HistoryVideo.fromReconString(backup) { url -> cache?.videos?.find { it.url == url } };
        })
        .load();

    private val _remoteHistoryDatesStore = FragmentedStorage.get<StringDateMapStorage>("remoteHistoryDates");

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
    fun isHistoryWatched(url: String, duration: Long): Boolean {
        return getHistoryPosition(url) > duration * 0.7;
    }

    private var _lastHistoryBroadcast = "";
    fun updateHistoryPosition(liveObj: IPlatformVideo, index: DBHistory.Index, updateExisting: Boolean, position: Long = -1L, date: OffsetDateTime? = null, isUserAction: Boolean = false): Long {
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
                historyVideo.date = date ?: OffsetDateTime.now();
                _historyDBStore.update(index.id!!, historyVideo);
                onHistoricVideoChanged.emit(liveObj, pos);


                val historyBroadcastSig = "${historyVideo.position}${historyVideo.video.id.value ?: historyVideo.video.url}"
                if(isUserAction && _lastHistoryBroadcast != historyBroadcastSig) {
                    _lastHistoryBroadcast = historyBroadcastSig;
                    StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                        try {
                            Logger.i(TAG, "SyncHistory playback broadcasted (${liveObj.name}: ${position})");
                            StateSync.instance.broadcastJsonData(
                                GJSyncOpcodes.syncHistory,
                                listOf(historyVideo)
                            );
                        } catch (e: Throwable) {
                            Logger.e(StatePlaylists.TAG, "Failed to broadcast sync history", e)
                        }
                    };
                }
            }
            return positionBefore;
        }
        return positionBefore;
    }

    fun getRecentHistory(minDate: OffsetDateTime, max: Int = 1000): List<HistoryVideo> {
        val pager = getHistoryPager();
        val videos = pager.getResults().filter { it.date > minDate }.toMutableList();
        while(pager.hasMorePages() && videos.size < max) {
            pager.nextPage();
            val newResults = pager.getResults();
            var foundEnd = false;
            for(item in newResults) {
                if(item.date < minDate) {
                    foundEnd = true;
                    break;
                }
                else
                    videos.add(item);
            }
            if(foundEnd)
                break;
        }
        return videos;
    }

    fun getHistoryPager(): IPager<HistoryVideo> {
        return _historyDBStore.getObjectPager();
    }
    fun getHistorySearchPager(query: String, withAuthor: Boolean = false): IPager<HistoryVideo> {
        return if(!withAuthor)
            _historyDBStore.queryLikeObjectPager(DBHistory.Index::name, "%${query}%", 10)
        else
            _historyDBStore.queryLikeObjectPager(DBHistory.Index::name, "%${query}%", 10)
            //_historyDBStore.queryLike2ObjectPager(DBHistory.Index::name, DBHistory.Index::auth,"%${query}%", 10)
        //TODO: See if we can include author name?
    }
    fun getHistoryIndexByUrl(url: String): DBHistory.Index? {
        return historyIndex[url];
    }
    fun getHistoryByVideo(video: IPlatformVideo, create: Boolean = false, watchDate: OffsetDateTime? = null): DBHistory.Index? {
        if(StateApp.instance.privateMode)
            return null;
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
        return result;
    }

    fun markAsWatched(video: IPlatformVideo) {
        try {
            val history = getHistoryByVideo(video, true, OffsetDateTime.now());
            if (history != null) {
                updateHistoryPosition(video, history, true, Math.max(1, video.duration - 1));
            }
        }
        catch(ex: Throwable) {
            Logger.e(TAG, "Failed to mark as watched", ex);
            UIDialogs.toast("Failed to mark as watched\n" + ex.message);
        }
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
        _remoteHistoryDatesStore.map = HashMap<String, Long>();
        _remoteHistoryDatesStore.save();
    }

    fun syncRemoteHistory(plugin: JSClient): Int {
        if (plugin.capabilities.hasGetUserHistory &&
            plugin.isLoggedIn) {
            Logger.i(TAG, "Syncing remote history for plugin [${plugin.name}]");

            val hist = StatePlatform.instance.getUserHistory(plugin.id);

            return syncRemoteHistory(plugin.id, hist, 100, 3);
        }
        return 0;
    }
    fun syncRemoteHistory(pluginId: String, videos: IPager<IPlatformContent>, maxVideos: Int, maxPages: Int): Int {
        val lastDate = _remoteHistoryDatesStore.get(pluginId) ?: OffsetDateTime.MIN;
        val maxVideosCount = if(maxVideos <= 0) 500 else maxVideos;
        val maxPageCount = if(maxPages <= 0) 3 else maxPages;
        var exceededDate = false;
        try {
            val toSync = mutableListOf<IPlatformVideo>();
            var pageCount = 0;
            var videoCount = 0;
            var isFirst = true;
            var oldestPlayback = OffsetDateTime.MAX;
            var newestPlayback = OffsetDateTime.MIN;
            do {
                if (!isFirst) videos.nextPage();
                val newVideos = videos.getResults();

                var foundVideos = false;
                var toSyncAddedCount = 0;
                for(video in newVideos) {
                    if(video is IPlatformVideo && video.playbackDate != null) {

                        if(video.playbackDate!! < lastDate) {
                            exceededDate = true;
                            break;
                        }

                        if(video.playbackTime > 0) {
                            toSync.add(video);
                            toSyncAddedCount++;
                            foundVideos = true;
                            oldestPlayback = video.playbackDate!!;
                            if(newestPlayback == OffsetDateTime.MIN)
                                newestPlayback = video.playbackDate!!;
                        }
                    }
                }

                pageCount++;
                videoCount += newVideos.size;
                isFirst = false;

                if(!foundVideos)
                {
                    Logger.i(TAG, "Found no more videos in remote history");
                    break;
                }
            }
            while(videos.hasMorePages() && videoCount <= maxVideosCount && pageCount <= maxPageCount && !exceededDate);

            var updated = 0;
            if(oldestPlayback < OffsetDateTime.MAX) {
                for(video in toSync){
                    val hist = getHistoryByVideo(video, true, video.playbackDate);
                    if(hist != null && hist.position < video.playbackTime) {
                        Logger.i(TAG, "Updated history for video [${video.name}] from remote history");
                        updateHistoryPosition(video, hist, true, video.playbackTime, video.playbackDate, false);
                        updated++;
                    }
                }
                if(updated > 0) {
                    _remoteHistoryDatesStore.setAndSave(pluginId, newestPlayback);

                    try {
                        val client = StatePlatform.instance.getClient(pluginId);
                        UIDialogs.appToast("Updated ${updated} history from ${client.name}")
                    }
                    catch(ex: Throwable){}
                }
                return updated;
            }
        }
        catch(ex: Throwable) {
            val plugin = if(pluginId != StateDeveloper.DEV_ID) StatePlugins.instance.getPlugin(pluginId) else null;
            Logger.e(TAG, "Sync Remote History failed for [${plugin?.config?.name}] due to: " + ex.message)
        }
        return 0;
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