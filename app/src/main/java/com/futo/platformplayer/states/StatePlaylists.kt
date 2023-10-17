package com.futo.platformplayer.states

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.exceptions.NoPlatformClientException
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.engine.exceptions.ScriptUnavailableException
import com.futo.platformplayer.exceptions.ReconstructionException
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.HistoryVideo
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.v2.ManagedStore
import com.futo.platformplayer.stores.v2.ReconstructStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/***
 * Used to maintain playlists
 */
class StatePlaylists {
    private val _watchlistStore = FragmentedStorage.storeJson<SerializedPlatformVideo>("watch_later")
        .withUnique { it.url }
        .withRestore(object: ReconstructStore<SerializedPlatformVideo>() {
            override fun toReconstruction(obj: SerializedPlatformVideo): String = obj.url;
            override suspend fun toObject(id: String, backup: String, builder: Builder): SerializedPlatformVideo
                = SerializedPlatformVideo.fromVideo(StatePlatform.instance.getContentDetails(backup).await() as IPlatformVideoDetails);
        })
        .load();
    private val _historyStore = FragmentedStorage.storeJson<HistoryVideo>("history")
        .withRestore(object: ReconstructStore<HistoryVideo>() {
            override fun toReconstruction(obj: HistoryVideo): String = obj.toReconString();
            override suspend fun toObject(id: String, backup: String, reconstructionBuilder: Builder): HistoryVideo
                = HistoryVideo.fromReconString(backup, null);
        })
        .load();
    val playlistStore = FragmentedStorage.storeJson<Playlist>("playlists")
        .withRestore(PlaylistBackup())
        .load();

    val playlistShareDir = FragmentedStorage.getOrCreateDirectory("shares");

    var onHistoricVideoChanged = Event2<IPlatformVideo, Long>();
    val onWatchLaterChanged = Event0();

    fun toMigrateCheck(): List<ManagedStore<*>> {
        return listOf(playlistStore, _watchlistStore, _historyStore);
    }

    fun getWatchLater() : List<SerializedPlatformVideo> {
        synchronized(_watchlistStore) {
            return _watchlistStore.getItems();
        }
    }
    fun updateWatchLater(updated: List<SerializedPlatformVideo>) {
        synchronized(_watchlistStore) {
            _watchlistStore.deleteAll();
            _watchlistStore.saveAllAsync(updated);
        }
        onWatchLaterChanged.emit();
    }
    fun removeFromWatchLater(video: SerializedPlatformVideo) {
        synchronized(_watchlistStore) {
            _watchlistStore.delete(video);
        }

        onWatchLaterChanged.emit();
    }
    fun addToWatchLater(video: SerializedPlatformVideo) {
        synchronized(_watchlistStore) {
            _watchlistStore.saveAsync(video);
        }
        onWatchLaterChanged.emit();
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

    fun getHistoryPosition(url: String): Long {
        val histVideo = _historyStore.findItem { it.video.url == url };
        if(histVideo != null)
            return histVideo.position;
        return 0;
    }
    fun updateHistoryPosition(video: IPlatformVideo, updateExisting: Boolean, position: Long = -1L): Long {
        val pos = if(position < 0) 0 else position;
        val historyVideo = _historyStore.findItem { it.video.url == video.url };
        if (historyVideo != null) {
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
                        historyVideo.video = SerializedPlatformVideo.fromVideo(video);

                    historyVideo.position = pos;
                    historyVideo.date = OffsetDateTime.now();
                    _historyStore.saveAsync(historyVideo);
                    onHistoricVideoChanged.emit(video, pos);
                }
            }

            return positionBefore;
        } else {
            val newHistItem = HistoryVideo(SerializedPlatformVideo.fromVideo(video), pos, OffsetDateTime.now());
            _historyStore.saveAsync(newHistItem);
            return 0;
        }
    }

    fun getHistory() : List<HistoryVideo> {
        return _historyStore.getItems().sortedByDescending { it.date };
    }

    fun removeHistory(url: String) {
        val hist = _historyStore.findItem { it.video.url == url };
        if(hist != null)
            _historyStore.delete(hist);
    }

    fun removeHistoryRange(minutesToDelete: Long) {
        val now = OffsetDateTime.now();
        val toDelete = _historyStore.findItems { minutesToDelete == -1L || ChronoUnit.MINUTES.between(it.date, now) < minutesToDelete };

        for(item in toDelete)
            _historyStore.delete(item);
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
        newFile.writeText(Json.encodeToString(reconstruction.split("\n")), Charsets.UTF_8);

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
        override suspend fun toObject(id: String, backup: String, builder: Builder): Playlist {
            val items = backup.split("\n");
            if(items.size <= 0)
                throw IllegalStateException("Cannot reconstructor playlist ${id}");

            val name = items[0];
            val videos = items.drop(1).filter { it.isNotEmpty() }.map {
                try {
                    val video = StatePlatform.instance.getContentDetails(it).await();
                    if (video is IPlatformVideoDetails)
                        return@map SerializedPlatformVideo.fromVideo(video);
                    else return@map null;
                }
                catch(ex: ScriptUnavailableException) {
                    Logger.w(TAG, "${name}:[${it}] is no longer available");
                    builder.messages.add("${name}:[${it}] is no longer available");
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