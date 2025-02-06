package com.futo.platformplayer.states

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.exceptions.NoPlatformClientException
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.downloads.VideoDownload
import com.futo.platformplayer.engine.exceptions.ScriptUnavailableException
import com.futo.platformplayer.exceptions.ReconstructionException
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.ImportCache
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.smartMerge
import com.futo.platformplayer.states.StateSubscriptionGroups.Companion
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringArrayStorage
import com.futo.platformplayer.stores.StringDateMapStorage
import com.futo.platformplayer.stores.StringStorage
import com.futo.platformplayer.stores.v2.ManagedStore
import com.futo.platformplayer.stores.v2.ReconstructStore
import com.futo.platformplayer.sync.internal.GJSyncOpcodes
import com.futo.platformplayer.sync.models.SyncPlaylistsPackage
import com.futo.platformplayer.sync.models.SyncSubscriptionGroupsPackage
import com.futo.platformplayer.sync.models.SyncSubscriptionsPackage
import com.futo.platformplayer.sync.models.SyncWatchLaterPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/***
 * Used to maintain playlists
 */
class StatePlaylists {
    private val _watchlistStore = FragmentedStorage.storeJson<SerializedPlatformVideo>("watch_later")
        .withUnique { it.url }
        .withRestore(object: ReconstructStore<SerializedPlatformVideo>() {
            override fun toReconstruction(obj: SerializedPlatformVideo): String = obj.url;
            override suspend fun toObject(id: String, backup: String, reconstructionBuilder: Builder, importCache: ImportCache?): SerializedPlatformVideo
                = SerializedPlatformVideo.fromVideo(
                    importCache?.videos?.find { it.url == backup }?.let { Logger.i(TAG, "Reconstruction [${backup}] from cache"); return@let it; } ?:
                    StatePlatform.instance.getContentDetails(backup).await() as IPlatformVideoDetails);
        })
        .load();
    private val _watchlistOrderStore = FragmentedStorage.get<StringArrayStorage>("watchListOrder"); //Temporary workaround to add order..

    private val _watchLaterReorderTime = FragmentedStorage.get<StringStorage>("watchLaterReorderTime");
    private val _watchLaterAdds = FragmentedStorage.get<StringDateMapStorage>("watchLaterAdds");
    private val _watchLaterRemovals = FragmentedStorage.get<StringDateMapStorage>("watchLaterRemovals");


    val playlistStore = FragmentedStorage.storeJson<Playlist>("playlists")
        .withRestore(PlaylistBackup())
        .load();
    private val _playlistRemoved = FragmentedStorage.get<StringDateMapStorage>("playlist_removed");

    val playlistShareDir = FragmentedStorage.getOrCreateDirectory("shares");

    val onWatchLaterChanged = Event0();

    fun getWatchLaterAddTime(url: String): OffsetDateTime? {
        return _watchLaterAdds.get(url)
    }
    fun setWatchLaterAddTime(url: String, time: OffsetDateTime) {
        _watchLaterAdds.setAndSave(url, time);
    }
    fun getWatchLaterRemovalTime(url: String): OffsetDateTime? {
        return _watchLaterRemovals.get(url);
    }
    fun getWatchLaterLastReorderTime(): OffsetDateTime{
        val value = _watchLaterReorderTime.value;
        if(value.isEmpty())
            return OffsetDateTime.MIN;
        val tryParse = value.toLongOrNull() ?: 0;
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(tryParse), ZoneOffset.UTC);
    }
    private fun setWatchLaterReorderTime() {
        val now = OffsetDateTime.now(ZoneOffset.UTC);
        val nowEpoch = now.toEpochSecond();
        _watchLaterReorderTime.setAndSave(nowEpoch.toString());
    }

    fun getWatchLaterOrdering() = _watchlistOrderStore.getAllValues().toList();

    fun updateWatchLaterOrdering(order: List<String>, notify: Boolean = false) {
        _watchlistOrderStore.set(*smartMerge(order, getWatchLaterOrdering()).toTypedArray());
        _watchlistOrderStore.save();
        if(notify) {
            onWatchLaterChanged.emit();
        }
    }

    fun toMigrateCheck(): List<ManagedStore<*>> {
        return listOf(playlistStore, _watchlistStore);
    }
    fun getWatchLater() : List<SerializedPlatformVideo> {
        synchronized(_watchlistStore) {
            val order = _watchlistOrderStore.getAllValues();
            return _watchlistStore.getItems().sortedBy { order.indexOf(it.url) };
        }
    }
    fun updateWatchLater(updated: List<SerializedPlatformVideo>, isUserInteraction: Boolean = false) {
        var wasModified = false;
        synchronized(_watchlistStore) {
            //_watchlistStore.deleteAll();
            val existing = _watchlistStore.getItems();
            val toAdd = updated.filter { u -> !existing.any { u.url == it.url } };
            val toRemove = existing.filter { u -> !updated.any { u.url == it.url } };
            wasModified = toAdd.size > 0 || toRemove.size > 0;
            Logger.i(TAG, "WatchLater changed:\nTo Add:\n" +
                    (if(toAdd.size == 0) "None" else toAdd.map { " + " + it.name }.joinToString("\n")) +
                    "\nTo Remove:\n" +
                    (if(toRemove.size == 0) "None" else toRemove.map { " - " + it.name }.joinToString("\n")));
            for(remove in toRemove)
                _watchlistStore.delete(remove);
            _watchlistStore.saveAllAsync(toAdd);
            _watchlistOrderStore.set(*updated.map { it.url }.toTypedArray());
            _watchlistOrderStore.save();
        }
        onWatchLaterChanged.emit();

        if(isUserInteraction) {
            setWatchLaterReorderTime();
            broadcastWatchLater(!wasModified);
        }

        if(StateDownloads.instance.getWatchLaterDescriptor() != null) {
            StateDownloads.instance.checkForOutdatedPlaylistVideos(VideoDownload.GROUP_WATCHLATER);
        }
    }
    fun getWatchLaterFromUrl(url: String): SerializedPlatformVideo?{
        synchronized(_watchlistStore) {
            val order = _watchlistOrderStore.getAllValues();
            return _watchlistStore.getItems().firstOrNull { it.url == url };
        }
    }
    fun removeFromWatchLater(url: String, isUserInteraction: Boolean = false) {
        val item = getWatchLaterFromUrl(url);
        if(item != null){
            removeFromWatchLater(item, isUserInteraction);
        }
    }
    fun removeFromWatchLater(video: SerializedPlatformVideo, isUserInteraction: Boolean = false, time: OffsetDateTime? = null) {
        synchronized(_watchlistStore) {
            _watchlistStore.delete(video);
            _watchlistOrderStore.set(*_watchlistOrderStore.values.filter { it != video.url }.toTypedArray());
            _watchlistOrderStore.save();
            if(time != null)
                _watchLaterRemovals.setAndSave(video.url, time);
        }
        onWatchLaterChanged.emit();

        if(isUserInteraction) {
            val now = OffsetDateTime.now();
            if(time == null) {
                _watchLaterRemovals.setAndSave(video.url, now);
                broadcastWatchLaterRemoval(video.url, now);
            }
            else
                broadcastWatchLaterRemoval(video.url, time);
        }

        if(StateDownloads.instance.getWatchLaterDescriptor() != null) {
            StateDownloads.instance.checkForOutdatedPlaylistVideos(VideoDownload.GROUP_WATCHLATER);
        }
    }
    fun addToWatchLater(video: SerializedPlatformVideo, isUserInteraction: Boolean = false, orderPosition: Int = -1) {
        synchronized(_watchlistStore) {
            _watchlistStore.saveAsync(video);
            if(orderPosition == -1)
                _watchlistOrderStore.set(*(listOf(video.url) + _watchlistOrderStore.values) .toTypedArray());
            else {
                val existing = _watchlistOrderStore.getAllValues().toMutableList();
                existing.add(orderPosition, video.url);
                _watchlistOrderStore.set(*existing.toTypedArray());
            }
            _watchlistOrderStore.save();
        }
        onWatchLaterChanged.emit();

        if(isUserInteraction) {
            val now = OffsetDateTime.now();
            _watchLaterAdds.setAndSave(video.url, now);
            broadcastWatchLaterAddition(video, now);
        }

        StateDownloads.instance.checkForOutdatedPlaylists();
    }

    fun getLastPlayedPlaylist() : Playlist? {
        return playlistStore.queryItem { it.maxByOrNull { x -> x.datePlayed } };
    }
    fun getLastUpdatedPlaylist() : Playlist? {
        return playlistStore.queryItem { it.maxByOrNull { x -> x.dateUpdate } };
    }

    fun getPlaylists() : List<Playlist> {
        return playlistStore.getItems();
    }
    fun getPlaylist(id: String): Playlist? {
        return playlistStore.findItem { it.id == id };
    }

    fun getPlaylistRemovals(): Map<String, Long> {
        return _playlistRemoved.all();
    }

    fun didPlay(playlistId: String) {
        val playlist = getPlaylist(playlistId);
        if(playlist != null) {
            playlist.datePlayed = OffsetDateTime.now();
            playlistStore.saveAsync(playlist);
        }
    }

    private fun broadcastWatchLater(orderOnly: Boolean = false) {
        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            try {
                StateSync.instance.broadcastJsonData(
                    GJSyncOpcodes.syncWatchLater, SyncWatchLaterPackage(
                        if (orderOnly) listOf() else getWatchLater(),
                        if (orderOnly) mapOf() else _watchLaterAdds.all(),
                        if (orderOnly) mapOf() else _watchLaterRemovals.all(),
                        getWatchLaterLastReorderTime().toEpochSecond(),
                        _watchlistOrderStore.values.toList()
                    )
                );
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to broadcast watch later", e)
            }
        };
    }
    private fun broadcastWatchLaterAddition(video: SerializedPlatformVideo, time: OffsetDateTime) {
        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            try {
                StateSync.instance.broadcastJsonData(
                    GJSyncOpcodes.syncWatchLater, SyncWatchLaterPackage(
                        listOf(video),
                        mapOf(Pair(video.url, time.toEpochSecond())),
                        mapOf(),

                        )
                )
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to broadcast watch later addition", e)
            }
        };
    }
    private fun broadcastWatchLaterRemoval(url: String, time: OffsetDateTime) {
        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            try {
                StateSync.instance.broadcastJsonData(
                    GJSyncOpcodes.syncWatchLater, SyncWatchLaterPackage(
                        listOf(),
                        mapOf(),
                        mapOf(Pair(url, time.toEpochSecond()))
                    )
                )
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to broadcast watch later removal", e)
            }
        };
    }

    suspend fun createPlaylistFromChannel(channelUrl: String, onPage: (Int) -> Unit): Playlist {
        val channel = StatePlatform.instance.getChannel(channelUrl).await();
        return createPlaylistFromChannel(channel, onPage);
    }
    fun createPlaylistFromChannel(channel: IPlatformChannel, onPage: (Int) -> Unit): Playlist {
        val contents = StatePlatform.instance.getChannelContent(channel.url);
        val allContents = mutableListOf<IPlatformContent>();
        allContents.addAll(contents.getResults());
        var page = 1;
        while(contents.hasMorePages()) {
            Logger.i("StatePlaylists", "Fetching channel video page ${page} from ${channel.url}");
            onPage(page);
            contents.nextPage();
            allContents.addAll(contents.getResults());
            page++;
        }
        val allVideos = allContents.filter { it is IPlatformVideo }.map { it as IPlatformVideo };
        val newPlaylist = Playlist(channel.name, allVideos.map { SerializedPlatformVideo.fromVideo(it) });
        createOrUpdatePlaylist(newPlaylist);
        return newPlaylist;
    }
    fun createOrUpdatePlaylist(playlist: Playlist, isUserInteraction: Boolean = true) {
        playlist.dateUpdate = OffsetDateTime.now();
        playlistStore.saveAsync(playlist, true);
        if(playlist.id.isNotEmpty()) {
            if (StateDownloads.instance.isPlaylistCached(playlist.id)) {
                StateDownloads.instance.checkForOutdatedPlaylistVideos(playlist.id);
            }
            if(isUserInteraction)
                broadcastSyncPlaylist(playlist);
        }
    }
    fun addToPlaylist(id: String, video: IPlatformVideo): Boolean {
        synchronized(playlistStore) {
            val playlist = getPlaylist(id) ?: return false;
            if(!Settings.instance.other.playlistAllowDups && playlist.videos.any { it.url == video.url })
                return false;


            playlist.videos.add(SerializedPlatformVideo.fromVideo(video));
            playlist.dateUpdate = OffsetDateTime.now();
            playlistStore.saveAsync(playlist, true);

            broadcastSyncPlaylist(playlist);
            return true;
        }
    }

    private fun broadcastSyncPlaylist(playlist: Playlist){
        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            try {
                Logger.i(StateSubscriptionGroups.TAG, "SyncPlaylist (${playlist.name})");
                StateSync.instance.broadcastJsonData(
                    GJSyncOpcodes.syncPlaylists,
                    SyncPlaylistsPackage(listOf(playlist), mapOf())
                );
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to broadcast sync playlist", e)
            }
        };
    }

    fun removePlaylist(playlist: Playlist, isUserInteraction: Boolean = true) {
        playlistStore.delete(playlist);
        if(StateDownloads.instance.isPlaylistCached(playlist.id)) {
            StateDownloads.instance.deleteCachedPlaylist(playlist.id);
        }
        if(isUserInteraction) {
            _playlistRemoved.setAndSave(playlist.id, OffsetDateTime.now());

            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                try {
                    Logger.i(StateSubscriptionGroups.TAG, "SyncPlaylist (${playlist.name})");
                    StateSync.instance.broadcastJsonData(
                        GJSyncOpcodes.syncPlaylists,
                        SyncPlaylistsPackage(listOf(), mapOf(Pair(playlist.id, OffsetDateTime.now().toEpochSecond())))
                    );
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to broadcast sync playlists", e)
                }
            };
        }
    }

    fun createPlaylistShareUri(context: Context, playlist: Playlist): Uri {
        val reconstruction = playlistStore.getReconstructionString(playlist, true);

        val newFile = File(playlistShareDir, playlist.name + ".recp");
        newFile.writeText(reconstruction, Charsets.UTF_8);

        return FileProvider.getUriForFile(context, context.resources.getString(R.string.authority), newFile);
    }
    fun createPlaylistShareJsonUri(context: Context, playlist: Playlist): Uri {
        val reconstruction = playlistStore.getReconstructionString(playlist, true);

        val newFile = File(playlistShareDir, playlist.name + ".json");
        newFile.writeText(Json.encodeToString(reconstruction.split("\n") + listOf(
            "__CACHE:" + Json.encodeToString(ImportCache(
                videos = playlist.videos.toList()
            ))
        )), Charsets.UTF_8);

        return FileProvider.getUriForFile(context, context.resources.getString(R.string.authority), newFile);
    }


    fun getSyncPlaylistsPackageString(): String{
        return Json.encodeToString(
            SyncPlaylistsPackage(
                getPlaylists(),
                getPlaylistRemovals()
            )
        );
    }

    companion object {
        val TAG = "StatePlaylists";
        private var _instance : StatePlaylists? = null;
        val instance : StatePlaylists
            get(){
            if(_instance == null)
                _instance = StatePlaylists();
            return _instance!!;
        };

        fun finish() {
            _instance?.let {
                _instance = null;
            }
        }
    }



    class PlaylistBackup: ReconstructStore<Playlist>() {
        override fun toReconstruction(obj: Playlist): String {
            val items = ArrayList<String>();
            items.add(obj.name);
            items.addAll(obj.videos.map { it.url });
            return items.map { it.replace("\n","") }.joinToString("\n");
        }
        override suspend fun toObject(id: String, backup: String, reconstructionBuilder: Builder, importCache: ImportCache?): Playlist {
            val items = backup.split("\n");
            if(items.size <= 0) {
                throw IllegalStateException("Cannot reconstructor playlist ${id}");
            }

            val name = items[0];
            val videos = items.drop(1).filter { it.isNotEmpty() }.map {
                try {
                    val videoUrl = it;
                    val video = importCache?.videos?.find { it.url == videoUrl } ?:
                        StatePlatform.instance.getContentDetails(it).await();
                    if (video is IPlatformVideoDetails) {
                        return@map SerializedPlatformVideo.fromVideo(video);
                    }
                    else if(video is SerializedPlatformVideo) {
                        Logger.i(TAG, "Reconstruction [${it}] from cache");
                        return@map video;
                    }
                    else {
                        return@map null
                    }
                }
                catch(ex: ScriptUnavailableException) {
                    Logger.w(TAG, "${name}:[${it}] is no longer available");
                    reconstructionBuilder.messages.add("${name}:[${it}] is no longer available");
                    return@map null;
                }
                catch(ex: NoPlatformClientException) {
                    throw ReconstructionException(name, "No source enabled for [${it}]", ex);
                    //TODO: Propagate this to dialog, and then back, allowing users to enable plugins...
                    //builder.messages.add("No source enabled for [${it}]");
                    //return@map null;
                }
                catch(ex: Throwable) {
                    throw ReconstructionException(name, "${name}:[${it}] ${ex.message}", ex);
                }
            }.filter { it != null }.map { it!! }
            return Playlist(id, name, videos);
        }
    }
}