package com.futo.platformplayer.fragment.channel.tab

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.models.JSPager
import com.futo.platformplayer.api.media.structures.IAsyncPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.IRefreshPager
import com.futo.platformplayer.api.media.structures.IReplacerPager
import com.futo.platformplayer.api.media.structures.MultiPager
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.engine.exceptions.PluginException
import com.futo.platformplayer.engine.exceptions.ScriptCaptchaRequiredException
import com.futo.platformplayer.exceptions.ChannelException
import com.futo.platformplayer.fragment.mainactivity.main.FeedView
import com.futo.platformplayer.fragment.mainactivity.main.PolycentricProfile
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateCache
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.ContentPreviewViewHolder
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.platformplayer.views.adapters.feedtypes.PreviewContentListAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max

class ChannelContentsFragment(private val subType: String? = null) : Fragment(), IChannelTabFragment {
    private var _recyclerResults: RecyclerView? = null;
    private var _glmVideo: GridLayoutManager? = null;
    private var _loading = false;
    private var _pager_parent: IPager<IPlatformContent>? = null;
    private var _pager: IPager<IPlatformContent>? = null;
    private var _cache: FeedView.ItemCache<IPlatformContent>? = null;
    private var _channel: IPlatformChannel? = null;
    private var _results: ArrayList<IPlatformContent> = arrayListOf();
    private var _adapterResults: InsertedViewAdapterWithLoader<ContentPreviewViewHolder>? = null;
    private var _lastPolycentricProfile: PolycentricProfile? = null;

    val onContentClicked = Event2<IPlatformContent, Long>();
    val onContentUrlClicked = Event2<String, ContentType>();
    val onUrlClicked = Event1<String>();
    val onChannelClicked = Event1<PlatformAuthorLink>();
    val onAddToClicked = Event1<IPlatformContent>();
    val onAddToQueueClicked = Event1<IPlatformContent>();
    val onAddToWatchLaterClicked = Event1<IPlatformContent>();
    val onLongPress = Event1<IPlatformContent>();


    private fun getContentPager(channel: IPlatformChannel): IPager<IPlatformContent> {
        Logger.i(TAG, "getContentPager");

        val lastPolycentricProfile = _lastPolycentricProfile;
        var pager: IPager<IPlatformContent>? = null;
        if (lastPolycentricProfile != null)
            pager= StatePolycentric.instance.getChannelContent(lifecycleScope, lastPolycentricProfile);

        if(pager == null) {
            if(subType != null)
                pager = StatePlatform.instance.getChannelContent(channel.url, subType);
            else
                pager = StatePlatform.instance.getChannelContent(channel.url);
        }
        return pager;
    }

    private val _taskLoadVideos = TaskHandler<IPlatformChannel, IPager<IPlatformContent>>({lifecycleScope}, {
            val livePager = getContentPager(it);
            return@TaskHandler if(_channel?.let { StateSubscriptions.instance.isSubscribed(it) } == true)
                StateCache.cachePagerResults(lifecycleScope, livePager);
            else livePager;
        }).success { livePager ->
            setLoading(false);

            setPager(livePager);
        }
        .exception<ScriptCaptchaRequiredException> {  }
        .exception<Throwable> {
            Logger.w(TAG, "Failed to load initial videos.", it);
            UIDialogs.showGeneralRetryErrorDialog(requireContext(), it.message ?: "", it, { loadNextPage() });
    };

    private var _nextPageHandler: TaskHandler<IPager<IPlatformContent>, List<IPlatformContent>> = TaskHandler<IPager<IPlatformContent>, List<IPlatformContent>>({lifecycleScope}, {
        if (it is IAsyncPager<*>)
            it.nextPageAsync();
        else
            it.nextPage();

        processPagerExceptions(it);
        return@TaskHandler it.getResults();
    }).success {
        setLoading(false);
        val posBefore = _results.size;
        //val toAdd = it.filter { it is IPlatformVideo }.map { it as IPlatformVideo }
        _results.addAll(it);
        _adapterResults?.let { adapterVideo -> adapterVideo.notifyItemRangeInserted(adapterVideo.childToParentPosition(posBefore), it.size); };
    }.exception<Throwable> {
        Logger.w(TAG, "Failed to load next page.", it);
        UIDialogs.showGeneralRetryErrorDialog(requireContext(), it.message ?: "", it, { loadNextPage() });
    };

    private val _scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy);

            val recyclerResults = _recyclerResults ?: return;
            val llmVideo = _glmVideo ?: return;

            val visibleItemCount = recyclerResults.childCount;
            val firstVisibleItem = llmVideo.findFirstVisibleItemPosition();
            val visibleThreshold = 15;
            if (!_loading && firstVisibleItem + visibleItemCount + visibleThreshold >= _results.size) {
                loadNextPage();
            }
        }
    };

    override fun setChannel(channel: IPlatformChannel) {
        val c = _channel;
        if (c != null && c.url == channel.url) {
            Logger.i(TAG, "setChannel skipped because previous was same");
            return;
        }

        Logger.i(TAG, "setChannel setChannel=${channel}")

        _taskLoadVideos.cancel();

        _channel = channel;
        _results.clear();
        _adapterResults?.notifyDataSetChanged();

        loadInitial();
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_channel_videos, container, false);

        _recyclerResults = view.findViewById(R.id.recycler_videos);

        _adapterResults = PreviewContentListAdapter(view.context, FeedStyle.THUMBNAIL, _results, null, Settings.instance.channel.progressBar).apply {
            this.onContentUrlClicked.subscribe(this@ChannelContentsFragment.onContentUrlClicked::emit);
            this.onUrlClicked.subscribe(this@ChannelContentsFragment.onUrlClicked::emit);
            this.onContentClicked.subscribe(this@ChannelContentsFragment.onContentClicked::emit);
            this.onChannelClicked.subscribe(this@ChannelContentsFragment.onChannelClicked::emit);
            this.onAddToClicked.subscribe(this@ChannelContentsFragment.onAddToClicked::emit);
            this.onAddToQueueClicked.subscribe(this@ChannelContentsFragment.onAddToQueueClicked::emit);
            this.onAddToWatchLaterClicked.subscribe(this@ChannelContentsFragment.onAddToWatchLaterClicked::emit);
            this.onLongPress.subscribe(this@ChannelContentsFragment.onLongPress::emit);
        }

        val numColumns = max((resources.configuration.screenWidthDp.toDouble() / resources.getInteger(R.integer.column_width_dp)).toInt(), 1)
        _glmVideo = GridLayoutManager(view.context, numColumns);
        _recyclerResults?.adapter = _adapterResults;
        _recyclerResults?.layoutManager = _glmVideo;
        _recyclerResults?.addOnScrollListener(_scrollListener);

        return view;
    }

    override fun onDestroyView() {
        super.onDestroyView();
        _recyclerResults?.removeOnScrollListener(_scrollListener);
        _recyclerResults = null;
        _pager = null;

        _taskLoadVideos.cancel();
        _nextPageHandler.cancel();
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        _glmVideo?.spanCount =
            max((resources.configuration.screenWidthDp.toDouble() / resources.getInteger(R.integer.column_width_dp)).toInt(), 1)
    }

    /*
    private fun setPager(pager: IPager<IPlatformContent>, cache: FeedFragment.ItemCache<IPlatformContent>? = null) {
        if (_pager_parent != null && _pager_parent is IRefreshPager<*>) {
            (_pager_parent as IRefreshPager<*>).onPagerError?.remove(this);
            (_pager_parent as IRefreshPager<*>).onPagerChanged?.remove(this);
            _pager_parent = null;
        }

        if(pager is IRefreshPager<*>) {
            _pager_parent = pager;
            _pager = pager.getCurrentPager() as IPager<IPlatformContent>;
            pager.onPagerChanged.subscribe {
                lifecycleScope.launch(Dispatchers.Main) {
                    setPager(it as IPager<IPlatformContent>);
                }
            };
            pager.onPagerError.subscribe {
                Logger.e(TAG, "Search pager failed: ${it.message}", it);
                if(it is PluginException)
                    UIDialogs.toast("Plugin [${it.config.name}] failed due to:\n${it.message}");
                else
                    UIDialogs.toast("Plugin failed due to:\n${it.message}");
            };
        }
        else _pager = pager;
        _cache = cache;

        processPagerExceptions(pager);

        _results.clear();
        val toAdd = pager.getResults();
        _results.addAll(toAdd);
        _adapterResults?.notifyDataSetChanged();
        _recyclerResults?.scrollToPosition(0);
    }*/

    fun setPager(pager: IPager<IPlatformContent>, cache: FeedView.ItemCache<IPlatformContent>? = null) {
        if (_pager_parent != null && _pager_parent is IRefreshPager<*>) {
            (_pager_parent as IRefreshPager<*>).onPagerError.remove(this);
            (_pager_parent as IRefreshPager<*>).onPagerChanged.remove(this);
            _pager_parent = null;
        }
        if(_pager is IReplacerPager<*>)
            (_pager as IReplacerPager<*>).onReplaced.remove(this);

        var pagerToSet: IPager<IPlatformContent>?;
        if(pager is IRefreshPager<*>) {
            _pager_parent = pager;
            pagerToSet = pager.getCurrentPager() as IPager<IPlatformContent>;
            pager.onPagerChanged.subscribe(this) {

                lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        loadPagerInternal(it as IPager<IPlatformContent>);
                    } catch (e: Throwable) {
                        Logger.e(TAG, "loadPagerInternal failed.", e)
                    }
                }
            };
            pager.onPagerError.subscribe(this) {
                Logger.e(TAG, "Search pager failed: ${it.message}", it);
                if(it is PluginException)
                    UIDialogs.toast("Plugin [${it.config.name}] failed due to:\n${it.message}");
                else
                    UIDialogs.toast("Plugin failed due to:\n${it.message}");
            };
        }
        else pagerToSet = pager;

        loadPagerInternal(pagerToSet, cache);
    }
    private fun loadPagerInternal(pager: IPager<IPlatformContent>, cache: FeedView.ItemCache<IPlatformContent>? = null) {
        _cache = cache;

        if(_pager is IReplacerPager<*>)
            (_pager as IReplacerPager<*>).onReplaced.remove(this);
        if(pager is IReplacerPager<*>) {
            pager.onReplaced.subscribe(this) { oldItem, newItem ->
                if(_pager != pager)
                    return@subscribe;

                if(_pager !is IPager<IPlatformContent>)
                    return@subscribe;


                lifecycleScope.launch(Dispatchers.Main) {
                    val toReplaceIndex = _results.indexOfFirst { it == oldItem };
                    if (toReplaceIndex >= 0) {
                        _results[toReplaceIndex] = newItem as IPlatformContent;
                        _adapterResults?.let {
                            it.notifyItemChanged(it.childToParentPosition(toReplaceIndex));
                        }
                    }
                }
            }
        }

        _pager = pager;

        processPagerExceptions(pager);

        _results.clear();
        val toAdd = pager.getResults();
        _results.addAll(toAdd);
        //insertPagerResults(toAdd, true);
        _adapterResults?.notifyDataSetChanged();
        _recyclerResults?.scrollToPosition(0);
    }

    private fun loadInitial() {
        val channel: IPlatformChannel = _channel ?: return;
        setLoading(true);
        _taskLoadVideos.run(channel);
    }

    private fun loadNextPage() {
        val pager: IPager<IPlatformContent> = _pager ?: return;
        if(_pager?.hasMorePages() ?: false) {
            setLoading(true);
            _nextPageHandler.run(pager);
        }
    }

    private fun setLoading(loading: Boolean) {
        _loading = loading;
        _adapterResults?.setLoading(loading);
    }

    override fun setPolycentricProfile(polycentricProfile: PolycentricProfile?) {
        val p = _lastPolycentricProfile;
        if (p != null && polycentricProfile != null && p.system == polycentricProfile.system) {
            Logger.i(TAG, "setPolycentricProfile skipped because previous was same");
            return;
        }

        _lastPolycentricProfile = polycentricProfile;

        if (polycentricProfile != null) {
            _taskLoadVideos.cancel();
            val itemsRemoved = _results.size;
            _results.clear();
            _adapterResults?.notifyItemRangeRemoved(0, itemsRemoved);
            loadInitial();
        }
    }

    private fun processPagerExceptions(pager: IPager<*>) {
        if(pager is MultiPager<*> && pager.allowFailure) {
            val ex = pager.getResultExceptions();
            for(kv in ex) {
                val jsVideoPager: JSPager<*>? = if(kv.key is MultiPager<*>)
                    (kv.key as MultiPager<*>).findPager { it is JSPager<*> } as JSPager<*>?;
                else if(kv.key is JSPager<*>)
                    kv.key as JSPager<*>;
                else null;

                context?.let {
                    lifecycleScope.launch(Dispatchers.Main) {
                        try {
                            val channel = if(kv.value is ChannelException) (kv.value as ChannelException).channelNameOrUrl else null;
                            if(jsVideoPager != null)
                                UIDialogs.toast(it, "Plugin ${jsVideoPager.getPluginConfig().name} failed:\n" +
                                        (if(!channel.isNullOrEmpty()) "(${channel}) " else "") +
                                        "${kv.value.message}", false);
                            else
                                UIDialogs.toast(it, kv.value.message ?: "", false);
                        } catch (e: Throwable) {
                            Logger.e(TAG, "Failed to show toast.", e)
                        }
                    }
                }
            }
        }
    }

    companion object {
        val TAG = "VideoListFragment";
        fun newInstance(subType: String? = null) = ChannelContentsFragment(subType).apply { }
    }
}