package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.dp
import com.futo.platformplayer.states.Album
import com.futo.platformplayer.states.Artist
import com.futo.platformplayer.states.ArtistOrdering
import com.futo.platformplayer.states.FileEntry
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateLibrary
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.AnyInsertedAdapterView
import com.futo.platformplayer.views.AnyInsertedAdapterView.Companion.asAnyWithTop
import com.futo.platformplayer.views.AnyInsertedAdapterView.Companion.asAnyWithViews
import com.futo.platformplayer.views.LibrarySection
import com.futo.platformplayer.views.NoResultsView
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.adapters.InsertedViewAdapter
import com.futo.platformplayer.views.adapters.viewholders.AlbumTileViewHolder
import com.futo.platformplayer.views.adapters.viewholders.ArtistTileViewHolder
import com.futo.platformplayer.views.adapters.viewholders.FileViewHolder
import com.futo.platformplayer.views.adapters.viewholders.LocalVideoTileViewHolder
import com.futo.platformplayer.views.buttons.BigButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Dispatcher


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
                        StateApp?.instance?.activity?.requestPermissionAudio {
                            setPermissionResultAudio(it);
                        }
                    }, UIDialogs.ActionStyle.PRIMARY),
                    UIDialogs.Action("Cancel", {

                    }, UIDialogs.ActionStyle.NONE));
            }
            else -> {
                StateApp?.instance?.activity?.requestPermissionAudio {
                    setPermissionResultAudio(it);
                }
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
                        StateApp?.instance?.activity?.requestPermissionVideo {
                            setPermissionResultVideo(it);
                        }
                    }, UIDialogs.ActionStyle.PRIMARY),
                    UIDialogs.Action("Cancel", {

                    }, UIDialogs.ActionStyle.NONE));
            }
            else -> {
                StateApp?.instance?.activity?.requestPermissionVideo {
                    setPermissionResultVideo(it);
                }
            }
        }
    }


    companion object {
        fun newInstance() = LibraryFragment().apply {}
    }


    class FragView: ConstraintLayout {
        val fragment: LibraryFragment;

        var sectionArtists: LibrarySection;
        var sectionAlbums: LibrarySection;
        var sectionVideos: LibrarySection;
        var sectionFiles: LibrarySection;
        var noContent: NoResultsView;
        //var buttonFiles: BigButton;

        val recycler: RecyclerView;

        var adapterFiles: AnyInsertedAdapterView<FileEntry, FileViewHolder>? = null;

        //var metaInfo: TextView;

        var allowMusic: Boolean = false;
        var allowVideo: Boolean = false;

        constructor(fragment: LibraryFragment, allowMusic: Boolean?, allowVideo: Boolean?) : super(fragment.requireContext()) {
            inflate(context, R.layout.fragview_library, this);
            this.fragment = fragment;
            recycler = findViewById(R.id.recycler);
            sectionArtists = LibrarySection(context)//findViewById<LibrarySection>(R.id.section_artists);
            sectionArtists.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 140.dp(resources)).apply {
                this.setMargins(0,10.dp(resources), 0, 0);
            }
            sectionAlbums = LibrarySection(context)//findViewById(R.id.section_albums);
            sectionAlbums.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 185.dp(resources)).apply {
                this.setMargins(0,0, 0, 0);
            }
            sectionVideos = LibrarySection(context)//findViewById(R.id.section_videos);
            sectionVideos.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 170.dp(resources)).apply {
                this.setMargins(0,0, 0, 0);
            }
            sectionFiles = LibrarySection(context)//findViewById(R.id.section_videos);
            sectionFiles.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 40.dp(resources)).apply {
                this.setMargins(0,0, 0, 0);
            }
            sectionFiles.setSection("Directories") {
                StateLibrary.instance.addFileDirectory({
                    reloadFiles();
                }, true)
            }
            sectionFiles.setNavIcon(R.drawable.ic_add);
            //buttonFiles = findViewById<BigButton>(R.id.button_files);
            //metaInfo = findViewById(R.id.meta_info);

            noContent = NoResultsView(context, "No directories", "No directories have been added.\nAdd them using the (+) icon.", -1, listOf());
            noContent.isVisible = false;

            this.allowMusic = allowMusic ?: false;
            this.allowVideo = allowVideo ?: false;

            sectionArtists.setSection("Artists", {
                if(this.allowMusic)
                    fragment.navigate<LibraryArtistsFragment>();
                else
                    fragment.requestPermissionMusic();
            });

            sectionAlbums.setSection("Albums", {
                if(this.allowMusic)
                    fragment.navigate<LibraryAlbumsFragment>();
                else
                    fragment.requestPermissionMusic();
            });


            sectionVideos.setSection("Videos", {
                if(this.allowVideo)
                    fragment.navigate<LibraryVideosFragment>();
                else
                    fragment.requestPermissionVideo();
            });

            reloadLibraryUI();


            /*
            buttonFiles.onClick.subscribe {
                fragment.navigate<LibraryFilesFragment>()
            } */
            //buttonFiles.setButtonEnabled(false);
            setMusicPermissions(allowMusic ?: false);
            setVideoPermissions(allowVideo ?: false);
        }

        fun reloadFiles() {
            val files = StateLibrary.instance.getFileDirectories();
            adapterFiles?.setData(files);
            if(files.size == 0) {
                noContent.isVisible = true;
            }
            else
                noContent.isVisible = false;
        }

        fun reloadLibraryUI() {

            val adapterAlbums = sectionAlbums.getAnyAdapter<Album, AlbumTileViewHolder>({
                it.onClick.subscribe {
                    if(it != null)
                        fragment.navigate<LibraryAlbumFragment>(it);
                }
            });
            val adapterArtists = sectionArtists.getAnyAdapter<Artist, ArtistTileViewHolder>({
                it.onClick.subscribe {
                    if(it != null)
                        fragment.navigate<LibraryArtistFragment>(it);
                }
            });
            val adapterVideos = sectionVideos.getAnyAdapter<IPlatformVideo, LocalVideoTileViewHolder>({
                it.onClick.subscribe {
                    if(it != null)
                        fragment.navigate<VideoDetailFragment>(it);
                }
            });

            if(this.allowMusic) {
                val artists = StateLibrary.instance.getArtists(ArtistOrdering.TrackCount);
                adapterArtists.setData(artists);
                if (artists.size == 0)
                    sectionArtists.setEmpty(
                        "No artists",
                        "No artists were found on your device",
                        -1
                    );
            }
            else if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                sectionAlbums.isVisible = false;
            }
            else {
                sectionArtists.setEmpty(
                    "No Music Permissions",
                    "You have not granted music access permissions to Grayjay",
                    -1
                );
            }

            if(this.allowMusic) {
                val albums = StateLibrary.instance.getAlbums();
                adapterAlbums.setData(albums);
                if (albums.size == 0)
                    sectionAlbums.setEmpty("No albums", "No albums were found on your device", -1);
            }
            else if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                sectionArtists.isVisible = false;
            }
            else {
                sectionAlbums.setEmpty(
                    "No Music Permissions",
                    "You have not granted music access permissions to Grayjay",
                    -1
                );
            }

            if(this.allowVideo) {
                val videos = StateLibrary.instance.getRecentVideos(null, 20);
                adapterVideos.setData(videos);
                if (videos.size == 0)
                    sectionVideos.setEmpty("No videos", "No videos were found on your device", -1);
            }
            else if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                sectionVideos.isVisible = false;
            }
            else {
                sectionVideos.setEmpty(
                    "No Video Permissions",
                    "You have not granted video access permissions to Grayjay",
                    -1
                );
            }

            adapterFiles = recycler.asAnyWithViews<FileEntry, FileViewHolder>(
                arrayListOf(
                    sectionArtists,
                    sectionAlbums,
                    sectionVideos,
                    sectionFiles,
                    noContent
                ),
                arrayListOf(View(context).apply { this.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 20.dp(resources)) }),
                RecyclerView.VERTICAL, false, {
                    it.onClick.subscribe {
                        if(it != null)
                            fragment.navigate<LibraryFilesFragment>(it);
                    }
                    it.onDelete.subscribe {
                        if(it != null) {
                            StateLibrary.instance.deleteFileDirectory(it.path);
                            reloadFiles();
                        }
                    }
                }
            );
            reloadFiles();
        }

        fun setMusicPermissions(access: Boolean) {
            allowMusic = access;
            sectionAlbums.setContentEmptyMessage(R.drawable.ic_library, "No mediastore permissions");
            sectionArtists.setContentEmptyMessage(R.drawable.ic_library, "No mediastore permissions");
            //buttonArtists.setButtonEnabled(access);
            //metaInfo.text = listOf(
            //    if(!allowMusic) "You did not give access to local music, so these options are disabled" else null,
            //    if(!allowVideo) "You did not give access to local videos, so these options are disabled" else null
            //).filterNotNull().joinToString("\n");

            fragment.lifecycleScope.launch(Dispatchers.Main) {
                reloadLibraryUI();
            }
        }
        fun setVideoPermissions(access: Boolean) {
            allowVideo = access;
            sectionVideos.setContentEmptyMessage(R.drawable.ic_library, "No video permissions");
            //metaInfo.text = listOf(
            //    if(!allowMusic) "You did not give access to local music, so these options are disabled" else null,
            //    if(!allowVideo) "You did not give access to local videos, so these options are disabled" else null
            //).filterNotNull().joinToString("\n");
            // }

            fragment.lifecycleScope.launch(Dispatchers.Main) {
                reloadLibraryUI();
            }
        }

        fun onShown() {
            UIDialogs.appToast("Library is in alpha\nImprovements are coming to local media playback.")
        }
    }
}