package com.futo.platformplayer.fragment.mainactivity.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.downloads.VideoDownload
import com.futo.platformplayer.downloads.VideoLocal
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.services.DownloadService
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringStorage
import com.futo.platformplayer.toHumanBytesSize
import com.futo.platformplayer.toHumanDuration
import com.futo.platformplayer.views.AnyInsertedAdapterView
import com.futo.platformplayer.views.AnyInsertedAdapterView.Companion.asAnyWithTop
import com.futo.platformplayer.views.adapters.viewholders.VideoDownloadViewHolder
import com.futo.platformplayer.views.items.ActiveDownloadItem
import com.futo.platformplayer.views.items.PlaylistDownloadItem
import com.futo.platformplayer.views.others.ProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

class DownloadsFragment : MainFragment() {
    private val TAG = "DownloadsFragment";

    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _view: DownloadsView? = null;

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = DownloadsView(this, inflater);
        _view = view;
        return view;
    }

    override fun onResume() {
        super.onResume()
        _view?.reloadUI();

        if(StateDownloads.instance.getDownloading().any { it.state == VideoDownload.State.QUEUED } &&
            !StateDownloads.instance.getDownloading().any { it.state == VideoDownload.State.DOWNLOADING } &&
            Settings.instance.downloads.shouldDownload()) {
            Logger.w(TAG, "Detected queued download, while not downloading, attempt recreating service");
            StateApp.withContext {
                DownloadService.getOrCreateService(it);
            }
        }

        StateDownloads.instance.onDownloadsChanged.subscribe(this) {
            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    Logger.i(TAG, "Reloading UI for downloads");
                    _view?.reloadUI()
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to reload UI for downloads", e)
                }
            }
        };
        StateDownloads.instance.onDownloadedChanged.subscribe(this) {
            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    Logger.i(TAG, "Reloading UI for downloaded");
                    _view?.reloadUI();
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to reload UI for downloaded", e)
                }
            }
        };
    }

    override fun onPause() {
        super.onPause();

        StateDownloads.instance.onDownloadsChanged.remove(this);
        StateDownloads.instance.onDownloadedChanged.remove(this);
    }

    private class DownloadsView : LinearLayout {
        private val TAG = "DownloadsView";
        private val _frag: DownloadsFragment;

        private val _usageUsed: TextView;
        private val _usageAvailable: TextView;
        private val _usageProgress: ProgressBar;

        private val _listActiveDownloadsContainer: LinearLayout;
        private val _listActiveDownloadsMeta: TextView;
        private val _listActiveDownloads: LinearLayout;

        private val _listPlaylistsContainer: LinearLayout;
        private val _listPlaylistsMeta: TextView;
        private val _listPlaylists: LinearLayout;

        private val _listDownloadedHeader: LinearLayout;
        private val _listDownloadedMeta: TextView;
        private val _listDownloadSearch: EditText;
        private val _listDownloaded: AnyInsertedAdapterView<VideoLocal, VideoDownloadViewHolder>;

        private var lastDownloads: List<VideoLocal>? = null;
        private var ordering = FragmentedStorage.get<StringStorage>("downloads_ordering")

        constructor(frag: DownloadsFragment, inflater: LayoutInflater): super(frag.requireContext()) {
            inflater.inflate(R.layout.fragment_downloads, this);
            _frag = frag;

            if(ordering.value.isNullOrBlank())
                ordering.value = "nameAsc";

            _usageUsed = findViewById(R.id.downloads_usage_used);
            _usageAvailable = findViewById(R.id.downloads_usage_available);
            _usageProgress = findViewById(R.id.downloads_usage_progress);

            _listActiveDownloadsContainer = findViewById(R.id.downloads_active_downloads_container);
            _listActiveDownloadsMeta = findViewById(R.id.downloads_active_downloads_meta);
            _listDownloadSearch = findViewById(R.id.downloads_search);
            _listActiveDownloads = findViewById(R.id.downloads_active_downloads_list);

            _listPlaylistsContainer = findViewById(R.id.downloads_playlist_container);
            _listPlaylistsMeta = findViewById(R.id.downloads_playlist_meta);
            _listPlaylists = findViewById(R.id.downloads_playlist_list);

            _listDownloadedHeader = findViewById(R.id.downloads_videos_header);
            _listDownloadedMeta = findViewById(R.id.downloads_videos_meta);

            _listDownloadSearch.addTextChangedListener {
                updateContentFilters();
            }
            val spinnerSortBy: Spinner = findViewById(R.id.spinner_sortby);
            spinnerSortBy.adapter = ArrayAdapter(context, R.layout.spinner_item_simple, resources.getStringArray(R.array.downloads_sortby_array)).also {
                it.setDropDownViewResource(R.layout.spinner_dropdownitem_simple);
            };
            val options = listOf("nameAsc", "nameDesc", "downloadDateAsc", "downloadDateDesc", "releasedAsc", "releasedDesc", "sizeAsc", "sizeDesc", "typeAudio", "typeVideo");
            spinnerSortBy.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                    when(pos) {
                        0 -> ordering.setAndSave("nameAsc")
                        1 -> ordering.setAndSave("nameDesc")
                        2 -> ordering.setAndSave("downloadDateAsc")
                        3 -> ordering.setAndSave("downloadDateDesc")
                        4 -> ordering.setAndSave("releasedAsc")
                        5 -> ordering.setAndSave("releasedDesc")
                        6 -> ordering.setAndSave("sizeAsc")
                        7 -> ordering.setAndSave("sizeDesc")
                        8 -> ordering.setAndSave("typeAudio")
                        9 -> ordering.setAndSave("typeVideo")
                        else -> ordering.setAndSave("")
                    }
                    updateContentFilters()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            };
            spinnerSortBy.setSelection(Math.max(0, options.indexOf(ordering.value)));

            _listDownloaded = findViewById<RecyclerView>(R.id.list_downloaded)
                .asAnyWithTop(findViewById(R.id.downloads_top)) {
                    it.onClick.subscribe {
                        StatePlayer.instance.clearQueue();
                        _frag.navigate<VideoDetailFragment>(it).maximizeVideoDetail();
                    }
                };


            reloadUI();
        }

        fun reloadUI() {
            val usage = StateDownloads.instance.getTotalUsage(true);
            _usageUsed.text = "${usage.usage.toHumanBytesSize()} " + context.getString(R.string.used);
            _usageAvailable.text = "${usage.available.toHumanBytesSize()} " + context.getString(R.string.available);
            _usageProgress.progress = usage.percentage.toFloat();


            val activeDownloads = StateDownloads.instance.getDownloading();
            val playlists = StateDownloads.instance.getCachedPlaylists();
            val watchLaterDownload = StateDownloads.instance.getWatchLaterDescriptor();
            val downloaded = StateDownloads.instance.getDownloadedVideos()
                .filter { it.groupType != VideoDownload.GROUP_PLAYLIST || it.groupID == null || !StateDownloads.instance.hasCachedPlaylist(it.groupID!!) };

            if(activeDownloads.isEmpty())
                _listActiveDownloadsContainer.visibility = GONE;
            else {
                _listActiveDownloadsContainer.visibility = VISIBLE;
                _listActiveDownloadsMeta.text = "(${activeDownloads.size} videos)";

                _listActiveDownloads.removeAllViews();
                for(view in activeDownloads.take(4).map { ActiveDownloadItem(context, it, _frag.lifecycleScope) })
                    _listActiveDownloads.addView(view);
            }

            if(playlists.isEmpty() && watchLaterDownload == null)
                _listPlaylistsContainer.visibility = GONE;
            else {
                _listPlaylistsContainer.visibility = VISIBLE;

                val watchLater = if(watchLaterDownload != null) StatePlaylists.instance.getWatchLater() else listOf();

                _listPlaylistsMeta.text = "(${playlists.size + (if(watchLaterDownload != null) 1 else 0)} ${context.getString(R.string.playlists).lowercase()}, ${playlists.sumOf { it.playlist.videos.size } + watchLater.size} ${context.getString(R.string.videos).lowercase()})";

                _listPlaylists.removeAllViews();
                if(watchLaterDownload != null) {
                    val pdView = PlaylistDownloadItem(context, "Watch Later", watchLater.firstOrNull()?.thumbnails?.getHQThumbnail(), "WATCHLATER");
                    pdView.setOnClickListener {
                        _frag.navigate<WatchLaterFragment>();
                    }
                    _listPlaylists.addView(pdView);
                }
                for(view in playlists.map { PlaylistDownloadItem(context, it.playlist.name, it.playlist.videos.firstOrNull()?.thumbnails?.getHQThumbnail(), it.playlist) }) {
                    view.setOnClickListener {
                        if(view.obj is Playlist) {
                            _frag.navigate<PlaylistFragment>(view.obj);
                        }
                    };
                    _listPlaylists.addView(view);
                }
            }

            if(downloaded.isEmpty()) {
                _listDownloadedHeader.visibility = GONE;
            } else {
                _listDownloadedHeader.visibility = VISIBLE;
                _listDownloadedMeta.text = "(${downloaded.size} ${context.getString(R.string.videos).lowercase()}${if(downloaded.size > 0) ", ${downloaded.sumOf { it.duration }.toHumanDuration(false)}" else ""})";
            }

            lastDownloads = downloaded;
            _listDownloaded.setData(filterDownloads(downloaded));
        }
        fun updateContentFilters(){
            val toFilter = lastDownloads ?: return;
            _listDownloaded.setData(filterDownloads(toFilter));
        }
        fun filterDownloads(vids: List<VideoLocal>): List<VideoLocal>{
            var vidsToReturn = vids;
            if(!_listDownloadSearch.text.isNullOrEmpty())
                vidsToReturn = vids.filter { it.name.contains(_listDownloadSearch.text, true) || it.author.name.contains(_listDownloadSearch.text, true) };
            if(!ordering.value.isNullOrEmpty()) {
                vidsToReturn = when(ordering.value){
                    "downloadDateAsc" -> vidsToReturn.sortedBy { it.downloadDate ?: OffsetDateTime.MAX };
                    "downloadDateDesc" -> vidsToReturn.sortedByDescending { it.downloadDate ?: OffsetDateTime.MIN };
                    "nameAsc" -> vidsToReturn.sortedBy { it.name.lowercase() }
                    "nameDesc" -> vidsToReturn.sortedByDescending { it.name.lowercase() }
                    "releasedAsc" -> vidsToReturn.sortedBy { it.datetime ?: OffsetDateTime.MAX }
                    "releasedDesc" -> vidsToReturn.sortedByDescending { it.datetime ?: OffsetDateTime.MIN }
                    "sizeAsc" -> vidsToReturn.sortedBy { it.videoSource.sumOf { it.fileSize } + it.audioSource.sumOf { it.fileSize } }
                    "sizeDesc" -> vidsToReturn.sortedByDescending { it.videoSource.sumOf { it.fileSize } + it.audioSource.sumOf { it.fileSize } }
                    "typeAudio" -> vidsToReturn.sortedBy { if (it.videoSource.isEmpty() && it.audioSource.isNotEmpty()) 0 else 1 }
                    "typeVideo" -> vidsToReturn.sortedBy { if (it.videoSource.isNotEmpty()) 0 else 1 }
                    else -> vidsToReturn
                }
            }
            return vidsToReturn;
        }
    }
}