package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.structures.AdhocPager
import com.futo.platformplayer.api.media.structures.EmptyPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.dp
import com.futo.platformplayer.fragment.mainactivity.topbar.NavigationTopBarFragment
import com.futo.platformplayer.states.Album
import com.futo.platformplayer.states.StateLibrary
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.toHumanDuration
import com.futo.platformplayer.views.AlbumHeaderView
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.platformplayer.views.adapters.viewholders.TrackViewHolder


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


    class FragView : FeedView<LibraryAlbumFragment, IPlatformVideo, IPlatformVideo, IPager<IPlatformVideo>, TrackViewHolder> {
        override val feedStyle: FeedStyle = FeedStyle.THUMBNAIL; //R.layout.list_creator;

        private val _header: AlbumHeaderView;

        private var _album: Album? = null;
        private var _tracks: List<IPlatformVideo>? = null;
        private var _url: String? = null;

        constructor(fragment: LibraryAlbumFragment, inflater: LayoutInflater) : super(fragment, inflater) {
            _header = AlbumHeaderView(context);
            _toolbarContentView.addView(_header);

            _header.onPlayAll.subscribe {
                val playlist = _album?.toPlaylist(_tracks);
                if (playlist != null) {
                    StatePlayer.instance.setPlaylist(playlist, focus = true);
                }
            }
            _header.onShuffle.subscribe {
                val playlist = _album?.toPlaylist(_tracks);
                if (playlist != null) {
                    StatePlayer.instance.setPlaylist(playlist, focus = true, shuffle = true);
                }
            }

            /*
            _feedRoot.updateLayoutParams<LinearLayout.LayoutParams> {
                this.setMargins(0,-50.dp(resources),0,0)
            } */
        }

        fun onShown(parameter: Any?) {
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
                setPager(EmptyPager());
                return;
            }
            _header.setName(album.name);
            _header.setThumbnail(album.thumbnail);
            val tracks = album.getTracks();
            _album = album;
            _tracks = tracks;
            _header.setMetadata("${tracks.size} tracks" +  if(tracks.size > 0) (" • " + tracks.sumOf { it.duration }.toHumanDuration(false)) else "");
            setPager(AdhocPager({listOf()}, tracks));
        }

        override fun createAdapter(recyclerResults: RecyclerView, context: Context, dataset: ArrayList<IPlatformVideo>): InsertedViewAdapterWithLoader<TrackViewHolder> {
            return InsertedViewAdapterWithLoader(context, arrayListOf(), arrayListOf(),
                childCountGetter = { dataset.size },
                childViewHolderBinder = { viewHolder, position -> viewHolder.bind(dataset[position]); },
                childViewHolderFactory = { viewGroup, _ ->
                    val holder = TrackViewHolder(viewGroup);
                    holder.onClick.subscribe { c ->

                        val playlist = _album?.toPlaylist(_tracks);
                        if (playlist != null) {
                            val index = playlist.videos.indexOf(c);
                            if (index == -1)
                                return@subscribe;

                            StatePlayer.instance.setPlaylist(playlist, index, true);
                        }
                    };
                    holder.onOptions.subscribe {
                        if(it is IPlatformVideo)
                            UISlideOverlays.showVideoOptionsOverlay(it, _overlayContainer);
                    }
                    return@InsertedViewAdapterWithLoader holder;
                }
            );
        }

        override fun updateSpanCount(){ }

        override fun createLayoutManager(recyclerResults: RecyclerView, context: Context): GridLayoutManager {
            val glmResults = GridLayoutManager(context, 1)

            _swipeRefresh.layoutParams = (_swipeRefresh.layoutParams as MarginLayoutParams?)?.apply {
                rightMargin = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    8.0f,
                    context.resources.displayMetrics
                ).toInt()
            }
            return glmResults
        }

        companion object {
            private const val TAG = "LibraryArtistsFragmentsView";
        }
    }
}