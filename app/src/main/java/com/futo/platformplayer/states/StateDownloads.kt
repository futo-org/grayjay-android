package com.futo.platformplayer.states

import android.content.ContentResolver
import android.os.StatFs
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.exceptions.AlreadyQueuedException
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalSubtitleSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.SubtitleRawSource
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.downloads.PlaylistDownloadDescriptor
import com.futo.platformplayer.downloads.VideoDownload
import com.futo.platformplayer.downloads.VideoExport
import com.futo.platformplayer.downloads.VideoLocal
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.DiskUsage
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.models.PlaylistDownloaded
import com.futo.platformplayer.services.DownloadService
import com.futo.platformplayer.services.ExportingService
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.v2.ManagedStore
import java.io.File

/***
 * Used to maintain downloads
 */
class StateDownloads {
    private val _downloadsDirectory: File = FragmentedStorage.getOrCreateDirectory("downloads");
    private val _downloadsStat = StatFs(_downloadsDirectory.absolutePath);

    private val _downloaded = FragmentedStorage.storeJson<VideoLocal>("downloaded")
        .load()
        .apply { afterLoadingDownloaded(this) };
    private val _downloading = FragmentedStorage.storeJson<VideoDownload>("downloading")
        .load().apply {
            for(video in this.getItems())
                video.changeState(VideoDownload.State.QUEUED);
        };
    private val _downloadPlaylists = FragmentedStorage.storeJson<PlaylistDownloadDescriptor>("playlistDownloads")
        .load();

    private val _exporting = FragmentedStorage.storeJson<VideoExport>("exporting")
        .load();

    private lateinit var _downloadedSet: HashSet<PlatformID>;

    val onExportsChanged = Event0();
    val onDownloadsChanged = Event0();
    val onDownloadedChanged = Event0();

    private fun afterLoadingDownloaded(v: ManagedStore<VideoLocal>) {
        _downloadedSet = HashSet(v.getItems().map { it.id });
    }

    fun getTotalUsage(reload: Boolean): DiskUsage {
        if(reload)
            _downloadsStat.restat(_downloadsDirectory.absolutePath);
        val usage = _downloadsDirectory.listFiles()?.sumOf { it.length() } ?: 0;
        val available = _downloadsStat.availableBytes;
        return DiskUsage(usage, available);
    }

    fun getCachedVideo(id: PlatformID): VideoLocal? {
        return _downloaded.findItem  { it.id.equals(id) };
    }
    fun updateCachedVideo(vid: VideoLocal) {
        Logger.i("StateDownloads", "Updating local video ${vid.name}");
        _downloaded.save(vid);
        onDownloadedChanged.emit();
    }
    fun deleteCachedVideo(id: PlatformID) {
        Logger.i("StateDownloads", "Deleting local video ${id.value}");
        val downloaded = getCachedVideo(id);
        if(downloaded != null) {
            synchronized(_downloadedSet) {
                _downloadedSet.remove(id);
            }
            _downloaded.delete(downloaded);
        }
        onDownloadedChanged.emit();
    }

    fun isDownloaded(id: PlatformID): Boolean {
        synchronized(_downloadedSet) {
            return _downloadedSet.contains(id);
        }
    }

    fun getCachedPlaylists(): List<PlaylistDownloaded> {
        return _downloadPlaylists.getItems()
            .map { Pair(it, StatePlaylists.instance.getPlaylist(it.id)) }
            .filter { it.second != null }
            .map { PlaylistDownloaded(it.first, it.second!!) }
            .toList();
    }
    fun hasCachedPlaylist(playlistId: String): Boolean {
        return _downloadPlaylists.hasItem {  it.id == playlistId };
    }
    fun getCachedPlaylist(playlistId: String): PlaylistDownloaded? {
        val descriptor = getPlaylistDownload(playlistId) ?: return null;
        val playlist = StatePlaylists.instance.getPlaylist(playlistId) ?: return null;
        return PlaylistDownloaded(descriptor, playlist);
    }
    fun getPlaylistDownload(playlistId: String): PlaylistDownloadDescriptor? {
        return _downloadPlaylists.findItem { it.id == playlistId };
    }
    fun savePlaylistDownload(playlistDownload: PlaylistDownloadDescriptor) {
        synchronized(playlistDownload.preventDownload) {
            _downloadPlaylists.save(playlistDownload);
        }
    }
    fun deleteCachedPlaylist(id: String) {
        val pdl = getPlaylistDownload(id);
        if(pdl != null)
            _downloadPlaylists.delete(pdl);
        getDownloading().filter { it.groupType == VideoDownload.GROUP_PLAYLIST && it.groupID == id }
            .forEach { removeDownload(it) };
        getDownloadedVideos().filter { it.groupType == VideoDownload.GROUP_PLAYLIST && it.groupID == id }
            .forEach { deleteCachedVideo(it.id) };
    }

    fun getDownloadedVideos(): List<VideoLocal> {
        return _downloaded.getItems();
    }

    fun getDownloadPlaylists(): List<PlaylistDownloadDescriptor> {
        return _downloadPlaylists.getItems();
    }
    fun isPlaylistCached(id: String): Boolean {
        return getDownloadPlaylists().any{it.id == id};
    }

    fun getDownloading(): List<VideoDownload> {
        return _downloading.getItems();
    }
    fun updateDownloading(download: VideoDownload) {
        _downloading.save(download, false, true);
    }


    fun removeDownload(download: VideoDownload) {
        download.isCancelled = true;
        _downloading.delete(download);
        onDownloadsChanged.emit();
    }
    fun preventPlaylistDownload(download: VideoDownload) {
        if(download.video != null && download.groupID != null && download.groupType == VideoDownload.GROUP_PLAYLIST) {
            getPlaylistDownload(download.groupID!!)?.let {
                synchronized(it.preventDownload) {
                    if(download.video?.url != null && !it.preventDownload.contains(download.video!!.url)) {
                        it.preventDownload.add(download.video!!.url);
                        savePlaylistDownload(it);
                        Logger.w(TAG, "Preventing further download attempts in playlist [${it.id}] for [${download.name}]:${download.video?.url}");
                    }
                }
            }
        }
    }

    fun checkForDownloadsTodos() {
        val hasPlaylistChanged = checkForOutdatedPlaylists();
        val hasDownloads = _downloading.hasItems();

        if((hasPlaylistChanged || hasDownloads) && Settings.instance.downloads.shouldDownload())
            StateApp.withContext {
                DownloadService.getOrCreateService(it);
            }
    }
    fun checkForOutdatedPlaylists(): Boolean {
        var hasChanged = false;
        val playlistsDownloaded = getCachedPlaylists();
        for(playlist in playlistsDownloaded) {
            val playlistDownload = getPlaylistDownload(playlist.playlist.id) ?: continue;
            val toIgnore = playlistDownload.getPreventDownloadList();
            val missingVideoCount = playlist.playlist.videos.count { !toIgnore.contains(it.url) && getCachedVideo(it.id) == null };
            if(missingVideoCount > 0) {
                Logger.i(TAG, "Found new videos (${missingVideoCount}) on playlist [${playlist.playlist.name}] to download");
                continueDownload(playlistDownload, playlist.playlist);
                hasChanged = true;
            }
            else
                Logger.v(TAG, "Offline playlist [${playlist.playlist.name}] is up to date");
        }
        return hasChanged;
    }

    fun continueDownload(playlistDownload: PlaylistDownloadDescriptor, playlist: Playlist) {
        var hasNew = false;
        for(item in playlist.videos) {
            val existing = getCachedVideo(item.id);

            if(!playlistDownload.shouldDownload(item)) {
                Logger.i(TAG, "Not downloading for playlist [${playlistDownload.id}] Video [${item.name}]:${item.url}")
                continue;
            }
            if(existing == null) {
                val ongoingDownload = getDownloading().find { it.id.value == item.id.value && it.id.value != null };
                if(ongoingDownload != null) {
                    Logger.i(TAG, "New playlist video (already downloading) ${item.name}");
                    ongoingDownload.groupID = playlist.id;
                    ongoingDownload.groupType = VideoDownload.GROUP_PLAYLIST;
                }
                else {
                    Logger.i(TAG, "New playlist video ${item.name}");
                    download(VideoDownload(item, playlistDownload.targetPxCount, playlistDownload.targetBitrate)
                        .withGroup(VideoDownload.GROUP_PLAYLIST, playlist.id), false);
                    hasNew = true;
                }
            }
            else {
                Logger.i(TAG, "New playlist video (already downloaded) ${item.name}");
                if(existing.groupID == null) {
                    existing.groupID = playlist.id;
                    existing.groupType = VideoDownload.GROUP_PLAYLIST;
                    synchronized(_downloadedSet) {
                        _downloadedSet.add(existing.id);
                    }
                    _downloaded.save(existing);
                }
            }
        }
        if(playlist.videos.isNotEmpty() && Settings.instance.downloads.shouldDownload()) {
            if(hasNew) {
                UIDialogs.toast("Downloading [${playlist.name}]")
                StateApp.withContext {
                    DownloadService.getOrCreateService(it);
                }
            }
            onDownloadsChanged.emit();
        }
    }
    fun download(playlist: Playlist, targetPixelcount: Long?, targetBitrate: Long?) {
        val playlistDownload = PlaylistDownloadDescriptor(playlist.id, targetPixelcount, targetBitrate);
        _downloadPlaylists.save(playlistDownload);
        continueDownload(playlistDownload, playlist);
    }
    fun download(video: IPlatformVideo, targetPixelcount: Long?, targetBitrate: Long?) {
        download(VideoDownload(video, targetPixelcount, targetBitrate));
    }
    fun download(video: IPlatformVideoDetails, videoSource: IVideoUrlSource?, audioSource: IAudioUrlSource?, subtitleSource: SubtitleRawSource?) {
        download(VideoDownload(video, videoSource, audioSource, subtitleSource));
    }

    private fun download(videoState: VideoDownload, notify: Boolean = true) {
        val shortName = if(videoState.name.length > 23)
            videoState.name.substring(0, 20) + "...";
        else
            videoState.name;

        try {
            validateDownload(videoState);
            _downloading.save(videoState);


            if(notify) {
                if(Settings.instance.downloads.shouldDownload()) {
                    UIDialogs.toast("Downloading [${shortName}]")
                    StateApp.withContext {
                        DownloadService.getOrCreateService(it);
                    }
                    onDownloadsChanged.emit();
                }
                else {
                    UIDialogs.toast("Registered [${shortName}]\n(Can't download now)");
                }
            }
        }
        catch(ex: Throwable) {
            Logger.e(TAG, "Failed to start download", ex);
            StateApp.withContext {
                UIDialogs.showDialog(
                    it,
                    R.drawable.ic_error,
                    "Failed to start download due to:\n${ex.message}", null, null,
                    0,
                    UIDialogs.Action("Ok", {}, UIDialogs.ActionStyle.PRIMARY)
                );
            }
        }
    }
    private fun validateDownload(videoState: VideoDownload) {
        if(_downloading.hasItem { it.videoEither.url == videoState.videoEither.url })
            throw IllegalStateException("Video [${videoState.name}] is already queued for dowload");

        val existing = getCachedVideo(videoState.id);
        if(existing != null) {
            //Verify for better video
            val targetPx = if(videoState.targetPixelCount != null)
                videoState.targetPixelCount!!.toInt();
            else if(videoState.videoSource != null)
                videoState.videoSource!!.width * videoState.videoSource!!.height;
            else
                null;
            if(targetPx != null) {
                val bestExistingVideo = existing.videoSource.maxBy { it.width * it.height };
                val bestPx = bestExistingVideo.height * bestExistingVideo.width;
                if (bestPx.toFloat() / targetPx >= 0.85f)
                    throw IllegalStateException("A higher resolution video source is already downloaded");
            }

            //Verify for better bitrate
            val targetBitrate = if(videoState.targetBitrate != null)
                videoState.targetBitrate!!.toInt();
            else if(videoState.audioSource != null)
                videoState.audioSource!!.bitrate;
            else
                null;
            if(targetBitrate != null) {
                val bestExistingAudio = existing.audioSource.maxBy { it.bitrate };
                if(bestExistingAudio.bitrate / targetBitrate >= 0.85f)
                    throw IllegalStateException("A higher bitrate audio source is already downloaded");
            }
        }
    }

    suspend fun downloadSubtitles(subtitle: ISubtitleSource, contentResolver: ContentResolver): SubtitleRawSource? {
        val subtitleUri = subtitle.getSubtitlesURI();
        if(subtitleUri == null)
            return null;
        var subtitles: String? = null;
        if ("file" == subtitleUri.scheme) {
            val inputStream = contentResolver.openInputStream(subtitleUri);
            inputStream?.use { stream ->
                val reader = stream.bufferedReader();
                subtitles = reader.use { it.readText() };
            }
        } else if ("http" == subtitleUri.scheme || "https" == subtitleUri.scheme) {
            val client = ManagedHttpClient();
            val subtitleResponse = client.get(subtitleUri.toString());
            if (!subtitleResponse.isOk) {
                throw Exception("Cannot fetch subtitles from source '${subtitleUri}': ${subtitleResponse.code}");
            }

            subtitles = subtitleResponse.body?.toString()
                ?: throw Exception("Subtitles are invalid '${subtitleUri}': ${subtitleResponse.code}");
        } else {
            throw NotImplementedError("Unsuported scheme");
        }
        return if (subtitles != null) SubtitleRawSource(subtitle.name, subtitle.format, subtitles!!) else null;
    }

    fun cleanupDownloads(): Pair<Int, Long> {
        val expected = getDownloadedVideos();
        val validFiles = HashSet(expected.flatMap { e -> e.videoSource.map { it.filePath } + e.audioSource.map { it.filePath } });

        var totalDeleted: Long = 0;
        var totalDeletedCount = 0;
        _downloadsDirectory.listFiles()?.let {
            for(file in it) {
                val absUrl = file.absolutePath;
                if(!validFiles.contains(absUrl)) {
                    Logger.i("StateDownloads", "Deleting unresolved ${file.name}");
                    totalDeletedCount++;
                    totalDeleted += file.length();
                    file.delete();
                }
            }
        }
        return Pair(totalDeletedCount, totalDeleted);
    }

    fun getDownloadsDirectory(): File{
        return _downloadsDirectory;
    }



    //Export
    fun getExporting(): List<VideoExport> {
        return _exporting.getItems();
    }
    fun checkForExportTodos() {
        if(_exporting.hasItems()) {
            StateApp.withContext {
                ExportingService.getOrCreateService(it);
            }
        }
    }

    fun validateExport(export: VideoExport) {
        if(_exporting.hasItem { it.videoLocal.url == export.videoLocal.url })
            throw AlreadyQueuedException("Video [${export.videoLocal.name}] is already queued for export");
    }
    fun export(videoLocal: VideoLocal, videoSource: LocalVideoSource?, audioSource: LocalAudioSource?, subtitleSource: LocalSubtitleSource?, notify: Boolean = true) {
        val shortName = if(videoLocal.name.length > 23)
            videoLocal.name.substring(0, 20) + "...";
        else
            videoLocal.name;

        val videoExport = VideoExport(videoLocal, videoSource, audioSource, subtitleSource);

        try {
            validateExport(videoExport);
            _exporting.save(videoExport);

            if(notify) {
                UIDialogs.toast("Exporting [${shortName}]");
                StateApp.withContext { ExportingService.getOrCreateService(it) };
                onExportsChanged.emit();
            }
        }
        catch (ex: AlreadyQueuedException) {
            Logger.e(TAG, "File is already queued for export.", ex);
            StateApp.withContext { ExportingService.getOrCreateService(it) };
        }
        catch(ex: Throwable) {
            StateApp.withContext {
                UIDialogs.showDialog(
                    it,
                    R.drawable.ic_error,
                    "Failed to start export due to:\n${ex.message}", null, null,
                    0,
                    UIDialogs.Action("Ok", {}, UIDialogs.ActionStyle.PRIMARY)
                );
            }
        }
    }


    fun removeExport(export: VideoExport) {
        _exporting.delete(export);
        export.isCancelled = true;
        onExportsChanged.emit();
    }

    companion object {
        const val TAG = "StateDownloads";

        private var _instance : StateDownloads? = null;
        val instance : StateDownloads
            get(){
            if(_instance == null)
                _instance = StateDownloads();
            return _instance!!;
        };

        fun finish() {
            _instance?.let {
                _instance = null;
            }
        }
    }
}