package com.futo.platformplayer.views.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.fragment.channel.tab.*

class ChannelViewPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) : FragmentStateAdapter(fragmentManager, lifecycle) {
    private val _cache: Array<Fragment?> = arrayOfNulls(4);

    val onContentUrlClicked = Event2<String, ContentType>();
    val onUrlClicked = Event1<String>();
    val onContentClicked = Event2<IPlatformContent, Long>();
    val onChannelClicked = Event1<PlatformAuthorLink>();
    val onAddToClicked = Event1<IPlatformContent>();
    val onAddToQueueClicked = Event1<IPlatformContent>();

    override fun getItemCount(): Int {
        return _cache.size;
    }
    inline fun <reified T:IChannelTabFragment> getFragment(): T {

        //TODO: I have a feeling this can somehow be synced with createFragment so only 1 mapping exists (without a Map<>)
        if(T::class == ChannelContentsFragment::class)
            return createFragment(0) as T;
        else if(T::class == ChannelListFragment::class)
            return createFragment(1) as T;
        //else if(T::class == ChannelStoreFragment::class)
        //    return createFragment(2) as T;
        else if(T::class == ChannelMonetizationFragment::class)
            return createFragment(2) as T;
        else if(T::class == ChannelAboutFragment::class)
            return createFragment(3) as T;
        else
            throw NotImplementedError("Implement other types");
    }

    override fun createFragment(position: Int): Fragment {
        val cachedFragment = _cache[position];
        if (cachedFragment != null) {
            return cachedFragment;
        }

        val fragment = when (position) {
            0 -> ChannelContentsFragment.newInstance().apply {
                onContentClicked.subscribe(this@ChannelViewPagerAdapter.onContentClicked::emit);
                onContentUrlClicked.subscribe(this@ChannelViewPagerAdapter.onContentUrlClicked::emit);
                onUrlClicked.subscribe(this@ChannelViewPagerAdapter.onUrlClicked::emit);
                onChannelClicked.subscribe(this@ChannelViewPagerAdapter.onChannelClicked::emit);
                onAddToClicked.subscribe(this@ChannelViewPagerAdapter.onAddToClicked::emit);
                onAddToQueueClicked.subscribe(this@ChannelViewPagerAdapter.onAddToQueueClicked::emit);
            };
            1 -> ChannelListFragment.newInstance().apply { onClickChannel.subscribe(onChannelClicked::emit) };
            //2 -> ChannelStoreFragment.newInstance();
            2 -> ChannelMonetizationFragment.newInstance();
            3 -> ChannelAboutFragment.newInstance();
            else -> throw IllegalStateException("Invalid tab position $position")
        };

        _cache[position]= fragment;
        return fragment;
    }
}