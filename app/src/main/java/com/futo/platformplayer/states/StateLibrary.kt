package com.futo.platformplayer.states

import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Artists
import android.provider.MediaStore.Images.ImageColumns
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
import com.futo.platformplayer.states.Album.Companion
import com.futo.platformplayer.states.Album.Companion.TAG
import com.futo.platformplayer.states.StateLibrary.Companion.getAudioTrack
import com.futo.platformplayer.states.StateLibrary.Companion.videoFromCursor
import com.google.protobuf.Empty
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset


class StateLibrary {



    fun getAlbums(): List<Album> {
        return Album.getAlbums();
    }
    fun getAlbum(str: String): Album? {
        val idLong = str.toLongOrNull();
        if(idLong != null)
            return getAlbum(idLong);
        return null;
    }
    fun getAlbum(id: Long): Album? {
        return Album.getAlbum(id);
    }

    fun getArtists(): List<Artist> {
        return Artist.getArtists();
    }
    fun getArtist(str: String): Artist? {
        val idLong = str.toLongOrNull();
        if(idLong != null)
            return getArtist(idLong);
        return null;
    }
    fun getArtist(id: Long): Artist? {
        return Artist.getArtist(id);
    }

    fun getVideos(buckets: List<String>? = null): IPager<IPlatformContent> {
        val cursor = StateApp.instance.contextOrNull?.contentResolver?.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, PROJECTION_VIDEO,
            if(buckets != null) "${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} IN " + "(" + buckets.map { "'${it}'" }.joinToString(",") + ")" else null,
            null,
            MediaStore.Video.Media.DATE_ADDED + " DESC") ?: return EmptyPager();
        cursor.moveToFirst();
        val list = mutableListOf<IPlatformVideo>()
        while(!cursor.isAfterLast && list.size < 10) {
            list.add(videoFromCursor(cursor));
            cursor.moveToNext();
        }

        return AdhocPager<IPlatformContent>({
            val list = mutableListOf<IPlatformContent>()
            while(!cursor.isAfterLast && list.size < 10) {
                list.add(videoFromCursor(cursor));
                cursor.moveToNext();
            }
            return@AdhocPager list;
        }, list);
    }

    private var _cacheBucketNames: List<Bucket>? = null;
    fun getVideoBucketNames(): List<Bucket> {
        if(_cacheBucketNames != null)
            return _cacheBucketNames ?: listOf();
        val cur: Cursor = StateApp.instance.contextOrNull?.contentResolver?.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            ), null, null, null
        ) ?: return listOf();

        val buckets = mutableListOf<Bucket>();
        val list = HashSet<Long>();
        if (cur.moveToFirst()) {
            var id: Long;
            var bucket: String
            do {
                id = cur.getLong(0);
                bucket = cur.getString(1)
                if(!list.contains(id)) {
                    list.add(id);
                    buckets.add(Bucket(id, bucket));
                }
            } while (cur.moveToNext())
        }
        _cacheBucketNames = buckets.toList()
        return _cacheBucketNames ?: listOf();
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

        fun getAudioTrack(url: String): IPlatformContentDetails? {
            val uri = Uri.parse(url);
            val id = uri.lastPathSegment?.toLongOrNull();
            if(id == null)
                return null;

            val resolver =  StateApp.instance.contextOrNull?.contentResolver;
            if(resolver == null) {
                Logger.w(TAG, "Album contentResolver not found");
                return null;
            }
            val cursor = resolver?.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, StateLibrary.PROJECTION_MEDIA, "${MediaStore.Audio.Media._ID} = ?", arrayOf(id.toString()),
                null) ?: return null;
            cursor.moveToFirst();
            if(cursor.isAfterLast)
                return null;
            return audioFromCursor(cursor);
        }
        fun getVideoTrack(url: String): IPlatformContentDetails? {
            val uri = Uri.parse(url);
            val id = uri.lastPathSegment?.toLongOrNull();
            if(id == null)
                return null;

            val resolver =  StateApp.instance.contextOrNull?.contentResolver;
            if(resolver == null) {
                Logger.w(TAG, "Album contentResolver not found");
                return null;
            }
            val cursor = resolver?.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, StateLibrary.PROJECTION_VIDEO, "${MediaStore.Video.Media._ID} = ?", arrayOf(id.toString()),
                null) ?: return null;
            cursor.moveToFirst();
            if(cursor.isAfterLast)
                return null;
            return videoFromCursor(cursor);
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
                arrayOf(id.toString()), null) ?:
            return null;
            cursor.moveToFirst();
            if(cursor.isAfterLast)
                return null;
            return Artist.fromCursor(cursor);
        }
        fun getArtists(): List<Artist> {
            val cursor = StateApp.instance.contextOrNull?.contentResolver?.query(Artists.EXTERNAL_CONTENT_URI, PROJECTION, null, null,
                Artists.ARTIST + " ASC") ?: return listOf();
            cursor.moveToFirst();
            val list = mutableListOf<Artist>()
            while(!cursor.isAfterLast) {
                list.add(fromCursor(cursor));
                cursor.moveToNext();
            }
            return list;
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
            cursor.moveToFirst();
            val list = mutableListOf<IPlatformVideo>()
            while(!cursor.isAfterLast) {
                list.add(StateLibrary.audioFromCursor(cursor));
                cursor.moveToNext();
            }
            return list;
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
            cursor.moveToFirst();
            val list = mutableListOf<IPlatformVideo>()
            while(!cursor.isAfterLast) {
                list.add(StateLibrary.audioFromCursor(cursor));
                cursor.moveToNext();
            }
            return list;
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
                arrayOf(id.toString()), null) ?:
                return null;
            cursor.moveToFirst();
            if(cursor.isAfterLast)
                return null;
            return fromCursor(cursor);
        }
        fun getAlbums(): List<Album> {
            val resolver =  StateApp.instance.contextOrNull?.contentResolver;
            if(resolver == null) {
                Logger.w(TAG, "Album contentResolver not found");
                return listOf();
            }
            val cursor = resolver?.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, PROJECTION, null, null,
                MediaStore.Audio.Albums.ALBUM + " ASC") ?: return listOf();
            cursor.moveToFirst();
            val list = mutableListOf<Album>()
            while(!cursor.isAfterLast) {
                list.add(fromCursor(cursor));
                cursor.moveToNext();
            }
            return list;
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
            cursor.moveToFirst();
            val list = mutableListOf<Album>()
            while(!cursor.isAfterLast) {
                list.add(fromCursor(cursor));
                cursor.moveToNext();
            }
            return list;
        }
    }
}