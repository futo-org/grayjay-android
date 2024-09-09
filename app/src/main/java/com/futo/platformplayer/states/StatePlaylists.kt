package com.futo.platformplayer.states

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.futo.platformplayer.R
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
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringArrayStorage
import com.futo.platformplayer.stores.v2.ManagedStore
import com.futo.platformplayer.stores.v2.ReconstructStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.OffsetDateTime

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

    val playlistStore = FragmentedStorage.storeJson<Playlist>("playlists")
        .withRestore(PlaylistBackup())
        .load();

    val playlistShareDir = FragmentedStorage.getOrCreateDirectory("shares");

    val onWatchLaterChanged = Event0();

    fun toMigrateCheck(): List<ManagedStore<*>> {
        return listOf(playlistStore, _watchlistStore);
    }
    fun getWatchLater() : List<SerializedPlatformVideo> {
        synchronized(_watchlistStore) {
            val order = _watchlistOrderStore.getAllValues();
            return _watchlistStore.getItems().sortedBy { order.indexOf(it.url) };
        }
    }
    fun updateWatchLater(updated: List<SerializedPlatformVideo>) {
        synchronized(_watchlistStore) {
            //_watchlistStore.deleteAll();
            val existing = _watchlistStore.getItems();
            val toAdd = updated.filter { u -> !existing.any { u.url == it.url } };
            val toRemove = existing.filter { u -> !updated.any { u.url == it.url } };
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

        if(StateDownloads.instance.getWatchLaterDescriptor() != null) {
            StateDownloads.instance.checkForOutdatedPlaylistVideos(VideoDownload.GROUP_WATCHLATER);
        }
    }
    fun removeFromWatchLater(video: SerializedPlatformVideo) {
        synchronized(_watchlistStore) {
            _watchlistStore.delete(video);
            _watchlistOrderStore.set(*_watchlistOrderStore.values.filter { it != video.url }.toTypedArray());
            _watchlistOrderStore.save();
        }
        onWatchLaterChanged.emit();

        if(StateDownloads.instance.getWatchLaterDescriptor() != null) {
            StateDownloads.instance.checkForOutdatedPlaylistVideos(VideoDownload.GROUP_WATCHLATER);
        }
    }
    fun addToWatchLater(video: SerializedPlatformVideo) {
        synchronized(_watchlistStore) {
            _watchlistStore.saveAsync(video);
            _watchlistOrderStore.set(*(listOf(video.url) + _watchlistOrderStore.values) .toTypedArray());
            _watchlistOrderStore.save();
        }
        onWatchLaterChanged.emit();

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


    fun didPlay(playlistId: String) {
        val playlist = getPlaylist(playlistId);
        if(playlist != null) {
            playlist.datePlayed = OffsetDateTime.now();
            playlistStore.saveAsync(playlist);
        }
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
    fun createOrUpdatePlaylist(playlist: Playlist) {
        playlist.dateUpdate = OffsetDateTime.now();
        playlistStore.saveAsync(playlist, true);
        if(playlist.id.isNotEmpty()) {
            if (StateDownloads.instance.isPlaylistCached(playlist.id)) {
                StateDownloads.instance.checkForOutdatedPlaylistVideos(playlist.id);
            }
        }
    }
    fun addToPlaylist(id: String, video: IPlatformVideo) {
        synchronized(playlistStore) {
            val playlist = getPlaylist(id) ?: return;
            playlist.videos.add(SerializedPlatformVideo.fromVideo(video));
            playlist.dateUpdate = OffsetDateTime.now();
            playlistStore.saveAsync(playlist, true);
        }
    }

    fun removePlaylist(playlist: Playlist) {
        playlistStore.delete(playlist);
        if(StateDownloads.instance.isPlaylistCached(playlist.id)) {
            StateDownloads.instance.deleteCachedPlaylist(playlist.id);
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