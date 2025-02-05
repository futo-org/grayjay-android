package com.futo.platformplayer.views.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.fragment.channel.tab.ChannelAboutFragment
import com.futo.platformplayer.fragment.channel.tab.ChannelContentsFragment
import com.futo.platformplayer.fragment.channel.tab.ChannelListFragment
import com.futo.platformplayer.fragment.channel.tab.ChannelMonetizationFragment
import com.futo.platformplayer.fragment.channel.tab.ChannelPlaylistsFragment
import com.futo.platformplayer.fragment.channel.tab.IChannelTabFragment
import com.futo.platformplayer.fragment.mainactivity.main.PolycentricProfile
import com.google.android.material.tabs.TabLayout


enum class ChannelTab {
    VIDEOS, SHORTS, CHANNELS, PLAYLISTS, SUPPORT, ABOUT
}

class ChannelViewPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
    FragmentStateAdapter(fragmentManager, lifecycle) {
    private val _supportedFragments = mutableMapOf(
        ChannelTab.VIDEOS.ordinal to ChannelTab.VIDEOS, ChannelTab.ABOUT.ordinal to ChannelTab.ABOUT
    )
    private val _tabs = arrayListOf(ChannelTab.VIDEOS, ChannelTab.ABOUT)

    var profile: PolycentricProfile? = null
    var channel: IPlatformChannel? = null

    val onContentUrlClicked = Event2<String, ContentType>()
    val onUrlClicked = Event1<String>()
    val onContentClicked = Event2<IPlatformContent, Long>()
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

    fun getTabPosition(tab: ChannelTab): Int {
        return _tabs.indexOf(tab)
    }

    fun getTabNames(tab: TabLayout.Tab, position: Int) {
        tab.text = _tabs[position].name
    }

    fun insert(position: Int, tab: ChannelTab) {
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
            ChannelTab.VIDEOS -> {
                fragment = ChannelContentsFragment.newInstance().apply {
                    onContentClicked.subscribe(this@ChannelViewPagerAdapter.onContentClicked::emit)
                    onContentUrlClicked.subscribe(this@ChannelViewPagerAdapter.onContentUrlClicked::emit)
                    onUrlClicked.subscribe(this@ChannelViewPagerAdapter.onUrlClicked::emit)
                    onChannelClicked.subscribe(this@ChannelViewPagerAdapter.onChannelClicked::emit)
                    onAddToClicked.subscribe(this@ChannelViewPagerAdapter.onAddToClicked::emit)
                    onAddToQueueClicked.subscribe(this@ChannelViewPagerAdapter.onAddToQueueClicked::emit)
                    onAddToWatchLaterClicked.subscribe(this@ChannelViewPagerAdapter.onAddToWatchLaterClicked::emit)
                    onLongPress.subscribe(this@ChannelViewPagerAdapter.onLongPress::emit)
                }
            }

            ChannelTab.SHORTS -> {
                fragment = ChannelContentsFragment.newInstance(ResultCapabilities.TYPE_SHORTS).apply {
                    onContentClicked.subscribe(this@ChannelViewPagerAdapter.onContentClicked::emit)
                    onContentUrlClicked.subscribe(this@ChannelViewPagerAdapter.onContentUrlClicked::emit)
                    onUrlClicked.subscribe(this@ChannelViewPagerAdapter.onUrlClicked::emit)
                    onChannelClicked.subscribe(this@ChannelViewPagerAdapter.onChannelClicked::emit)
                    onAddToClicked.subscribe(this@ChannelViewPagerAdapter.onAddToClicked::emit)
                    onAddToQueueClicked.subscribe(this@ChannelViewPagerAdapter.onAddToQueueClicked::emit)
                    onAddToWatchLaterClicked.subscribe(this@ChannelViewPagerAdapter.onAddToWatchLaterClicked::emit)
                    onLongPress.subscribe(this@ChannelViewPagerAdapter.onLongPress::emit)
                }
            }

            ChannelTab.CHANNELS -> {
                fragment = ChannelListFragment.newInstance()
                    .apply { onClickChannel.subscribe(onChannelClicked::emit) }
            }

            ChannelTab.PLAYLISTS -> {
                fragment = ChannelPlaylistsFragment.newInstance().apply {
                    onContentClicked.subscribe(this@ChannelViewPagerAdapter.onContentClicked::emit)
                    onContentUrlClicked.subscribe(this@ChannelViewPagerAdapter.onContentUrlClicked::emit)
                    onUrlClicked.subscribe(this@ChannelViewPagerAdapter.onUrlClicked::emit)
                    onChannelClicked.subscribe(this@ChannelViewPagerAdapter.onChannelClicked::emit)
                    onAddToClicked.subscribe(this@ChannelViewPagerAdapter.onAddToClicked::emit)
                    onAddToQueueClicked.subscribe(this@ChannelViewPagerAdapter.onAddToQueueClicked::emit)
                    onAddToWatchLaterClicked.subscribe(this@ChannelViewPagerAdapter.onAddToWatchLaterClicked::emit)
                    onLongPress.subscribe(this@ChannelViewPagerAdapter.onLongPress::emit)
                }
            }

            ChannelTab.SUPPORT -> {
                fragment = ChannelMonetizationFragment.newInstance()
            }

            ChannelTab.ABOUT -> {
                fragment = ChannelAboutFragment.newInstance()
            }
        }
        channel?.let { (fragment as IChannelTabFragment).setChannel(it) }
        profile?.let { (fragment as IChannelTabFragment).setPolycentricProfile(it) }

        return fragment
    }
}