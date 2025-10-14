package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.futo.platformplayer.R
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.activities.IWithResultLauncher
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.states.Album
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.states.StateLibrary
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.views.buttons.BigButton
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuOverlay
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuTextInput


class LibraryAlbumFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var view: FragView? = null;


    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        val newView = FragView(this, inflater);
        view = newView;
        return newView;
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        view?.onShown(parameter);
    }

    override fun onDestroyMainView() {
        view = null;
        super.onDestroyMainView();
    }

    companion object {
        fun newInstance() = LibraryAlbumFragment().apply {}
    }


    class FragView: VideoListEditorView {
        val fragment: LibraryAlbumFragment;

        private var _album: Album? = null;
        private var _tracks: List<IPlatformVideo>? = null;
        private var _url: String? = null;

        constructor(fragment: LibraryAlbumFragment, inflater: LayoutInflater) : super(inflater) {
            this.fragment = fragment;

        }

        fun onShown(parameter: Any? = null) {

            val album = if(parameter is String)
                StateLibrary.instance.getAlbum(parameter);
            else if(parameter is Long)
                StateLibrary.instance.getAlbum(parameter);
            else if(parameter is Album)
                parameter;
            else null;
            if(album == null) {
                _album = null;
                _tracks = null;
                setVideos(listOf(), false);
                return;
            }
            setName(album.name);
            val tracks = album.getTracks();
            _album = album;
            _tracks = tracks;
            setMetadata(tracks.size, if(tracks.size > 0) tracks.sumOf { it.duration } else -1);
            setVideos(tracks, false, album.thumbnail);
        }

        override fun onPlayAllClick() {
            val playlist = _album?.toPlaylist(_tracks);
            if (playlist != null) {
                StatePlayer.instance.setPlaylist(playlist, focus = true);
            }
        }

        override fun onShuffleClick() {
            val playlist = _album?.toPlaylist(_tracks);
            if (playlist != null) {
                StatePlayer.instance.setPlaylist(playlist, focus = true, shuffle = true);
            }
        }

        override fun onVideoOptions(video: IPlatformVideo) {
            UISlideOverlays.showVideoOptionsOverlay(video, overlayContainer);
        }
        override fun onVideoClicked(video: IPlatformVideo) {
            val playlist = _album?.toPlaylist(_tracks);
            if (playlist != null) {
                val index = playlist.videos.indexOf(video);
                if (index == -1)
                    return;

                StatePlayer.instance.setPlaylist(playlist, index, true);
            }
        }
    }
}