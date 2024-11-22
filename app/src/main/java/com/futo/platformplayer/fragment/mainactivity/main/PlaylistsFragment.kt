package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.views.adapters.*
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuOverlay
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class PlaylistsFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _view: PlaylistsView? = null;

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = PlaylistsView(this, inflater);
        _view = view;
        return view;
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        _view?.cleanup();
        _view = null;
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        _view?.onShown();
    }

    override fun onBackPressed(): Boolean {
        if (_view?.onBackPressed() == true) {
            return true;
        }

        return super.onBackPressed()
    }

    @SuppressLint("ViewConstructor")
    class PlaylistsView : LinearLayout {
        private val _fragment: PlaylistsFragment;

        var watchLater: ArrayList<IPlatformVideo> = arrayListOf();
        var playlists: ArrayList<Playlist> = arrayListOf();
        private var _appBar: AppBarLayout;
        private var _adapterWatchLater: VideoListHorizontalAdapter;
        private var _adapterPlaylist: PlaylistsAdapter;
        private var _layoutWatchlist: ConstraintLayout;
        private var _slideUpOverlay: SlideUpMenuOverlay? = null;

        constructor(fragment: PlaylistsFragment, inflater: LayoutInflater) : super(inflater.context) {
            _fragment = fragment;
            inflater.inflate(R.layout.fragment_playlists, this);

            watchLater = ArrayList();
            playlists = ArrayList();

            val recyclerWatchLater = findViewById<RecyclerView>(R.id.recycler_watch_later);

            _adapterWatchLater = VideoListHorizontalAdapter(watchLater);
            recyclerWatchLater.adapter = _adapterWatchLater;
            recyclerWatchLater.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);

            _adapterWatchLater.onClick.subscribe { v ->
                val index = watchLater.indexOf(v);
                if (index == -1) {
                    return@subscribe;
                }

                StatePlayer.instance.setQueueWithPosition(watchLater, StatePlayer.TYPE_WATCHLATER, index, true);
            };

            val recyclerPlaylists = findViewById<RecyclerView>(R.id.recycler_playlists);
            _adapterPlaylist = PlaylistsAdapter(playlists, inflater, context.getString(R.string.confirm_delete_playlist));
            recyclerPlaylists.adapter = _adapterPlaylist;
            recyclerPlaylists.layoutManager = LinearLayoutManager(context);



            val buttonCreatePlaylist = findViewById<ImageButton>(R.id.button_create_playlist);
            buttonCreatePlaylist.setOnClickListener {
                _slideUpOverlay = UISlideOverlays.showCreatePlaylistOverlay(findViewById<FrameLayout>(R.id.overlay_create_playlist)) {
                    val playlist = Playlist(it, arrayListOf());
                    playlists.add(0, playlist);
                    StatePlaylists.instance.createOrUpdatePlaylist(playlist);

                    _adapterPlaylist.notifyItemInserted(0);
                };
            };

            _adapterPlaylist.onClick.subscribe { p -> _fragment.navigate<PlaylistFragment>(p); };
            _adapterPlaylist.onPlay.subscribe { p ->
                StatePlayer.instance.setPlaylist(p, 0, true);
            };

            _appBar = findViewById(R.id.app_bar);
            _layoutWatchlist = findViewById(R.id.layout_watchlist);

            findViewById<TextView>(R.id.text_view_all).setOnClickListener { _fragment.navigate<WatchLaterFragment>(context.getString(R.string.watch_later)); };
            StatePlaylists.instance.onWatchLaterChanged.subscribe(this) {
                fragment.lifecycleScope.launch(Dispatchers.Main) {
                    updateWatchLater();
                }
            };
        }

        fun cleanup() {
            StatePlaylists.instance.onWatchLaterChanged.remove(this);
        }

        @SuppressLint("NotifyDataSetChanged")
        fun onShown() {
            playlists.clear()
            playlists.addAll(
                StatePlaylists.instance.getPlaylists().sortedByDescending { maxOf(it.datePlayed, it.dateUpdate, it.dateCreation) }
            );
            _adapterPlaylist.notifyDataSetChanged();

            updateWatchLater();
        }

        fun onBackPressed(): Boolean {
            val slideUpOverlay = _slideUpOverlay;
            if (slideUpOverlay != null) {
                if (slideUpOverlay.isVisible) {
                    slideUpOverlay.hide();
                    return true;
                }

                return false;
            }

            return false;
        }

        private fun updateWatchLater() {
            val watchList = StatePlaylists.instance.getWatchLater();
            if (watchList.isNotEmpty()) {
                _layoutWatchlist.visibility = View.VISIBLE;

                _appBar.let { appBar ->
                    val layoutParams: CoordinatorLayout.LayoutParams = appBar.layoutParams as CoordinatorLayout.LayoutParams;
                    layoutParams.height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 230.0f, resources.displayMetrics).toInt();
                    appBar.layoutParams = layoutParams;
                }
            } else {
                _layoutWatchlist.visibility = View.GONE;

                _appBar.let { appBar ->
                    val layoutParams: CoordinatorLayout.LayoutParams = appBar.layoutParams as CoordinatorLayout.LayoutParams;
                    layoutParams.height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25.0f, resources.displayMetrics).toInt();
                    appBar.layoutParams = layoutParams;
                };
            }

            watchLater.clear();
            watchLater.addAll(StatePlaylists.instance.getWatchLater());
            _adapterWatchLater.notifyDataSetChanged();
        }
    }

    companion object {
        fun newInstance() = PlaylistsFragment().apply {}
    }
}