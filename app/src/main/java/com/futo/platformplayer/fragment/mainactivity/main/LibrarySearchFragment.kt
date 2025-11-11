package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.GONE
import android.widget.LinearLayout.VISIBLE
import android.widget.Spinner
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.structures.AdhocPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.dp
import com.futo.platformplayer.fragment.mainactivity.topbar.SearchTopBarFragment
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.Album
import com.futo.platformplayer.states.Artist
import com.futo.platformplayer.states.ArtistOrdering
import com.futo.platformplayer.states.FileEntry
import com.futo.platformplayer.states.StateLibrary
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringStorage
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.AnyInsertedAdapterView
import com.futo.platformplayer.views.AnyInsertedAdapterView.Companion.asAnyWithViews
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.LibrarySection
import com.futo.platformplayer.views.LibraryTypeHeaderView
import com.futo.platformplayer.views.LibraryTypeHeaderView.SelectedType
import com.futo.platformplayer.views.PillV2
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.platformplayer.views.adapters.SubscriptionAdapter
import com.futo.platformplayer.views.adapters.viewholders.AlbumTileViewHolder
import com.futo.platformplayer.views.adapters.viewholders.ArtistTileViewHolder
import com.futo.platformplayer.views.adapters.viewholders.FileViewHolder
import com.futo.platformplayer.views.adapters.viewholders.LocalVideoTileViewHolder
import com.futo.platformplayer.views.adapters.viewholders.SelectablePlaylist
import com.futo.platformplayer.views.adapters.viewholders.TrackViewHolder
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.platform.PlatformIndicator

class LibrarySearchFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;


    var view: FragView? = null;

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = FragView(this);
        this.view = view;
        return view;
    }

    override fun onShown(parameter: Any?, isBack: Boolean) {
        super.onShown(parameter, isBack)
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
        fun newInstance() = LibrarySearchFragment().apply {}
    }


    class FragView: ConstraintLayout {
        val fragment: LibrarySearchFragment;

        val pillArtist: PillV2;
        val pillAlbums: PillV2;
        val pillSongs: PillV2;
        val pills: List<PillV2>;

        val textMetadata: TextView;

        val recycler: RecyclerView;

        val adapterArtists: AnyAdapterView<Artist, LibraryArtistsFragment.ArtistViewHolder>;
        val adapterSongs: AnyAdapterView<IPlatformContent, TrackViewHolder>;
        val adapterAlbums: AnyAdapterView<Album, LibraryAlbumsFragment.AlbumViewHolder>;



        constructor(fragment: LibrarySearchFragment) : super(fragment.requireContext()) {
            inflate(context, R.layout.fragview_library_search, this);
            this.fragment = fragment;

            pillArtist = findViewById(R.id.pill_artist);
            pillAlbums = findViewById(R.id.pill_albums);
            pillSongs = findViewById(R.id.pill_songs);
            pills = listOf(pillArtist, pillAlbums, pillSongs);

            textMetadata = findViewById(R.id.text_metadata);

            pillArtist.onClick.subscribe {
                pills.forEach { it.setIsEnabled(false) };
                pillArtist.setIsEnabled(true);
                loadArtists();
            }
            pillAlbums.onClick.subscribe {
                pills.forEach { it.setIsEnabled(false) };
                pillAlbums.setIsEnabled(true);
                loadAlbums();
            }
            pillSongs.onClick.subscribe {
                pills.forEach { it.setIsEnabled(false) };
                pillSongs.setIsEnabled(true);
                loadSongs();
            }

            recycler = findViewById(R.id.recycler);
            adapterArtists = recycler.asAny<Artist, LibraryArtistsFragment.ArtistViewHolder>(RecyclerView.VERTICAL, false, {
                it.onClick.subscribe {
                    if(it != null)
                        fragment.navigate<LibraryArtistFragment>(it);
                }
            });
            adapterAlbums = recycler.asAny<Album, LibraryAlbumsFragment.AlbumViewHolder>(RecyclerView.VERTICAL, false, {
                it.onClick.subscribe {
                    if(it != null)
                        fragment.navigate<LibraryAlbumFragment>(it);
                }
            });
            adapterSongs = recycler.asAny<IPlatformContent, TrackViewHolder>(RecyclerView.VERTICAL, false, {
                it.onClick.subscribe {
                    if(it != null && it is IPlatformVideo)
                        fragment.navigate<VideoDetailFragment>(it);
                }
            });

            fragment.topBar?.let {
                if(it is SearchTopBarFragment) {
                    it.onSearch.subscribe {
                        search(it);
                    }
                }
            }

            pillArtist.setIsEnabled(true);
            loadArtists();
        }

        fun loadArtists(){
            recycler.adapter = adapterArtists.adapter.adapter;
            fragment.topBar?.let {
                if(it is SearchTopBarFragment)
                    search(it.getSearchText());
            }
        }
        fun loadAlbums() {
            recycler.adapter = adapterAlbums.adapter.adapter;
            fragment.topBar?.let {
                if(it is SearchTopBarFragment)
                    search(it.getSearchText());
            }
        }
        fun loadSongs() {
            recycler.adapter = adapterSongs.adapter.adapter;
            fragment.topBar?.let {
                if(it is SearchTopBarFragment)
                    search(it.getSearchText());
            }
        }

        fun search(str: String) {
            if(recycler.adapter == adapterArtists.adapter.adapter) {
                val data = if(!str.isNullOrBlank())
                    StateLibrary.instance.searchArtists(str)
                else listOf();
                adapterArtists.setData(data);
                textMetadata.text = "${data.size} artists";
            }
            else if(recycler.adapter == adapterAlbums.adapter.adapter) {
                val data = if(!str.isNullOrBlank())
                    StateLibrary.instance.searchAlbums(str)
                else listOf();
                adapterAlbums.setData(data);
                textMetadata.text = "${data.size} albums";
            }
            else if(recycler.adapter == adapterSongs.adapter.adapter) {
                val data = if(!str.isNullOrBlank())
                    StateLibrary.instance.searchTracks(str)
                else listOf();

                adapterSongs.setData(data);
                textMetadata.text = "${data.size} songs";
            }
        }


        fun onShown() {
            fragment.topBar?.let {
                if(it is SearchTopBarFragment)
                    it.focus();
            }
        }
    }
}