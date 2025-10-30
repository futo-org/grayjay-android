package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.buttons.BigButton


class LibraryFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var view: FragView? = null;

    private var allowedMusic = false;
    private var allowedVideo = false;


    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        val newView = FragView(this, allowedMusic, allowedVideo);
        view = newView;
        return newView;
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        view?.onShown();

        requestPermissionMusic();
        requestPermissionVideo();
    }

    override fun onDestroyMainView() {
        view = null;
        super.onDestroyMainView();
    }

    fun setPermissionResultAudio(access: Boolean) {
        allowedMusic = access;
        view?.setMusicPermissions(access);
        StateApp.instance.hasMediaStoreAudioPermission = (access);
    }
    fun setPermissionResultVideo(access: Boolean) {
        allowedVideo = access;
        view?.setVideoPermissions(access);
        StateApp.instance.hasMediaStoreVideoPermission = (access);
    }

    fun requestPermissionMusic() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                setPermissionResultAudio(true);
            }
            ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), android.Manifest.permission.READ_MEDIA_AUDIO) -> {
                UIDialogs.showDialog(requireContext(), R.drawable.ic_library,
                    "Music permissions", "We require permissions to see your on-device music, denying this will hide the option to see local music.", null, 1,
                    UIDialogs.Action("Ok", {
                        permissionReqAudio.launch(android.Manifest.permission.READ_MEDIA_AUDIO);
                    }, UIDialogs.ActionStyle.PRIMARY),
                    UIDialogs.Action("Cancel", {

                    }, UIDialogs.ActionStyle.NONE));
            }
            else -> {
                permissionReqAudio.launch(android.Manifest.permission.READ_MEDIA_AUDIO);
            }
        }
    }
    fun requestPermissionVideo() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED -> {
                setPermissionResultVideo(true);
            }
            ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), android.Manifest.permission.READ_MEDIA_VIDEO) -> {
                UIDialogs.showDialog(requireContext(), R.drawable.ic_library, false,
                    "Videos permissions", "We require permissions to see your on-device videos, denying this will hide the option to see local videos.", null, 1,
                    UIDialogs.Action("Ok", {
                        permissionReqVideo.launch(android.Manifest.permission.READ_MEDIA_VIDEO);
                    }, UIDialogs.ActionStyle.PRIMARY),
                    UIDialogs.Action("Cancel", {

                    }, UIDialogs.ActionStyle.NONE));
            }
            else -> {
                permissionReqVideo.launch(android.Manifest.permission.READ_MEDIA_VIDEO);
            }
        }
    }

    val permissionReqAudio = registerForActivityResult(ActivityResultContracts.RequestPermission(), { isGranted ->
        setPermissionResultAudio(isGranted);
    });
    val permissionReqVideo = registerForActivityResult(ActivityResultContracts.RequestPermission(), { isGranted ->
        setPermissionResultVideo(isGranted);
    });

    companion object {
        fun newInstance() = LibraryFragment().apply {}
    }


    class FragView: ConstraintLayout {
        val fragment: LibraryFragment;

        var buttonArtists: BigButton;
        var buttonAlbums: BigButton;
        var buttonVideos: BigButton;
        var buttonPlaylists: BigButton;
        var buttonFiles: BigButton;

        var metaInfo: TextView;

        var allowMusic: Boolean = false;
        var allowVideo: Boolean = false;

        constructor(fragment: LibraryFragment, allowMusic: Boolean?, allowVideo: Boolean?) : super(fragment.requireContext()) {
            inflate(context, R.layout.fragview_library, this);
            this.fragment = fragment;
            buttonArtists = findViewById<BigButton>(R.id.button_artists);
            buttonAlbums = findViewById<BigButton>(R.id.button_albums);
            buttonVideos = findViewById<BigButton>(R.id.button_videos);
            buttonPlaylists = findViewById<BigButton>(R.id.button_playlists);
            buttonFiles = findViewById<BigButton>(R.id.button_files);
            metaInfo = findViewById(R.id.meta_info);

            this.allowMusic = allowMusic ?: false;
            this.allowVideo = allowVideo ?: false;

            buttonArtists.onClick.subscribe {
                if(this.allowMusic)
                    fragment.navigate<LibraryArtistsFragment>();
                else
                    fragment.requestPermissionMusic();
            }
            buttonAlbums.onClick.subscribe {
                if(this.allowMusic)
                    fragment.navigate<LibraryAlbumsFragment>();
                else
                    fragment.requestPermissionMusic();
            }
            buttonVideos.onClick.subscribe {
                if(this.allowVideo)
                    fragment.navigate<LibraryVideosFragment>();
                else
                    fragment.requestPermissionVideo();
            }
            buttonPlaylists.onClick.subscribe {
                fragment.navigate<PlaylistsFragment>();
            }
            buttonFiles.onClick.subscribe {
                fragment.navigate<LibraryFilesFragment>()
            }
            //buttonFiles.setButtonEnabled(false);
            setMusicPermissions(allowMusic ?: false);
            setVideoPermissions(allowVideo ?: false);
        }

        fun setMusicPermissions(access: Boolean) {
            allowMusic = access;
            buttonAlbums.setButtonEnabled(access);
            buttonArtists.setButtonEnabled(access);
            metaInfo.text = listOf(
                if(!allowMusic) "You did not give access to local music, so these options are disabled" else null,
                if(!allowVideo) "You did not give access to local videos, so these options are disabled" else null
            ).filterNotNull().joinToString("\n");
        }
        fun setVideoPermissions(access: Boolean) {
            allowVideo = access;
            buttonVideos.setButtonEnabled(access);
            metaInfo.text = listOf(
                if(!allowMusic) "You did not give access to local music, so these options are disabled" else null,
                if(!allowVideo) "You did not give access to local videos, so these options are disabled" else null
            ).filterNotNull().joinToString("\n");
        }

        fun onShown() {
        }
    }
}