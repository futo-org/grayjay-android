package com.futo.platformplayer.states

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.documentfile.provider.DocumentFile
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.IWithResultLauncher
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.modifier.IRequestModifier
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalSubtitleSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.SubtitleRawSource
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.downloads.DownloadMover
import com.futo.platformplayer.downloads.DownloadPaths
import com.futo.platformplayer.downloads.PlaylistDownloadDescriptor
import com.futo.platformplayer.downloads.VideoDownload
import com.futo.platformplayer.downloads.VideoExport
import com.futo.platformplayer.downloads.VideoLocal
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.DiskUsage
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.models.PlaylistDownloaded
import com.futo.platformplayer.exceptions.DownloadException
import com.futo.platformplayer.services.DownloadService
import com.futo.platformplayer.share
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.v2.ManagedStore
import com.futo.platformplayer.toHumanBytesSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/***
 * Used to maintain downloads
 */
class StateDownloads {
    private val _internalDownloadsDirectory: File = FragmentedStorage.getOrCreateDirectory("downloads");

    private val _downloaded = FragmentedStorage.storeJson<VideoLocal>("downloaded")
        .withOnModified({
            synchronized(_downloadedSet) {
                if(!_downloadedSet.contains(it.id))
                    _downloadedSet.add(it.id);
            }
        }, {
            synchronized(_downloadedSet) {
                if(_downloadedSet.contains(it.id))
                    _downloadedSet.remove(it.id);
            }
        })
        .load()
        .apply { afterLoadingDownloaded(this) };
    private val _downloading = FragmentedStorage.storeJson<VideoDownload>("downloading")
        .load().apply {
            for(video in this.getItems())
                video.changeState(VideoDownload.State.QUEUED);
        };
    private val _downloadPlaylists = FragmentedStorage.storeJson<PlaylistDownloadDescriptor>("playlistDownloads")
        .load();

    private lateinit var _downloadedSet: HashSet<PlatformID>;

    val onDownloadsChanged = Event0();
    val onDownloadedChanged = Event0();

    private fun afterLoadingDownloaded(v: ManagedStore<VideoLocal>) {
        _downloadedSet = HashSet(v.getItems().map { it.id });
    }

    fun getTotalUsage(reload: Boolean): DiskUsage {
        val directories = listOf(_internalDownloadsDirectory, getDownloadsDirectory()).distinctBy { it.absolutePath };
        val usage = directories.sumOf { dir -> dir.listFiles()?.sumOf { it.length() } ?: 0L };
        return DiskUsage(usage, getAvailableBytes(getDownloadsDirectory()));
    }

    /** Free bytes in [directory], or 0 if it is not mounted. */
    fun getAvailableBytes(directory: File): Long {
        return try {
            if (directory.exists()) StatFs(directory.absolutePath).availableBytes else 0L;
        } catch (ex: Throwable) {
            Logger.w(TAG, "Failed to read free space of [${directory.absolutePath}]", ex);
            0L;
        }
    }

    /** The folder the user picked in settings, or null when downloads stay in app storage. */
    fun getPickedFolder(): File? = Settings.instance.downloads.downloadFolderPath?.let { File(it) };

    /** The folder new downloads go to. A picked folder can be missing (for example, SD card out). */
    fun getDownloadsDirectory(): File {
        return getPickedFolder()?.let { File(it, DownloadPaths.SUBFOLDER_NAME) } ?: _internalDownloadsDirectory;
    }

    fun isDownloadsDirectoryAvailable(): Boolean {
        val picked = getPickedFolder() ?: return true;
        if (!hasAllFilesAccess())
            return false;
        val dir = File(picked, DownloadPaths.SUBFOLDER_NAME);
        return picked.isDirectory && (dir.isDirectory || dir.mkdirs()) && dir.canWrite();
    }

    /** Returns the download folder, ready to write to. Throws if it is not available. */
    fun prepareDownloadsDirectory(): File {
        val dir = getDownloadsDirectory();
        if (!isDownloadsDirectoryAvailable())
            throw DownloadException("Download folder is not available: ${dir.absolutePath}. Insert the SD card, allow \"All files access\", or pick another folder in Settings > Downloads.", true);
        if (getPickedFolder() != null)
            claimDownloadsDirectory(dir);
        return dir;
    }

    /** Throws if [directory] has less than [estimatedBytes] free, plus a margin. */
    fun checkFreeSpace(directory: File, estimatedBytes: Long) {
        val available = getAvailableBytes(directory);
        val needed = estimatedBytes.coerceAtLeast(0) + FREE_SPACE_MARGIN_BYTES;
        if (available < needed)
            throw DownloadException("Not enough free space in ${directory.absolutePath}: needs about ${needed.toHumanBytesSize()}, ${available.toHumanBytesSize()} free.", true);
    }

    /** A picked folder needs "All files access" (Android 11+). */
    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager();

    fun resolveFile(storedPath: String): File = DownloadPaths.resolve(storedPath, getDownloadsDirectory());
    fun toStoredPath(absolutePath: String): String = DownloadPaths.toStoredPath(absolutePath, getDownloadsDirectory());

    /**
     * Sets the download folder ([path] null = app storage). Existing items keep working:
     * their file names are pinned to the old folder as absolute paths. Files that are
     * already in the new folder get relative names.
     */
    fun changeDownloadFolder(path: String?) {
        val oldDirectory = getDownloadsDirectory();
        Settings.instance.downloads.downloadFolderPath = path;
        Settings.instance.save();

        val newDirectory = getDownloadsDirectory();
        for (item in getDownloadedVideos()) {
            if (item.mapFilePaths { DownloadPaths.rebase(it, oldDirectory, newDirectory) })
                _downloaded.save(item);
        }

        Logger.i(TAG, "Download folder changed from [${oldDirectory.absolutePath}] to [${getDownloadsDirectory().absolutePath}]");

        if (path != null && isDownloadsDirectoryAvailable())
            claimDownloadsDirectory(getDownloadsDirectory());
        onDownloadedChanged.emit();
    }

    /** Files of [item] that are not found (SD card out, folder moved, file deleted by hand). */
    fun getMissingFiles(item: VideoLocal): List<File> =
        (item.videoSource.map { it.filePath } + item.audioSource.map { it.filePath }).map { resolveFile(it) }.filter { !it.isFile };

    fun isMissing(item: VideoLocal): Boolean = getMissingFiles(item).isNotEmpty();

    /** Folder where the missing files of [item] are expected. */
    fun getMissingLocation(item: VideoLocal): String =
        getMissingFiles(item).firstOrNull()?.parentFile?.absolutePath ?: getDownloadsDirectory().absolutePath;

    /**
     * Looks for missing files by name in [folder] and in its "Grayjay Downloads" subfolder,
     * and points the items to the files it finds. Returns the number of items fixed.
     */
    fun relinkMissing(folder: File): Int {
        var fixed = 0;
        for (item in getDownloadedVideos()) {
            if (!isMissing(item))
                continue;
            val changed = item.mapFilePaths { stored ->
                if (resolveFile(stored).isFile)
                    return@mapFilePaths stored;
                val found = DownloadPaths.findByName(File(stored).name, folder);
                if (found != null) toStoredPath(found.absolutePath) else stored;
            };
            if (changed) {
                _downloaded.save(item);
                if (!isMissing(item))
                    fixed++;
            }
        }
        onDownloadedChanged.emit();
        return fixed;
    }

    /** Removes the sources of [item] whose files are missing, so a new download can replace them. */
    fun dropMissingSources(item: VideoLocal) {
        item.videoSource = ArrayList(item.videoSource.filter { resolveFile(it.filePath).isFile });
        item.audioSource = ArrayList(item.audioSource.filter { resolveFile(it.filePath).isFile });
        item.subtitlesSources = ArrayList(item.subtitlesSources.filter { resolveFile(it.filePath).isFile });
    }

    /** Queues a new download of [item] with the same quality. The item stays in the list until the new files arrive. */
    fun downloadAgain(item: VideoLocal) {
        val pixels = item.videoSource.maxOfOrNull { it.width * it.height }?.toLong();
        val bitrate = item.audioSource.maxOfOrNull { it.bitrate }?.toLong();
        download(VideoDownload(item.videoSerialized, pixels, bitrate));
    }

    /** Items with at least one file that exists outside the current download folder. */
    fun getItemsOutsideDownloadsDirectory(): List<VideoLocal> {
        val mover = DownloadMover(getDownloadsDirectory());
        return getDownloadedVideos().filter { item ->
            item.getFilePaths().map { resolveFile(it) }.any { it.isFile && !mover.isInTarget(it) }
        };
    }

    class MoveResult(val movedItems: Int, val totalItems: Int, val error: Throwable?);

    /**
     * Moves the files of all items into the current download folder.
     * Per item: copy each file, check it, save the item with the new names, then delete the old files.
     * On an error it stops; the old files stay and a new run continues where it stopped.
     */
    fun moveDownloadsToCurrentFolder(onProgress: (itemIndex: Int, itemCount: Int, fraction: Double) -> Unit, isCancelled: () -> Boolean = { false }): MoveResult {
        if (DownloadService.getService() != null)
            return MoveResult(0, 0, IllegalStateException("Downloads are running. Wait until they finish, then try again."));

        val items = getItemsOutsideDownloadsDirectory();
        var moved = 0;
        try {
            val directory = prepareDownloadsDirectory();
            val mover = DownloadMover(directory);
            val sources = items.flatMap { item -> item.getFilePaths().map { resolveFile(it) }.filter { it.isFile && !mover.isInTarget(it) } };
            checkFreeSpace(directory, DownloadMover.bytesNeeded(sources, directory));

            val totalBytes = sources.sumOf { it.length() }.coerceAtLeast(1L);
            var doneBytes = 0L;
            val oldFolders = HashSet<File>();
            for ((index, item) in items.withIndex()) {
                val copied = LinkedHashMap<String, Pair<File, File>>();
                for (storedPath in item.getFilePaths()) {
                    val source = resolveFile(storedPath);
                    if (!source.isFile || mover.isInTarget(source))
                        continue;
                    val target = mover.copyIntoTarget(source, { bytes ->
                        doneBytes += bytes;
                        onProgress(index, items.size, doneBytes.toDouble() / totalBytes);
                    }, isCancelled);
                    copied[storedPath] = Pair(source, target);
                }

                item.mapFilePaths { path -> copied[path]?.let { toStoredPath(it.second.absolutePath) } ?: path };
                _downloaded.save(item);

                for ((source, target) in copied.values) {
                    if (!mover.deleteSource(source, target))
                        Logger.w(TAG, "Did not delete old file [${source.absolutePath}] after move");
                    source.parentFile?.let { oldFolders.add(it) };
                }
                moved++;
            }
            oldFolders.forEach { removeEmptyGrayjayFolder(it) };
        } catch (ex: Throwable) {
            Logger.e(TAG, "Move of downloads stopped after $moved of ${items.size} items", ex);
            onDownloadedChanged.emit();
            return MoveResult(moved, items.size, ex);
        }
        onDownloadedChanged.emit();
        return MoveResult(moved, items.size, null);
    }

    /** Deletes an old "Grayjay Downloads" folder if only Grayjay's own marker files are in it. */
    private fun removeEmptyGrayjayFolder(folder: File) {
        if (folder.name != DownloadPaths.SUBFOLDER_NAME || folder.absolutePath == getDownloadsDirectory().absolutePath)
            return;
        val files = folder.listFiles() ?: return;
        if (files.all { it.isFile && it.name in KEEP_FILE_NAMES }) {
            files.forEach { it.delete() };
            folder.delete();
        }
    }

    /** Writes the owner file if the folder has none. Returns true if this install owns the folder. */
    private fun claimDownloadsDirectory(directory: File): Boolean {
        val ownerFile = File(directory, DownloadPaths.OWNER_FILE_NAME);
        val packageName = StateApp.instance.contextOrNull?.packageName ?: return false;
        return try {
            if (!ownerFile.exists())
                ownerFile.writeText(packageName);
            ownerFile.readText().trim() == packageName;
        } catch (ex: Throwable) {
            Logger.w(TAG, "Failed to read or write owner file in [${directory.absolutePath}]", ex);
            false;
        }
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
            _downloaded.delete(downloaded);
        }
        onDownloadedChanged.emit();
    }

    fun isDownloaded(id: PlatformID): Boolean {
        synchronized(_downloadedSet) {
            return _downloadedSet.contains(id);
        }
    }

    fun getWatchLaterDescriptor(): PlaylistDownloadDescriptor? {
        return _downloadPlaylists.getItems().find { it.id == VideoDownload.GROUP_WATCHLATER };
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
        if(id == VideoDownload.GROUP_WATCHLATER) {
            getDownloading().filter { it.groupType == VideoDownload.GROUP_WATCHLATER && it.groupID == id }
                .forEach { removeDownload(it) };
            getDownloadedVideos().filter { it.groupType == VideoDownload.GROUP_WATCHLATER && it.groupID == id }
                .forEach { deleteCachedVideo(it.id) };
        }
        else {
            getDownloading().filter { it.groupType == VideoDownload.GROUP_PLAYLIST && it.groupID == id }
                .forEach { removeDownload(it) };
            getDownloadedVideos().filter { it.groupType == VideoDownload.GROUP_PLAYLIST && it.groupID == id }
                .forEach { deleteCachedVideo(it.id) };
        }
    }

    fun getDownloadedVideos(): List<VideoLocal> {
        return _downloaded.getItems();
    }
    fun getDownloadedVideosPlaylist(str: String): List<VideoLocal> {
        val videos = _downloaded.findItems { it.groupID == str };
        return videos;
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
        download.cleanup();
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

    fun checkForOutdatedPlaylistVideos(playlistId: String) {
        val playlistVideos = if(playlistId == VideoDownload.GROUP_WATCHLATER)
            (if(getWatchLaterDescriptor() != null) StatePlaylists.instance.getWatchLater() else listOf())
        else
            getCachedPlaylist(playlistId)?.playlist?.videos ?: return;
        val playlistVideosDownloaded = getDownloadedVideosPlaylist(playlistId);
        val urls = playlistVideos.map { it.url }.toHashSet();
        for(item in playlistVideosDownloaded) {
            if(!urls.contains(item.url)) {
                Logger.i(TAG, "Playlist [${playlistId}] deleting removed video [${item.name}]");
                deleteCachedVideo(item.id);
            }
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
        val downloadWatchLater = getWatchLaterDescriptor();
        if(downloadWatchLater != null) {
            continueDownloadWatchLater(downloadWatchLater);
        }
        return hasChanged;
    }

    fun continueDownloadWatchLater(playlistDownload: PlaylistDownloadDescriptor) {
        var hasNew = false;
        val watchLater = StatePlaylists.instance.getWatchLater();
        for(item in watchLater) {
            val existing = getCachedVideo(item.id);

            if(!playlistDownload.shouldDownload(item)) {
                Logger.i(TAG, "Not downloading for watchlater [${playlistDownload.id}] Video [${item.name}]:${item.url}")
                continue;
            }
            if(existing == null) {
                val ongoingDownload = getDownloading().find { it.id.value == item.id.value && it.id.value != null };
                if(ongoingDownload != null) {
                    Logger.i(TAG, "New watchlater video (already downloading) ${item.name}");
                    ongoingDownload.groupID = VideoDownload.GROUP_WATCHLATER;
                    ongoingDownload.groupType = VideoDownload.GROUP_WATCHLATER;
                }
                else {
                    Logger.i(TAG, "New watchlater video ${item.name}");
                    download(VideoDownload(item, playlistDownload.targetPxCount, playlistDownload.targetBitrate, true)
                        .withGroup(VideoDownload.GROUP_WATCHLATER, VideoDownload.GROUP_WATCHLATER), false);
                    hasNew = true;
                }
            }
            else {
                Logger.i(TAG, "New watchlater video (already downloaded) ${item.name}");
                if(existing.groupID == null) {
                    existing.groupID = VideoDownload.GROUP_WATCHLATER;
                    existing.groupType = VideoDownload.GROUP_WATCHLATER;
                    _downloaded.save(existing);
                }
            }
        }
        if(watchLater.isNotEmpty() && Settings.instance.downloads.shouldDownload()) {
            if(hasNew) {
                UIDialogs.toast("Downloading [Watch Later]")
                StateApp.withContext {
                    DownloadService.getOrCreateService(it);
                }
            }
            onDownloadsChanged.emit();
        }
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
                    download(VideoDownload(item, playlistDownload.targetPxCount, playlistDownload.targetBitrate, true)
                        .withGroup(VideoDownload.GROUP_PLAYLIST, playlist.id), false);
                    hasNew = true;
                }
            }
            else {
                Logger.i(TAG, "New playlist video (already downloaded) ${item.name}");
                if(existing.groupID == null) {
                    existing.groupID = playlist.id;
                    existing.groupType = VideoDownload.GROUP_PLAYLIST;
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
    fun downloadWatchLater(targetPixelCount: Long?, targetBitrate: Long?) {
        val playlistDownload = PlaylistDownloadDescriptor(VideoDownload.GROUP_WATCHLATER, targetPixelCount, targetBitrate);
        _downloadPlaylists.save(playlistDownload);
        continueDownloadWatchLater(playlistDownload);
    }
    fun download(playlist: Playlist, targetPixelcount: Long?, targetBitrate: Long?) {
        val playlistDownload = PlaylistDownloadDescriptor(playlist.id, targetPixelcount, targetBitrate);
        _downloadPlaylists.save(playlistDownload);
        continueDownload(playlistDownload, playlist);
    }
    fun download(video: IPlatformVideo, targetPixelcount: Long?, targetBitrate: Long?) {
        download(VideoDownload(video, targetPixelcount, targetBitrate));
    }
    fun download(video: IPlatformVideoDetails, videoSource: IVideoSource?, audioSource: IAudioSource?, subtitleSource: SubtitleRawSource?, videoModifier: IRequestModifier? = null, audioModifier: IRequestModifier? = null) {
        download(VideoDownload(video, videoSource, audioSource, subtitleSource, videoModifier, audioModifier));
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
            throw IllegalStateException("Video [${videoState.name}] is already queued for download");

        val existing = getCachedVideo(videoState.id)?.let { item ->
            //Missing files do not count: a new download replaces them.
            if (isMissing(item)) null else item
        };
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
        return if (subtitles != null) SubtitleRawSource(subtitle.name, subtitle.language, subtitle.format, subtitles!!) else null;
    }

    fun cleanupDownloads(): Pair<Int, Long> {
        val expected = getDownloadedVideos();
        val validFiles = HashSet(expected.flatMap { e -> e.getFilePaths().map { resolveFile(it).absolutePath } });

        val directories = arrayListOf(_internalDownloadsDirectory);
        //In a picked folder, only touch Grayjay's own subfolder, and only if this install owns it.
        val picked = getPickedFolder();
        if (picked != null) {
            val pickedDirectory = File(picked, DownloadPaths.SUBFOLDER_NAME);
            if (pickedDirectory.isDirectory && hasAllFilesAccess() && claimDownloadsDirectory(pickedDirectory))
                directories.add(pickedDirectory);
            else
                Logger.i(TAG, "Skipping cleanup of [${pickedDirectory.absolutePath}] (not available or owned by another install)");
        }

        var totalDeleted: Long = 0;
        var totalDeletedCount = 0;
        for (directory in directories) {
            val isPicked = directory != _internalDownloadsDirectory;
            directory.listFiles()?.let {
                for(file in it) {
                    if (isPicked && (!file.isFile || file.name in KEEP_FILE_NAMES))
                        continue;
                    val absUrl = file.absolutePath;
                    if(!validFiles.contains(absUrl)) {
                        Logger.i("StateDownloads", "Deleting unresolved ${file.name}");
                        totalDeletedCount++;
                        totalDeleted += file.length();
                        file.delete();
                    }
                }
            }
        }

        return Pair(totalDeletedCount, totalDeleted);
    }

    fun exportPlaylist(context: Context, playlistId: String) {
        if(context is IWithResultLauncher)
            StateApp.instance.requestDirectoryAccess(context, "Export Playlist", "To export playlist to directory", null) {
                if (it == null)
                    return@requestDirectoryAccess;

                val root = DocumentFile.fromTreeUri(context, it!!);

                val playlist = StatePlaylists.instance.getPlaylist(playlistId);
                var localVideos = StateDownloads.instance.getDownloadedVideosPlaylist(playlistId);
                if(playlist != null) {
                    val missing = playlist.videos
                        .filter { vid -> !localVideos.any { it.id.value == null || it.id.value == vid.id.value  } }
                        .map { getCachedVideo(it.id) }
                        .filterNotNull();
                    if(missing.size > 0)
                        localVideos = localVideos + missing;
                };

                var lastNotifyTime = -1L;

                UIDialogs.showDialogProgress(context) {
                    StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                        it.setText("Exporting videos..");
                        var i = 0;
                        var success = 0;
                        for (video in localVideos) {
                            withContext(Dispatchers.Main) {
                                it.setText("Exporting videos...(${i}/${localVideos.size})");
                            }

                            try {
                                val export = VideoExport(video, video.videoSource.firstOrNull(), video.audioSource.firstOrNull(), video.subtitlesSources.firstOrNull());
                                Logger.i(TAG, "Exporting [${export.videoLocal.name}] started");

                                val file = export.export(context, { progress ->
                                    val now = System.currentTimeMillis();
                                    if (lastNotifyTime == -1L || now - lastNotifyTime > 100) {
                                        it.setProgress(progress);
                                        lastNotifyTime = now;
                                    }
                                }, root);
                                success++;
                            } catch(ex: Throwable) {
                                Logger.e(TAG, "Failed export [${video.name}]: ${ex.message}", ex);
                            }
                            i++;
                        }
                        withContext(Dispatchers.Main) {
                            it.setProgress(1f);
                            it.dismiss();
                            UIDialogs.appToast("Finished exporting playlist (${success} videos${if(i < success) ", ${i} errors" else ""})");
                        }
                    };
                }
            }
    }

    fun export(context: Context, videoLocal: VideoLocal, videoSource: LocalVideoSource?, audioSource: LocalAudioSource?, subtitleSource: LocalSubtitleSource?) {
        var lastNotifyTime = -1L;

        UIDialogs.showDialogProgress(context) {
            it.setText("Exporting content..");
            it.setProgress(0f);
            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                val export = VideoExport(videoLocal, videoSource, audioSource, subtitleSource);
                try {
                    Logger.i(TAG, "Exporting [${export.videoLocal.name}] started");

                    val file = export.export(context, { progress ->
                        val now = System.currentTimeMillis();
                        if (lastNotifyTime == -1L || now - lastNotifyTime > 100) {
                            StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                                it.setProgress(progress);
                            }
                            lastNotifyTime = now;
                        }
                    }, null);

                    withContext(Dispatchers.Main) {
                        it.setProgress(100.0f)
                        it.dismiss()

                        StateAnnouncement.instance.registerAnnouncement(UUID.randomUUID().toString(), "File exported", "Exported [${file.uri}]", AnnouncementType.SESSION, time = null, category = "download", actionButton = "Open") {
                            file.share(context);
                        };
                    }
                } catch(ex: Throwable) {
                    Logger.e(TAG, "Failed export [${export.videoLocal.name}]: ${ex.message}", ex);

                }
            }
        }
    }

    companion object {
        const val TAG = "StateDownloads";
        private const val FREE_SPACE_MARGIN_BYTES = 50L * 1024 * 1024;
        private val KEEP_FILE_NAMES = setOf(DownloadPaths.OWNER_FILE_NAME);

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