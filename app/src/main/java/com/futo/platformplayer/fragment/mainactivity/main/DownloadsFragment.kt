package com.futo.platformplayer.fragment.mainactivity.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.*
import com.futo.platformplayer.downloads.VideoDownload
import com.futo.platformplayer.downloads.VideoLocal
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateDownloads
import com.futo.platformplayer.views.AnyInsertedAdapterView
import com.futo.platformplayer.views.AnyInsertedAdapterView.Companion.asAnyWithTop
import com.futo.platformplayer.views.others.ProgressBar
import com.futo.platformplayer.views.adapters.viewholders.VideoDownloadViewHolder
import com.futo.platformplayer.views.items.ActiveDownloadItem
import com.futo.platformplayer.views.items.PlaylistDownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        StateDownloads.instance.onExportsChanged.subscribe(this) {
            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    Logger.i(TAG, "Reloading UI for exports");
                    _view?.reloadUI()
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to reload UI for exports", e)
                }
            }
        };
    }

    override fun onPause() {
        super.onPause();

        StateDownloads.instance.onDownloadsChanged.remove(this);
        StateDownloads.instance.onDownloadedChanged.remove(this);
        StateDownloads.instance.onExportsChanged.remove(this);
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
        private val _listDownloaded: AnyInsertedAdapterView<VideoLocal, VideoDownloadViewHolder>;

        constructor(frag: DownloadsFragment, inflater: LayoutInflater): super(frag.requireContext()) {
            inflater.inflate(R.layout.fragment_downloads, this);
            _frag = frag;

            _usageUsed = findViewById(R.id.downloads_usage_used);
            _usageAvailable = findViewById(R.id.downloads_usage_available);
            _usageProgress = findViewById(R.id.downloads_usage_progress);

            _listActiveDownloadsContainer = findViewById(R.id.downloads_active_downloads_container);
            _listActiveDownloadsMeta = findViewById(R.id.downloads_active_downloads_meta);
            _listActiveDownloads = findViewById(R.id.downloads_active_downloads_list);

            _listPlaylistsContainer = findViewById(R.id.downloads_playlist_container);
            _listPlaylistsMeta = findViewById(R.id.downloads_playlist_meta);
            _listPlaylists = findViewById(R.id.downloads_playlist_list);

            _listDownloadedHeader = findViewById(R.id.downloads_videos_header);
            _listDownloadedMeta = findViewById(R.id.downloads_videos_meta);

            _listDownloaded = findViewById<RecyclerView>(R.id.list_downloaded)
                .asAnyWithTop(findViewById(R.id.downloads_top)) {
                    it.onClick.subscribe {
                        _frag.navigate<VideoDetailFragment>(it).maximizeVideoDetail();
                    }
                };


            reloadUI();
        }


        fun reloadUI() {
            val usage = StateDownloads.instance.getTotalUsage(true);
            _usageUsed.text = "${usage.usage.toHumanBytesSize()} Used";
            _usageAvailable.text = "${usage.available.toHumanBytesSize()} Available";
            _usageProgress.progress = usage.percentage.toFloat();


            val activeDownloads = StateDownloads.instance.getDownloading();
            val playlists = StateDownloads.instance.getCachedPlaylists();
            val downloaded = StateDownloads.instance.getDownloadedVideos()
                .filter { it.groupType != VideoDownload.GROUP_PLAYLIST || it.groupID == null || !StateDownloads.instance.hasCachedPlaylist(it.groupID!!) };

            if(activeDownloads.isEmpty())
                _listActiveDownloadsContainer.visibility = GONE;
            else {
                _listActiveDownloadsContainer.visibility = VISIBLE;
                _listActiveDownloadsMeta.text = "(${activeDownloads.size})";

                _listActiveDownloads.removeAllViews();
                for(view in activeDownloads.map { ActiveDownloadItem(context, it, _frag.lifecycleScope) })
                    _listActiveDownloads.addView(view);
            }

            if(playlists.isEmpty())
                _listPlaylistsContainer.visibility = GONE;
            else {
                _listPlaylistsContainer.visibility = VISIBLE;
                _listPlaylistsMeta.text = "(${playlists.size} playlists, ${playlists.sumOf { it.playlist.videos.size }} videos)";

                _listPlaylists.removeAllViews();
                for(view in playlists.map { PlaylistDownloadItem(context, it) }) {
                    view.setOnClickListener {
                        _frag.navigate<PlaylistFragment>(view.playlist.playlist);
                    };
                    _listPlaylists.addView(view);
                }
            }

            if(downloaded.isEmpty()) {
                _listDownloadedHeader.visibility = GONE;
            } else {
                _listDownloadedHeader.visibility = VISIBLE;
                _listDownloadedMeta.text = "(${downloaded.size} videos)";
            }

            _listDownloaded.setData(downloaded);
        }
    }
}