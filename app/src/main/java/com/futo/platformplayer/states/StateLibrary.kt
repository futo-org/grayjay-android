package com.futo.platformplayer.states

import android.content.ContentUris
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Artists
import android.webkit.MimeTypeMap
import androidx.core.database.getStringOrNull
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.Thumbnail
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.models.video.LocalVideoDetails
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.api.media.structures.AdhocPager
import com.futo.platformplayer.api.media.structures.EmptyPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.states.Album.Companion.TAG
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringArrayStorage
import com.futo.platformplayer.toList
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset


class StateLibrary {

    private val _files = FragmentedStorage.get<StringArrayStorage>("libraryFiles")


    fun getFileDirectories(): List<FileEntry> {
        val context = StateApp.instance.contextOrNull ?: return listOf();
        return _files.getAllValues().map {
            if(it.startsWith("content://")) {
                val uri = it.toUri();
                val docFile = DocumentFile.fromTreeUri(context, uri) ?: return@map null;
                //val access = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
                if(!docFile.isDirectory) {
                    _files.remove(it);
                    return@map null;
                }
                if(docFile == null)
                    return@map null;
                return@map FileEntry.fromFile(docFile).apply { this.removable = true }
            }
            else
                FileEntry.fromPath(it);
        }.filterNotNull();
    }
    fun deleteFileDirectory(path: String) {
        _files.remove(path);
        _files.save();
    }
    fun addFileDirectory(onAdded: ((entry: FileEntry) -> Unit)? = null, skipDialog: Boolean = false): Boolean {
        if(!StateApp.instance.isMainActive)
            return false;
        val mainActivity = StateApp.instance.contextOrNull as MainActivity? ?: return false;

        StateApp.instance.requestDirectoryAccess(mainActivity, "Select Directory",
                "Select a directory you would like to make accessible to Grayjay", null, {
                    if(it != null) {
                        mainActivity.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_WRITE_URI_PERMISSION.or(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                        try {
                            val file = DocumentFile.fromTreeUri(mainActivity, it) ?: return@requestDirectoryAccess;
                            val dir = FileEntry.fromFile(file);
                            _files.add(dir.path);
                            _files.save();
                            onAdded?.invoke(dir);
                        }
                        catch(ex: Throwable) {
                            Logger.e(TAG, "Something went wrong converting requested directory", ex);
                        }
                    }
                }, skipDialog);
        return false;
    }


    fun searchTracks(str: String): List<IPlatformVideo> {
        if(str.isNullOrBlank())
            return listOf();
        val resolver =  StateApp.instance.contextOrNull?.contentResolver;
        if(resolver == null) {
            Logger.w(TAG, "Album contentResolver not found");
            return listOf();
        }
        val cursor = resolver?.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, StateLibrary.PROJECTION_MEDIA,
            "LOWER(" + MediaStore.Audio.Media.DISPLAY_NAME + ") LIKE ? ", arrayOf("%" + str.trim().lowercase() + "%"),
            null) ?: return listOf();
        return cursor.use {
            cursor.moveToFirst();
            val list = mutableListOf<IPlatformVideo>()
            while(!cursor.isAfterLast) {
                list.add(StateLibrary.audioFromCursor(cursor));
                cursor.moveToNext();
            }
            return@use list;
        }
    }

    fun getAlbums(): List<Album> {
        return Album.getAlbums();
    }
    fun getAlbum(str: String): Album? {
        val idLong = str.toLongOrNull();
        if(idLong != null)
            return getAlbum(idLong);
        return null;
    }
    fun searchAlbums(str: String): List<Album> {
        if(str.isNullOrBlank())
            return listOf();
        return Album.getAlbums("LOWER(" + MediaStore.Audio.Albums.ALBUM + ") LIKE ? ", arrayOf("%" + str.trim().lowercase() + "%"));
    }

    fun getAlbum(id: Long): Album? {
        return Album.getAlbum(id);
    }

    fun getArtists(ordering: ArtistOrdering): List<Artist> {
        return Artist.getArtists(ordering);
    }
    fun getArtist(str: String): Artist? {
        val idLong = str.toLongOrNull();
        if(idLong != null)
            return getArtist(idLong);
        return null;
    }
    fun searchArtists(str: String): List<Artist> {
        if(str.isNullOrBlank())
            return listOf();
        return Artist.getArtists(ArtistOrdering.TrackCount, "LOWER(" + MediaStore.Audio.Artists.ARTIST + ") LIKE ? ", arrayOf("%" + str.trim().lowercase() + "%"));
    }

    fun getArtist(id: Long): Artist? {
        return Artist.getArtist(id);
    }

    fun getVideos(buckets: List<String>? = null): IPager<IPlatformContent> {
        var query = if(buckets != null) "${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} IN " + "(" + buckets.map { "'${it}'" }.joinToString(",") + ")" else null;
        val cursor = StateApp.instance.contextOrNull?.contentResolver?.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, PROJECTION_VIDEO,
            query,
            null,
            MediaStore.Video.Media.DATE_ADDED + " DESC") ?: return EmptyPager();
        return cursor.use {
            cursor.moveToFirst();
            val list = mutableListOf<IPlatformVideo>()
            while(!cursor.isAfterLast && list.size < 10) {
                list.add(videoFromCursor(cursor));
                cursor.moveToNext();
            }

            return@use AdhocPager<IPlatformContent>({
                val list = mutableListOf<IPlatformContent>()
                while(!cursor.isAfterLast && list.size < 10) {
                    list.add(videoFromCursor(cursor));
                    cursor.moveToNext();
                }
                return@AdhocPager list;
            }, list);
        }
    }
    fun getRecentVideos(buckets: List<String>? = null, count: Int = 20): List<IPlatformVideo> {
        val videoPager = getVideos(buckets);
        val items = mutableListOf<IPlatformVideo>();
        while(videoPager.getResults().size > 0 && items.size < count) {
            items.addAll(videoPager.getResults().filter { it is IPlatformVideo }.map { it as IPlatformVideo });
            if(videoPager.hasMorePages())
                videoPager.nextPage();
        }
        return items;
    }

    private var _cacheBucketNames: List<Bucket>? = null;
    fun getVideoBucketNames(): List<Bucket> {
        if(_cacheBucketNames != null)
            return _cacheBucketNames ?: listOf();
        try {
            val cur: Cursor = StateApp.instance.contextOrNull?.contentResolver?.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(
                    MediaStore.Video.Media.BUCKET_ID,
                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                ), null, null, null
            ) ?: return listOf();

            return cur.use {
                val buckets = mutableListOf<Bucket>();
                val list = HashSet<Long>();
                if (cur.moveToFirst()) {
                    var id: Long;
                    var bucket: String
                    do {
                        try {
                            id = cur.getLong(0);
                            bucket = cur.getStringOrNull(1) ?: continue;
                            if (!list.contains(id)) {
                                list.add(id);
                                buckets.add(Bucket(id, bucket));
                            }
                        } catch (ex: Throwable) {
                            Logger.e(TAG, "Failed to parse bucket due to ${ex.message}", ex);
                        }
                    } while (cur.moveToNext())
                }
                _cacheBucketNames = buckets.toList()
                return@use _cacheBucketNames ?: listOf();
            }
        }
        catch(ex: Throwable) {
            Logger.e(TAG, "Buckets loading failed, returning empty");
            return listOf();
        }
    }


    companion object {
        val PROJECTION_VIDEO = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.AUTHOR,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        );
        val PROJECTION_MEDIA = arrayOf(
            MediaStore.Audio.Media._ID, //0
            MediaStore.Audio.Media.DISPLAY_NAME, //1
            MediaStore.Audio.Media.ARTIST, //2
            MediaStore.Audio.Media.ALBUM_ID, //3
            MediaStore.Audio.Media.DURATION, //4
            MediaStore.Audio.Media.DATE_ADDED, //5
            MediaStore.Audio.Media.MIME_TYPE, //6
            MediaStore.Audio.Media.BUCKET_DISPLAY_NAME //7
        );

        fun getDocumentTrack(url: String): IPlatformContentDetails? {
            if(!url.contains("com.android.externalstorage.documents"))
                return null;
            val docFile = DocumentFile.fromSingleUri(StateApp.instance.context, url.toUri()) ?: return null;

            val contentUri = docFile.uri.toString();

            val mimeType = MimeTypeMap.getFileExtensionFromUrl(contentUri);

            if(docFile.name != null) {
                if (StateApp.instance.hasMediaStoreAudioPermission && mimeType.startsWith("audio/")) {
                    val aud = findAudioByName(docFile.name!!);
                    if (aud != null)
                        return aud;
                }
                if (StateApp.instance.hasMediaStoreVideoPermission && mimeType.startsWith("video/")) {
                    val vid = findVideoByName(docFile.name!!);
                    if (vid != null)
                        return vid;
                }
            }

            return LocalVideoDetails(
                PlatformID("FILE", contentUri, null, 0, -1),
                docFile.name ?: docFile.uri.toString(), Thumbnails(arrayOf(
                    Thumbnail(docFile.uri.toString(), 0)
                )), PlatformAuthorLink.UNKNOWN, contentUri, 0, mimeType, null);
        }

        fun getAudioTrack(url: String): IPlatformContentDetails? {
            val uri = Uri.parse(url);
            val id = uri.lastPathSegment?.toLongOrNull();
            if(id == null) {
                return getDocumentTrack(url);
            }

            val resolver =  StateApp.instance.contextOrNull?.contentResolver;
            if(resolver == null) {
                Logger.w(TAG, "Album contentResolver not found");
                return null;
            }
            val cursor = resolver?.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, StateLibrary.PROJECTION_MEDIA, "${MediaStore.Audio.Media._ID} = ?", arrayOf(id.toString()),
                null) ?: return null;
            return cursor.use {
                cursor.moveToFirst();
                if(cursor.isAfterLast)
                    return@use null;
                return@use audioFromCursor(cursor);
            }
        }
        fun findAudioByName(name: String): IPlatformContentDetails? {
            val resolver =  StateApp.instance.contextOrNull?.contentResolver;
            if(resolver == null) {
                Logger.w(TAG, "Audio contentResolver not found");
                return null;
            }
            val cursor = resolver?.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, StateLibrary.PROJECTION_MEDIA, "${MediaStore.Audio.Media.DISPLAY_NAME} = ?", arrayOf(name),
                null) ?: return null;
            return cursor.use {
                cursor.moveToFirst();
                if(cursor.isAfterLast)
                    return null;
                return@use audioFromCursor(cursor);
            }
        }
        fun getVideoTrack(url: String): IPlatformContentDetails? {
            val uri = Uri.parse(url);
            val id = uri.lastPathSegment?.toLongOrNull();
            if(id == null)
                return getDocumentTrack(url);

            val resolver =  StateApp.instance.contextOrNull?.contentResolver;
            if(resolver == null) {
                Logger.w(TAG, "Album contentResolver not found");
                return null;
            }
            val cursor = resolver?.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, StateLibrary.PROJECTION_VIDEO, "${MediaStore.Video.Media._ID} = ?", arrayOf(id.toString()),
                null) ?: return null;
            return cursor.use {
                cursor.moveToFirst();
                if(cursor.isAfterLast)
                    return@use null;
                return@use videoFromCursor(cursor);
            }
        }
        fun findVideoByName(name: String): IPlatformContentDetails? {
            val resolver =  StateApp.instance.contextOrNull?.contentResolver;
            if(resolver == null) {
                Logger.w(TAG, "Album contentResolver not found");
                return null;
            }
            val cursor = resolver?.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, StateLibrary.PROJECTION_VIDEO, "${MediaStore.Video.Media.DISPLAY_NAME} = ?", arrayOf(name),
                null) ?: return null;
            return cursor.use {
                cursor.moveToFirst();
                if(cursor.isAfterLast)
                    return@use null;
                return@use videoFromCursor(cursor);
            }
        }

        fun audioFromCursor(cursor: Cursor): IPlatformVideoDetails {
            val id = cursor.getString(0);
            val displayName = cursor.getString(1);
            val author = cursor.getString(2);
            val albumId = cursor.getLong(3);
            val duration = cursor.getLong(4).let { if(it > 0) it / 1000 else 0 };
            val date = cursor.getLong(5);
            val contentType = cursor.getString(6);
            val category = cursor.getString(7);

            val idLong = id.toLongOrNull();
            val contentUrl = if(idLong != null )
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, idLong).toString();
            else
                "";

            val albumContentUrl = if(albumId > 0)
                ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)?.toString()
            else null;

            val dateObj = if(date > 0)
                OffsetDateTime.ofInstant(Instant.ofEpochSecond(date), ZoneOffset.UTC)
            else null;

            val authorObj = if(!author.isNullOrBlank())
                PlatformAuthorLink(PlatformID.NONE, author, "", null, null)
            else PlatformAuthorLink.UNKNOWN;

            return LocalVideoDetails(
                PlatformID("FILE", contentUrl, null, 0, -1),
                displayName, Thumbnails(arrayOf(
                    Thumbnail(albumContentUrl ?: contentUrl, 0)
                )), authorObj, contentUrl, duration, contentType, dateObj);
        }
        fun videoFromCursor(cursor: Cursor): IPlatformVideoDetails {
            val id = cursor.getString(0);
            val displayName = cursor.getString(1);
            val author = cursor.getString(2);
            val date = cursor.getLong(3);
            val contentType = cursor.getString(4);
            val category = cursor.getString(5);

            val idLong = id.toLongOrNull();
            val contentUrl = if(idLong != null )
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, idLong).toString();
            else
                "";

            val dateObj = if(date > 0)
                OffsetDateTime.ofInstant(Instant.ofEpochSecond(date), ZoneOffset.UTC)
            else null;

            val authorObj = if(!author.isNullOrBlank())
                PlatformAuthorLink(PlatformID.NONE, author, "", null, null)
            else PlatformAuthorLink.UNKNOWN;

            return LocalVideoDetails(
                PlatformID("FILE", contentUrl, null, 0, -1),
                displayName, Thumbnails(arrayOf(
                    Thumbnail(contentUrl, 0)
                )), authorObj, contentUrl, -1, contentType, dateObj);
        }

        private var _instance : StateLibrary? = null;
        val instance : StateLibrary
            get(){
            if(_instance == null)
                _instance = StateLibrary();
            return _instance!!;
        };

        fun finish() {
            _instance?.let {
                _instance = null;
            }
        }
    }
}

class Bucket(val id: Long, val name: String);


enum class ArtistOrdering {
    Alphabethic,
    TrackCount,
    AlbumCount
}
class Artist {
    val id: String;
    val name: String;
    val countTracks: Int;
    val countAlbums: Int;
    val thumbnail: String?;
    val contentUrl: String?;

    constructor(name: String, countTracks: Int = -1, countAlbums: Int = -1, thumbnail: String? = null, id: String? = null, contentUrl: String? = null) {
        this.id = id ?: ID_UNKNOWN;
        this.name = name;
        this.thumbnail = thumbnail;
        this.countTracks = countTracks;
        this.countAlbums = countAlbums;
        this.contentUrl = contentUrl;
    }

    fun getAlbums(): List<Album> {
        return Album.getArtistAlbums(id.toLongOrNull() ?: return listOf());
    }

    fun toPlaylist(tracks: List<IPlatformVideo>? = null): Playlist {
        return Playlist(name, tracks?.map { SerializedPlatformVideo.fromVideo(it) } ?: getAudioTracks().toList().filter { it is IPlatformVideo }.map { SerializedPlatformVideo.fromVideo(it as IPlatformVideo) })
    }

    fun getAudioTracks(): IPager<IPlatformContent> {
        val idLong = id.toLongOrNull() ?: return EmptyPager();
        return AdhocPager({ listOf() }, getTracksPager(idLong));
    }

    companion object {
        val ID_UNKNOWN = "UNKNOWN";
        val PROJECTION: Array<String> = arrayOf(Artists._ID,
            Artists.ARTIST,
            Artists.NUMBER_OF_TRACKS,
            Artists.NUMBER_OF_ALBUMS);

        fun fromCursor(cursor: Cursor): Artist {
            val id = cursor.getString(0);
            val artist = cursor.getString(1);
            val numTracks = cursor.getInt(2);
            val numAlbums = cursor.getInt(3);

            val idLong = id.toLongOrNull();
            val uri = if(idLong != null) ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, idLong) else null;

            return Artist(artist, numTracks, numAlbums, null, id, uri?.toString());
        }

        fun getArtist(id: Long): Artist? {
            val resolver =  StateApp.instance.contextOrNull?.contentResolver;
            if(resolver == null) {
                Logger.w(TAG, "Artist contentResolver not found");
                return null
            }
            val cursor = resolver.query(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                Artist.PROJECTION,
                "${MediaStore.Audio.Artists._ID} = ?",
                arrayOf(id.toString()), null) ?: return null;
            return cursor.use {
                cursor.moveToFirst();
                if(cursor.isAfterLast)
                    return@use null;
                return@use Artist.fromCursor(cursor);
            }
        }
        fun getArtists(ordering: ArtistOrdering = ArtistOrdering.Alphabethic, query: String? = null, args: Array<String>? = null): List<Artist> {
            val ordering = when(ordering) {
                ArtistOrdering.Alphabethic -> Artists.ARTIST + " ASC";
                ArtistOrdering.AlbumCount -> Artists.NUMBER_OF_ALBUMS + " DESC";
                ArtistOrdering.TrackCount -> Artists.NUMBER_OF_TRACKS + " DESC";
                else -> null
            }

            val cursor = StateApp.instance.contextOrNull?.contentResolver?.query(Artists.EXTERNAL_CONTENT_URI, PROJECTION,
                query,
                args,
                ordering) ?: return listOf();
            return cursor.use {
                cursor.moveToFirst();
                val list = mutableListOf<Artist>()
                while(!cursor.isAfterLast) {
                    list.add(fromCursor(cursor));
                    cursor.moveToNext();
                }
                return@use list;
            }
        }

        fun getTracksPager(artistId: Long): List<IPlatformVideo> {
            val resolver =  StateApp.instance.contextOrNull?.contentResolver;
            if(resolver == null) {
                Logger.w(TAG, "Album contentResolver not found");
                return listOf();
            }
            val cursor = resolver?.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, StateLibrary.PROJECTION_MEDIA, "${MediaStore.Audio.Media.ARTIST_ID} = ?", arrayOf(artistId.toString()),
                null) ?: return listOf();
            return cursor.use {
                cursor.moveToFirst();
                val list = mutableListOf<IPlatformVideo>()
                while(!cursor.isAfterLast) {
                    list.add(StateLibrary.audioFromCursor(cursor));
                    cursor.moveToNext();
                }
                return@use list;
            }
        }
    }
}

class Album {
    val id: String;
    val name: String;
    val artist: String?;
    val countTracks: Int;
    val thumbnail: String?;

    constructor(name: String, countTracks: Int = -1, artist: String? = null, id: String? = null, thumbnail: String? = null) {
        this.id = id ?: ID_UNKNOWN;
        this.name = name;
        this.artist = artist;
        this.countTracks = countTracks;
        this.thumbnail = thumbnail;
    }

    fun getTracks(): List<IPlatformVideo> {
        return getAlbumTracks(id.toLongOrNull() ?: return listOf())
    }

    fun toPlaylist(tracks: List<IPlatformVideo>? = null): Playlist {
        return Playlist(name, tracks?.map { SerializedPlatformVideo.fromVideo(it) } ?: getTracks().map { SerializedPlatformVideo.fromVideo(it) })
    }

    companion object {
        val TAG = "StateLibrary";
        val ID_UNKNOWN = "UNKNOWN";
        val PROJECTION = arrayOf(MediaStore.Audio.Albums.ALBUM_ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
            MediaStore.Audio.Albums.ARTIST);

        fun fromCursor(cursor: Cursor): Album {
            val id = cursor.getString(0);
            val album = cursor.getString(1);
            val numTracks = cursor.getInt(2);
            val artist = cursor.getString(3);

            val idLong = id.toLongOrNull();
            val uri = if(idLong != null) ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, idLong) else null;
            return Album(album, numTracks, artist, id, uri?.toString());
        }

        fun getAlbumTracks(albumId: Long): List<IPlatformVideo> {
            val resolver =  StateApp.instance.contextOrNull?.contentResolver;
            if(resolver == null) {
                Logger.w(TAG, "Album contentResolver not found");
                return listOf();
            }
            val cursor = resolver?.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, StateLibrary.PROJECTION_MEDIA, "${MediaStore.Audio.Media.ALBUM_ID} = ?", arrayOf(albumId.toString()),
                null) ?: return listOf();
            return cursor.use {
                cursor.moveToFirst();
                val list = mutableListOf<IPlatformVideo>()
                while(!cursor.isAfterLast) {
                    list.add(StateLibrary.audioFromCursor(cursor));
                    cursor.moveToNext();
                }
                return@use list;
            }
        }
        fun getAlbum(id: Long): Album? {
            val resolver =  StateApp.instance.contextOrNull?.contentResolver;
            if(resolver == null) {
                Logger.w(TAG, "Album contentResolver not found");
                return null
            }
            val cursor = resolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                PROJECTION,
                "${MediaStore.Audio.Albums.ALBUM_ID} = ?",
                arrayOf(id.toString()), null) ?: return null;
            return cursor.use {
                cursor.moveToFirst();
                if(cursor.isAfterLast)
                    return@use null;
                return@use fromCursor(cursor);
            }
        }
        fun getAlbums(query: String? = null, args: Array<String>? = null): List<Album> {
            val resolver =  StateApp.instance.contextOrNull?.contentResolver;
            if(resolver == null) {
                Logger.w(TAG, "Album contentResolver not found");
                return listOf();
            }
            val cursor = resolver?.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, PROJECTION, query, args,
                MediaStore.Audio.Albums.ALBUM + " ASC") ?: return listOf();
            return cursor.use {
                cursor.moveToFirst();
                val list = mutableListOf<Album>()
                while(!cursor.isAfterLast) {
                    list.add(fromCursor(cursor));
                    cursor.moveToNext();
                }
                return@use list;
            }
        }
        fun getArtistAlbums(artistId: Long): List<Album> {
            val resolver =  StateApp.instance.contextOrNull?.contentResolver;
            if(resolver == null) {
                Logger.w(TAG, "Album contentResolver not found");
                return listOf();
            }
            val cursor = resolver?.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, PROJECTION, "${MediaStore.Audio.Media.ARTIST_ID} = ?", arrayOf(artistId.toString()),
                MediaStore.Audio.Albums.ALBUM + " ASC") ?: return listOf();
            return cursor.use {
                cursor.moveToFirst();
                val list = mutableListOf<Album>()
                while(!cursor.isAfterLast) {
                    list.add(fromCursor(cursor));
                    cursor.moveToNext();
                }
                return@use list;
            }
        }
    }
}


class FileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean = false,
    val thumbnail: String? = null,

    var removable: Boolean = false
) {

    fun getSubFiles(): List<FileEntry> {
        if(isDirectory) {
            if(path.startsWith("content://"))
                return DocumentFile.fromTreeUri(StateApp.instance.context, path.toUri())?.listFiles()
                    ?.map { fromFile(it) } ?: return listOf();
            return File(path).listFiles()
                .map { fromFile(it) }
        }
        return listOf();
    }

    companion object {
        fun fromPath(path: String): FileEntry {
            /*
            val cursor = StateApp.instance.context.contentResolver.query(path.toUri(), null, null, null, null);
            cursor?.moveToFirst();
            val fileName = cursor?.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            cursor?.close();
            return FileEntry(path, fileName, );
             */
            val file = File(path);
            return FileEntry(file.path, file.name, file.isDirectory);
        }
        fun fromFile(file: File): FileEntry {
            return FileEntry(file.path, file.name, file.isDirectory);
        }
        fun fromFile(file: DocumentFile): FileEntry {
            return FileEntry(file.uri.toString(), file.name ?: "", file.isDirectory);
        }
    }
}