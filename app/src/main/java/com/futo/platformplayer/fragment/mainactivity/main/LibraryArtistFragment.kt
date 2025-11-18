package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Context
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.post.IPlatformPost
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.api.media.structures.AdhocPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.assume
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.constructs.Event3
import com.futo.platformplayer.fragment.mainactivity.main.LibraryAlbumsFragment.AlbumViewHolder
import com.futo.platformplayer.fragment.mainactivity.topbar.NavigationTopBarFragment
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.SearchType
import com.futo.platformplayer.states.Album
import com.futo.platformplayer.states.Artist
import com.futo.platformplayer.states.StateLibrary
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.platformplayer.views.adapters.viewholders.AlbumTileViewHolder
import com.futo.platformplayer.views.adapters.viewholders.TrackViewHolder
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuOverlay
import com.futo.platformplayer.views.subscriptions.SubscribeButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryArtistFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _textMeta: TextView? = null;

    var view: FragView? = null;

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = FragView(this, inflater);
        this.view = view;
        return view;
    }

    override fun onShown(parameter: Any?, isBack: Boolean) {
        super.onShown(parameter, isBack)
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        view?.onShown(parameter, isBack);
    }

    override fun onDestroyMainView() {
        view = null;
        super.onDestroyMainView();
    }

    companion object {
        fun newInstance() = LibraryArtistFragment().apply {}
    }

    class FragView(fragment: LibraryArtistFragment, inflater: LayoutInflater) : LinearLayout(inflater.context) {
        private val _fragment: LibraryArtistFragment = fragment

        private var _textChannel: TextView
        private var _textChannelSub: TextView
        private var _creatorThumbnail: CreatorThumbnail
        private var _imageBanner: AppCompatImageView

        private var _tabs: TabLayout
        private var _viewPager: ViewPager2

        //        private var _adapter: ChannelViewPagerAdapter;
        private var _tabLayoutMediator: TabLayoutMediator
        private var _buttonSubscribe: SubscribeButton
        private var _buttonSubscriptionSettings: ImageButton

        private var _overlayContainer: FrameLayout
        private var _overlayLoading: LinearLayout
        private var _overlayLoadingSpinner: ImageView

        private var _slideUpOverlay: SlideUpMenuOverlay? = null

        private var _isLoading: Boolean = false
        private var _selectedTabIndex: Int = -1
        var channel: Artist? = null
            private set
        private var _url: String? = null

        private val _onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {}

        init {
            inflater.inflate(R.layout.fragment_artist, this)

            val tabs: TabLayout = findViewById(R.id.tabs)
            val viewPager: ViewPager2 = findViewById(R.id.view_pager)
            _textChannel = findViewById(R.id.text_channel_name)
            _textChannelSub = findViewById(R.id.text_metadata)
            _creatorThumbnail = findViewById(R.id.creator_thumbnail)
            _imageBanner = findViewById(R.id.image_channel_banner)
            _buttonSubscribe = findViewById(R.id.button_subscribe)
            _buttonSubscriptionSettings = findViewById(R.id.button_sub_settings)
            _overlayLoading = findViewById(R.id.channel_loading_overlay)
            _overlayLoadingSpinner = findViewById(R.id.channel_loader_frag)
            _overlayContainer = findViewById(R.id.overlay_container)
            _buttonSubscribe.onSubscribed.subscribe {
                UISlideOverlays.showSubscriptionOptionsOverlay(it, _overlayContainer)
                _buttonSubscriptionSettings.visibility =
                    if (_buttonSubscribe.isSubscribed) View.VISIBLE else View.GONE
            }
            _buttonSubscribe.onUnSubscribed.subscribe {
                _buttonSubscriptionSettings.visibility =
                    if (_buttonSubscribe.isSubscribed) View.VISIBLE else View.GONE
            }
            _buttonSubscriptionSettings.setOnClickListener {
                val url = channel?.contentUrl ?: _url ?: return@setOnClickListener
                val sub =
                    StateSubscriptions.instance.getSubscription(url) ?: return@setOnClickListener
                UISlideOverlays.showSubscriptionOptionsOverlay(sub, _overlayContainer)
            }

            //TODO: Determine if this is really the only solution (isSaveEnabled=false)
            viewPager.isSaveEnabled = false
            viewPager.registerOnPageChangeCallback(_onPageChangeCallback)
            val adapter = ArtistViewPagerAdapter(fragment, fragment.childFragmentManager, fragment.lifecycle)
            adapter.onChannelClicked.subscribe { c -> fragment.navigate<ChannelFragment>(c) }
            adapter.onContentClicked.subscribe { v, _ ->
                when (v) {
                    is IPlatformVideo -> {
                        StatePlayer.instance.clearQueue()
                        fragment.navigate<VideoDetailFragment>(v).maximizeVideoDetail()
                    }

                    is IPlatformPlaylist -> {
                        fragment.navigate<RemotePlaylistFragment>(v)
                    }

                    is IPlatformPost -> {
                        fragment.navigate<PostDetailFragment>(v)
                    }
                }
            }
            adapter.onShortClicked.subscribe { v, _, pagerPair ->
                when (v) {
                    is IPlatformVideo -> {
                        StatePlayer.instance.clearQueue()
                        fragment.navigate<ShortsFragment>(Triple(v, pagerPair!!.first, pagerPair.second))
                    }
                }
            }
            adapter.onAddToClicked.subscribe { content ->
                _overlayContainer.let {
                    if (content is IPlatformVideo) _slideUpOverlay =
                        UISlideOverlays.showVideoOptionsOverlay(content, it)
                }
            }
            adapter.onAddToQueueClicked.subscribe { content ->
                if (content is IPlatformVideo) {
                    StatePlayer.instance.addToQueue(content)
                }
            }
            adapter.onAddToWatchLaterClicked.subscribe { content ->
                if (content is IPlatformVideo) {
                    if(StatePlaylists.instance.addToWatchLater(SerializedPlatformVideo.fromVideo(content), true))
                        UIDialogs.toast("Added to watch later\n[${content.name}]")
                    else
                        UIDialogs.toast(context.getString(R.string.already_in_watch_later))
                }
            }
            adapter.onUrlClicked.subscribe { url ->
                fragment.navigate<BrowserFragment>(url)
            }
            adapter.onContentUrlClicked.subscribe { url, contentType ->
                when (contentType) {
                    ContentType.MEDIA -> {
                        StatePlayer.instance.clearQueue()
                        fragment.navigate<VideoDetailFragment>(url).maximizeVideoDetail()
                    }

                    ContentType.URL -> fragment.navigate<BrowserFragment>(url)
                    else -> {}
                }
            }
            adapter.onLongPress.subscribe { content ->
                _overlayContainer.let {
                    if (content is IPlatformVideo) _slideUpOverlay =
                        UISlideOverlays.showVideoOptionsOverlay(content, it)
                }
            }
            viewPager.adapter = adapter
            val tabLayoutMediator = TabLayoutMediator(
                tabs, viewPager, (viewPager.adapter as ArtistViewPagerAdapter)::getTabNames
            )
            tabLayoutMediator.attach()

            _tabLayoutMediator = tabLayoutMediator
            _tabs = tabs
            _viewPager = viewPager
            if (_selectedTabIndex != -1) {
                selectTab(_selectedTabIndex)
            }
            setLoading(true)
        }

        fun selectTab(tab: ArtistTab) {
            (_viewPager.adapter as ArtistViewPagerAdapter).getTabPosition(tab)
        }

        fun cleanup() {
            _tabLayoutMediator.detach()
            _viewPager.unregisterOnPageChangeCallback(_onPageChangeCallback)
            hideSlideUpOverlay()
            (_overlayLoadingSpinner.drawable as Animatable?)?.stop()
        }

        fun onShown(parameter: Any?, isBack: Boolean) {
            hideSlideUpOverlay()
            _selectedTabIndex = -1

            if (!isBack || _url == null) {
                _imageBanner.setImageDrawable(null)

                when (parameter) {
                    is String -> {
                        _buttonSubscribe.setSubscribeChannel(parameter)
                        _buttonSubscriptionSettings.visibility =
                            if (_buttonSubscribe.isSubscribed) View.VISIBLE else View.GONE

                        _url = parameter

                        val parsed = Uri.parse(parameter);
                        val idLong = parsed.lastPathSegment?.toLongOrNull();
                        if(idLong != null) {
                            val artist = StateLibrary.instance.getArtist(idLong) ?: return;
                            showArtist(artist);
                        }
                    }

                    is Artist -> {
                        showArtist(parameter)
                        _url = parameter.contentUrl
                    }
                }
            }
        }

        private fun selectTab(selectedTabIndex: Int) {
            _selectedTabIndex = selectedTabIndex
            _tabs.selectTab(_tabs.getTabAt(selectedTabIndex))
        }

        private fun setLoading(isLoading: Boolean) {
            if (_isLoading == isLoading) {
                return
            }

            _isLoading = isLoading
            if (isLoading) {
                _overlayLoading.visibility = View.VISIBLE
                (_overlayLoadingSpinner.drawable as Animatable?)?.start()
            } else {
                (_overlayLoadingSpinner.drawable as Animatable?)?.stop()
                _overlayLoading.visibility = View.GONE
            }
        }

        fun onBackPressed(): Boolean {
            if (_slideUpOverlay != null) {
                hideSlideUpOverlay()
                return true
            }

            return false
        }

        private fun hideSlideUpOverlay() {
            _slideUpOverlay?.hide(false)
            _slideUpOverlay = null
        }

        private fun showArtist(channel: Artist) {
            setLoading(false)

            _fragment.topBar?.onShown(channel)

            val buttons = arrayListOf<Pair<Int, ()->Unit>>();

            _fragment.lifecycleScope.launch(Dispatchers.IO) {
                val plugin = StatePlatform.instance.getChannelClientOrNull(channel.contentUrl ?: return@launch)
                withContext(Dispatchers.Main) {
                    buttons.add(Pair(R.drawable.ic_search) {
                        _fragment.navigate<SuggestionsFragment>(
                            SuggestionsFragmentData(
                                "", SearchType.VIDEO
                            )
                        )
                    })
                    _fragment.topBar?.assume<NavigationTopBarFragment>()?.setMenuItems(buttons)
                }
            }

            _buttonSubscribe.visibility = GONE;
            _buttonSubscriptionSettings.visibility = View.GONE
            _textChannel.text = channel.name
            _textChannelSub.text = "${channel.countTracks} songs, ${channel.countAlbums} albums";

            var supportsPlaylists = false;
            val playlistPosition = 1
            // keep the current tab selected
            if (_viewPager.currentItem >= playlistPosition) {
                _viewPager.setCurrentItem(_viewPager.currentItem + 1, false)
            }
            (_viewPager.adapter as ArtistViewPagerAdapter).insert(
                playlistPosition,
                ArtistTab.ALBUMS
            )

            // sets the channel for each tab
            for (fragment in _fragment.childFragmentManager.fragments) {
                (fragment as IArtistTabFragment).setArtist(channel)
            }

            (_viewPager.adapter as ArtistViewPagerAdapter).artist = channel


            _viewPager.adapter!!.notifyDataSetChanged()

            this.channel = channel
        }


        companion object {
            private const val TAG = "LibraryArtistFragmentsView";
        }
    }
    enum class ArtistTab {
        SONGS, ALBUMS
    }

    class ArtistViewPagerAdapter(private val fragment: LibraryArtistFragment, fragmentManager: FragmentManager, lifecycle: Lifecycle) :
        FragmentStateAdapter(fragmentManager, lifecycle) {
        private val _supportedFragments = mutableMapOf(
            ArtistTab.SONGS.ordinal to ArtistTab.SONGS
        )
        private val _tabs = arrayListOf(ArtistTab.SONGS, ArtistTab.ALBUMS)

        var artist: Artist? = null

        val onContentUrlClicked = Event2<String, ContentType>()
        val onUrlClicked = Event1<String>()
        val onContentClicked = Event2<IPlatformContent, Long>()
        val onShortClicked = Event3<IPlatformContent, Long, Pair<IPager<IPlatformContent>, ArrayList<IPlatformContent>>?>()
        val onChannelClicked = Event1<PlatformAuthorLink>()
        val onAddToClicked = Event1<IPlatformContent>()
        val onAddToQueueClicked = Event1<IPlatformContent>()
        val onAddToWatchLaterClicked = Event1<IPlatformContent>()
        val onLongPress = Event1<IPlatformContent>()

        override fun getItemId(position: Int): Long {
            return _tabs[position].ordinal.toLong()
        }

        override fun containsItem(itemId: Long): Boolean {
            return _supportedFragments.containsKey(itemId.toInt())
        }

        override fun getItemCount(): Int {
            return _supportedFragments.size
        }

        fun getTabPosition(tab: ArtistTab): Int {
            return _tabs.indexOf(tab)
        }

        fun getTabNames(tab: TabLayout.Tab, position: Int) {
            tab.text = _tabs[position].name
        }

        fun insert(position: Int, tab: ArtistTab) {
            _supportedFragments[tab.ordinal] = tab
            _tabs.add(position, tab)
            notifyItemInserted(position)
        }

        fun remove(position: Int) {
            _supportedFragments.remove(_tabs[position].ordinal)
            _tabs.removeAt(position)
            notifyItemRemoved(position)
        }

        override fun createFragment(position: Int): Fragment {
            val fragment: Fragment
            when (_tabs[position]) {
                ArtistTab.SONGS -> {
                    fragment = ChannelContentsFragment(this.fragment).apply {

                    }
                }

                ArtistTab.ALBUMS -> {
                    fragment = ArtistAlbumsFragment(this.fragment).apply {

                    }
                }
            }
            artist?.let { (fragment as IArtistTabFragment).setArtist(it) }

            return fragment
        }
    }

    interface IArtistTabFragment {
        fun setArtist(artist: Artist);
    }

    class ChannelContentsFragment(private val frag: LibraryArtistFragment) : Fragment(), IArtistTabFragment {

        var view: ArtistContentView? = null;

        private var _lastArtist: Artist? = null;

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            view = ArtistContentView(frag, inflater);
            _lastArtist?.let {
                view?.setArtist(it);
            }
            return view;
        }

        override fun onDestroyView() {
            view = null;
            super.onDestroyView()
        }

        override fun setArtist(artist: Artist) {
            view?.setArtist(artist);
            _lastArtist = artist;
        }
    }
    class ArtistContentView : FeedView<LibraryArtistFragment, IPlatformContent, IPlatformVideo, IPager<IPlatformContent>, TrackViewHolder> {
        override val feedStyle: FeedStyle = FeedStyle.THUMBNAIL; //R.layout.list_creator;

        protected var _artist: Artist? = null;

        constructor(fragment: LibraryArtistFragment, inflater: LayoutInflater) : super(fragment, inflater) {

        }

        fun setArtist(artist: Artist) {
            this._artist = artist;
            val tracks = artist.getAudioTracks();
            if(tracks.getResults().isEmpty())
                UIDialogs.appToast("No tracks found");
            setPager(tracks);
        }

        override fun filterResults(results: List<IPlatformContent>): List<IPlatformVideo> {
            return results.filter { it is IPlatformVideo }.map { it as IPlatformVideo };
        }

        override fun createAdapter(recyclerResults: RecyclerView, context: Context, dataset: ArrayList<IPlatformVideo>): InsertedViewAdapterWithLoader<TrackViewHolder> {
            return InsertedViewAdapterWithLoader(context, arrayListOf(), arrayListOf(),
                childCountGetter = { dataset.size },
                childViewHolderBinder = { viewHolder, position -> viewHolder.bind(dataset[position]); },
                childViewHolderFactory = { viewGroup, _ ->
                    val holder = TrackViewHolder(viewGroup);
                    holder.onClick.subscribe { c ->

                        val playlist = _artist?.toPlaylist();
                        if (playlist != null) {
                            val sameVideo = playlist.videos.find { it.name == c.name };
                            val index = sameVideo?.let {
                                playlist.videos.indexOf(sameVideo)
                            } ?: -1;
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

        override fun updateSpanCount(){ }


        companion object {
            private const val TAG = "LibraryAlbumsFragmentsView";
        }
    }
    class ArtistAlbumsFragment(private val frag: LibraryArtistFragment) : Fragment(), IArtistTabFragment {

        var view: ArtistAlbumsView? = null;

        private var _lastArtist: Artist? = null;

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            view = ArtistAlbumsView(frag, inflater);
            _lastArtist?.let {
                view?.setArtist(it);
            }
            return view;
        }

        override fun onDestroyView() {
            view = null;
            super.onDestroyView()
        }

        override fun setArtist(artist: Artist) {
            view?.setArtist(artist);
            _lastArtist = artist;
        }
    }
    class ArtistAlbumsView : FeedView<LibraryArtistFragment, Album, Album, IPager<Album>, AlbumTileViewHolder> {
        override val feedStyle: FeedStyle = FeedStyle.THUMBNAIL; //R.layout.list_creator;

        constructor(fragment: LibraryArtistFragment, inflater: LayoutInflater) : super(fragment, inflater)

        fun onShown() {
        }

        fun setArtist(artist: Artist) {
            val initialAlbums = artist.getAlbums();
            Logger.i(TAG, "Initial album count: " + initialAlbums.size);

            setPager(AdhocPager({ listOf() }, initialAlbums));
        }

        override fun createAdapter(recyclerResults: RecyclerView, context: Context, dataset: ArrayList<Album>): InsertedViewAdapterWithLoader<AlbumTileViewHolder> {
            return InsertedViewAdapterWithLoader(context, arrayListOf(), arrayListOf(),
                childCountGetter = { dataset.size },
                childViewHolderBinder = { viewHolder, position -> viewHolder.bind(dataset[position]); },
                childViewHolderFactory = { viewGroup, _ ->
                    val holder = AlbumTileViewHolder(viewGroup);
                    holder.setAutoSize(resources.displayMetrics.widthPixels / resources.displayMetrics.density)
                    holder.onClick.subscribe { c -> fragment.navigate<LibraryAlbumFragment>(c) };
                    return@InsertedViewAdapterWithLoader holder;
                }
            );
        }

        override fun updateSpanCount(){ }

        override fun createLayoutManager(recyclerResults: RecyclerView, context: Context): GridLayoutManager {
            val glmResults = GridLayoutManager(context, AlbumTileViewHolder.getAutoSizeColumns(resources.displayMetrics.widthPixels / resources.displayMetrics.density))

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
            private const val TAG = "LibraryAlbumsFragmentsView";
        }
    }
}