package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.futo.platformplayer.R
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.buttons.BigButton


class LibraryFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var view: FragView? = null;


    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        val newView = FragView(this);
        view = newView;
        return newView;
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        view?.onShown();
    }

    override fun onDestroyMainView() {
        view = null;
        super.onDestroyMainView();
    }

    companion object {
        fun newInstance() = LibraryFragment().apply {}
    }


    class FragView: ConstraintLayout {
        val fragment: LibraryFragment;
        constructor(fragment: LibraryFragment, attrs : AttributeSet? = null) : super(fragment.requireContext(), attrs) {
            inflate(context, R.layout.fragview_library, this);
            this.fragment = fragment;
            val buttonArtists = findViewById<BigButton>(R.id.button_artists);
            val buttonAlbums = findViewById<BigButton>(R.id.button_albums);
            val buttonVideos = findViewById<BigButton>(R.id.button_videos);
            val buttonPlaylists = findViewById<BigButton>(R.id.button_playlists);
            val buttonFiles = findViewById<BigButton>(R.id.button_files);

            buttonArtists.onClick.subscribe {
                fragment.navigate<LibraryArtistsFragment>();
            }
            buttonAlbums.onClick.subscribe {
                fragment.navigate<LibraryAlbumsFragment>();
            }
            buttonVideos.onClick.subscribe {
                fragment.navigate<LibraryVideosFragment>();
            }
            buttonPlaylists.onClick.subscribe {
                fragment.navigate<PlaylistsFragment>();
            }
            buttonFiles.onClick.subscribe {

            }
        }

        fun onShown() {
        }
    }
}