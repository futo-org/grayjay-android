package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.exceptions.NoPlatformClientException
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.channels.SerializedChannel
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.post.IPlatformPost
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.assume
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.dp
import com.futo.platformplayer.fragment.channel.tab.IChannelTabFragment
import com.futo.platformplayer.fragment.mainactivity.topbar.NavigationTopBarFragment
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.SearchType
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.selectBestImage
import com.futo.platformplayer.selectHighestResolutionImage
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.views.adapters.ChannelTab
import com.futo.platformplayer.views.adapters.ChannelViewPagerAdapter
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuOverlay
import com.futo.platformplayer.views.subscriptions.SubscribeButton
import com.futo.polycentric.core.OwnedClaim
import com.futo.polycentric.core.PublicKey
import com.futo.polycentric.core.Store
import com.futo.polycentric.core.SystemState
import com.futo.polycentric.core.systemToURLInfoSystemLinkUrl
import com.futo.polycentric.core.toURLInfoSystemLinkUrl
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class PolycentricProfile(
    val system: PublicKey, val systemState: SystemState, val ownedClaims: List<OwnedClaim>
) {
    fun getHarborUrl(context: Context): String{
        val systemState = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(system));
        val url = system.systemToURLInfoSystemLinkUrl(systemState.servers.asIterable());
        return "https://harbor.social/" + url.substring("polycentric://".length);
    }
}

class ChannelFragment : MainFragment() {
    override val isMainView: Boolean = true
    override val hasBottomBar: Boolean = true
    private var _view: ChannelView? = null

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack)
        _view?.onShown(parameter, isBack)
    }

    override fun onCreateMainView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = ChannelView(this, inflater)
        _view = view
        return view
    }

    override fun onBackPressed(): Boolean {
        return _view?.onBackPressed() ?: false
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView()

        _view?.cleanup()
        _view = null
    }

    fun selectTab(tab: ChannelTab) {
        _view?.selectTab(tab)
    }

    @SuppressLint("ViewConstructor")
    class ChannelView
        (fragment: ChannelFragment, inflater: LayoutInflater) : LinearLayout(inflater.context) {
        private val _fragment: ChannelFragment = fragment

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
        var channel: IPlatformChannel? = null
            private set
        private var _url: String? = null

        private val _onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {}

        private val _taskLoadPolycentricProfile: TaskHandler<PlatformID, PolycentricCache.CachedPolycentricProfile?>
        private val _taskGetChannel: TaskHandler<String, IPlatformChannel>

        init {
            inflater.inflate(R.layout.fragment_channel, this)
            _taskLoadPolycentricProfile =
                TaskHandler<PlatformID, PolycentricCache.CachedPolycentricProfile?>({ fragment.lifecycleScope },
                    { id ->
                        return@TaskHandler PolycentricCache.instance.getProfileAsync(id)
                    }).success { setPolycentricProfile(it, animate = true) }.exception<Throwable> {
                    Logger.w(TAG, "Failed to load polycentric profile.", it)
                }
            _taskGetChannel = TaskHandler<String, IPlatformChannel>({ fragment.lifecycleScope },
                { url -> StatePlatform.instance.getChannelLive(url) }).success { showChannel(it); }
                .exception<NoPlatformClientException> {

                    UIDialogs.showDialog(
                        context,
                        R.drawable.ic_sources,
                        context.getString(R.string.no_source_enabled_to_support_this_channel) + "\n(${_url})",
                        null,
                        null,
                        0,
                        UIDialogs.Action("Back", {
                            fragment.close(true)
                        }, UIDialogs.ActionStyle.PRIMARY)
                    )
                }.exception<Throwable> {
                    Logger.e(TAG, "Failed to load channel.", it)
                    UIDialogs.showGeneralRetryErrorDialog(
                        context, it.message ?: "", it, { loadChannel() }, null, fragment
                    )
                }
            val tabs: TabLayout = findViewById(R.id.tabs)
            val viewPager: ViewPager2 = findViewById(R.id.view_pager)
            _textChannel = findViewById(R.id.text_channel_name)
            _textChannelSub = findViewById(R.id.text_metadata)
            _creatorThumbnail = findViewById(R.id.creator_thumbnail)
            _imageBanner = findViewById(R.id.image_channel_banner)
            _buttonSubscribe = findViewById(R.id.button_subscribe)
            _buttonSubscriptionSettings = findViewById(R.id.button_sub_settings)
            _overlayLoading = findViewById(R.id.channel_loading_overlay)
            _overlayLoadingSpinner = findViewById(R.id.channel_loader)
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
                val url = channel?.url ?: _url ?: return@setOnClickListener
                val sub =
                    StateSubscriptions.instance.getSubscription(url) ?: return@setOnClickListener
                UISlideOverlays.showSubscriptionOptionsOverlay(sub, _overlayContainer)
            }

            //TODO: Determine if this is really the only solution (isSaveEnabled=false)
            viewPager.isSaveEnabled = false
            viewPager.registerOnPageChangeCallback(_onPageChangeCallback)
            val adapter = ChannelViewPagerAdapter(fragment.childFragmentManager, fragment.lifecycle)
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
                    StatePlaylists.instance.addToWatchLater(SerializedPlatformVideo.fromVideo(content), true)
                    UIDialogs.toast("Added to watch later\n[${content.name}]")
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
                tabs, viewPager, (viewPager.adapter as ChannelViewPagerAdapter)::getTabNames
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

        fun selectTab(tab: ChannelTab) {
            (_viewPager.adapter as ChannelViewPagerAdapter).getTabPosition(tab)
        }

        fun cleanup() {
            _taskLoadPolycentricProfile.cancel()
            _taskGetChannel.cancel()
            _tabLayoutMediator.detach()
            _viewPager.unregisterOnPageChangeCallback(_onPageChangeCallback)
            hideSlideUpOverlay()
            (_overlayLoadingSpinner.drawable as Animatable?)?.stop()
        }

        fun onShown(parameter: Any?, isBack: Boolean) {
            hideSlideUpOverlay()
            _taskLoadPolycentricProfile.cancel()
            _selectedTabIndex = -1

            if (!isBack || _url == null) {
                _imageBanner.setImageDrawable(null)

                when (parameter) {
                    is String -> {
                        _buttonSubscribe.setSubscribeChannel(parameter)
                        _buttonSubscriptionSettings.visibility =
                            if (_buttonSubscribe.isSubscribed) View.VISIBLE else View.GONE
                        setPolycentricProfileOr(parameter) {
                            _textChannel.text = ""
                            _textChannelSub.text = ""
                            _creatorThumbnail.setThumbnail(null, true)
                            Glide.with(_imageBanner).clear(_imageBanner)
                        }

                        _url = parameter
                        loadChannel()
                    }

                    is SerializedChannel -> {
                        showChannel(parameter)
                        _url = parameter.url
                        loadChannel()
                    }

                    is IPlatformChannel -> showChannel(parameter)
                    is PlatformAuthorLink -> {
                        setPolycentricProfileOr(parameter.url) {
                            _textChannel.text = parameter.name
                            _textChannelSub.text = ""
                            _creatorThumbnail.setThumbnail(parameter.thumbnail, true)
                            Glide.with(_imageBanner).clear(_imageBanner)

                            loadPolycentricProfile(parameter.id, parameter.url)
                        }

                        _url = parameter.url
                        loadChannel()
                    }

                    is Subscription -> {
                        setPolycentricProfileOr(parameter.channel.url) {
                            _textChannel.text = parameter.channel.name
                            _textChannelSub.text = ""
                            _creatorThumbnail.setThumbnail(parameter.channel.thumbnail, true)
                            Glide.with(_imageBanner).clear(_imageBanner)

                            loadPolycentricProfile(parameter.channel.id, parameter.channel.url)
                        }

                        _url = parameter.channel.url
                        loadChannel()
                    }
                }
            } else {
                loadChannel()
            }
        }

        private fun selectTab(selectedTabIndex: Int) {
            _selectedTabIndex = selectedTabIndex
            _tabs.selectTab(_tabs.getTabAt(selectedTabIndex))
        }

        private fun loadPolycentricProfile(id: PlatformID, url: String) {
            val cachedPolycentricProfile = PolycentricCache.instance.getCachedProfile(url, true)
            if (cachedPolycentricProfile != null) {
                setPolycentricProfile(cachedPolycentricProfile, animate = true)
                if (cachedPolycentricProfile.expired) {
                    _taskLoadPolycentricProfile.run(id)
                }
            } else {
                _taskLoadPolycentricProfile.run(id)
            }
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


        private fun loadChannel() {
            val url = _url
            if (url != null) {
                setLoading(true)
                _taskGetChannel.run(url)
            }
        }

        private fun showChannel(channel: IPlatformChannel) {
            setLoading(false)

            _fragment.topBar?.onShown(channel)

            val buttons = arrayListOf(Pair(R.drawable.ic_playlist_add) {
                UIDialogs.showConfirmationDialog(context,
                    context.getString(R.string.do_you_want_to_convert_channel_channelname_to_a_playlist)
                        .replace("{channelName}", channel.name),
                    {
                        UIDialogs.showDialogProgress(context) {
                            _fragment.lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    StatePlaylists.instance.createPlaylistFromChannel(channel) { page ->
                                        _fragment.lifecycleScope.launch(Dispatchers.Main) {
                                            it.setText("${channel.name}\n" + context.getString(R.string.page) + " $page")
                                        }
                                    }
                                } catch (ex: Exception) {
                                    Logger.e(TAG, "Error", ex)
                                    UIDialogs.showGeneralErrorDialog(
                                        context,
                                        context.getString(R.string.failed_to_convert_channel),
                                        ex
                                    )
                                }

                                withContext(Dispatchers.Main) {
                                    it.hide()
                                }
                            }
                        }
                    })
            })

            _fragment.lifecycleScope.launch(Dispatchers.IO) {
                val plugin = StatePlatform.instance.getChannelClientOrNull(channel.url)
                withContext(Dispatchers.Main) {
                    if (plugin != null && plugin.capabilities.hasSearchChannelContents) {
                        buttons.add(Pair(R.drawable.ic_search) {
                            _fragment.navigate<SuggestionsFragment>(
                                SuggestionsFragmentData(
                                    "", SearchType.VIDEO, channel.url
                                )
                            )
                        })

                        _fragment.topBar?.assume<NavigationTopBarFragment>()?.setMenuItems(buttons)
                    }
                    if(plugin != null && plugin.capabilities.hasGetChannelCapabilities) {
                        if(plugin.getChannelCapabilities()?.types?.contains(ResultCapabilities.TYPE_SHORTS) ?: false &&
                            !(_viewPager.adapter as ChannelViewPagerAdapter).containsItem(ChannelTab.SHORTS.ordinal.toLong())) {
                            (_viewPager.adapter as ChannelViewPagerAdapter).insert(1, ChannelTab.SHORTS);
                        }
                    }
                }
            }

            _buttonSubscribe.setSubscribeChannel(channel)
            _buttonSubscriptionSettings.visibility =
                if (_buttonSubscribe.isSubscribed) View.VISIBLE else View.GONE
            _textChannel.text = channel.name
            _textChannelSub.text =
                if (channel.subscribers > 0) "${channel.subscribers.toHumanNumber()} " + context.getString(
                    R.string.subscribers
                ).lowercase() else ""

            var supportsPlaylists = false;
            try {
                supportsPlaylists = StatePlatform.instance.getChannelClient(channel.url).capabilities.hasGetChannelPlaylists
            } catch (ex: Throwable) {
                //Ignore error
                Logger.e(TAG, "Failed to check if supports playlists", ex);
            }
            val playlistPosition = 1
            if (supportsPlaylists && !(_viewPager.adapter as ChannelViewPagerAdapter).containsItem(
                    ChannelTab.PLAYLISTS.ordinal.toLong()
                )
            ) {
                // keep the current tab selected
                if (_viewPager.currentItem >= playlistPosition) {
                    _viewPager.setCurrentItem(_viewPager.currentItem + 1, false)
                }

                (_viewPager.adapter as ChannelViewPagerAdapter).insert(
                    playlistPosition,
                    ChannelTab.PLAYLISTS
                )
            }
            if (!supportsPlaylists && (_viewPager.adapter as ChannelViewPagerAdapter).containsItem(
                    ChannelTab.PLAYLISTS.ordinal.toLong()
                )
            ) {
                // keep the current tab selected
                if (_viewPager.currentItem >= playlistPosition) {
                    _viewPager.setCurrentItem(_viewPager.currentItem - 1, false)
                }

                (_viewPager.adapter as ChannelViewPagerAdapter).remove(playlistPosition)
            }

            // sets the channel for each tab
            for (fragment in _fragment.childFragmentManager.fragments) {
                (fragment as IChannelTabFragment).setChannel(channel)
            }

            (_viewPager.adapter as ChannelViewPagerAdapter).channel = channel


            _viewPager.adapter!!.notifyDataSetChanged()

            this.channel = channel

            setPolycentricProfileOr(channel.url) {
                _textChannel.text = channel.name
                _creatorThumbnail.setThumbnail(channel.thumbnail, true)
                Glide.with(_imageBanner).load(channel.banner).crossfade().into(_imageBanner)

                _taskLoadPolycentricProfile.run(channel.id)
            }
        }

        private fun setPolycentricProfileOr(url: String, or: () -> Unit) {
            setPolycentricProfile(null, animate = false)

            val cachedProfile = channel?.let { PolycentricCache.instance.getCachedProfile(url) }
            if (cachedProfile != null) {
                setPolycentricProfile(cachedProfile, animate = false)
            } else {
                or()
            }
        }

        private fun setPolycentricProfile(
            cachedPolycentricProfile: PolycentricCache.CachedPolycentricProfile?, animate: Boolean
        ) {
            val dp35 = 35.dp(resources)
            val profile = cachedPolycentricProfile?.profile
            val avatar = profile?.systemState?.avatar?.selectBestImage(dp35 * dp35)?.let {
                it.toURLInfoSystemLinkUrl(
                    profile.system.toProto(), it.process, profile.systemState.servers.toList()
                )
            }

            if (avatar != null) {
                _creatorThumbnail.setThumbnail(avatar, animate)
            } else {
                _creatorThumbnail.setThumbnail(channel?.thumbnail, animate)
                _creatorThumbnail.setHarborAvailable(
                    profile != null, animate, profile?.system?.toProto()
                )
            }

            val banner = profile?.systemState?.banner?.selectHighestResolutionImage()?.let {
                it.toURLInfoSystemLinkUrl(
                    profile.system.toProto(), it.process, profile.systemState.servers.toList()
                )
            }

            if (banner != null) {
                Glide.with(_imageBanner).load(banner).crossfade().into(_imageBanner)
            } else {
                Glide.with(_imageBanner).load(channel?.banner).crossfade().into(_imageBanner)
            }

            if (profile != null) {
                _fragment.topBar?.onShown(profile)
                _textChannel.text = profile.systemState.username
            }

            // sets the profile for each tab
            for (fragment in _fragment.childFragmentManager.fragments) {
                (fragment as IChannelTabFragment).setPolycentricProfile(profile)
            }

            val insertPosition = 1

            //TODO only add channels and support if its setup on the polycentric profile
            if (profile != null && !(_viewPager.adapter as ChannelViewPagerAdapter).containsItem(
                    ChannelTab.SUPPORT.ordinal.toLong()
                )
            ) {
                (_viewPager.adapter as ChannelViewPagerAdapter).insert(insertPosition, ChannelTab.SUPPORT)
            }
            if (profile != null && !(_viewPager.adapter as ChannelViewPagerAdapter).containsItem(
                    ChannelTab.CHANNELS.ordinal.toLong()
                )
            ) {
                (_viewPager.adapter as ChannelViewPagerAdapter).insert(insertPosition, ChannelTab.CHANNELS)
            }
            (_viewPager.adapter as ChannelViewPagerAdapter).profile = profile
            _viewPager.adapter!!.notifyDataSetChanged()
        }
    }

    companion object {
        const val TAG = "ChannelFragment"
        fun newInstance() = ChannelFragment().apply { }
    }
}
