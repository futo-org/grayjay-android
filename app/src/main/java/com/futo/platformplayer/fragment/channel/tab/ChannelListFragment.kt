package com.futo.platformplayer.fragment.channel.tab

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.engine.exceptions.ScriptCaptchaRequiredException
import com.futo.platformplayer.fragment.mainactivity.main.ChannelFragment
import com.futo.platformplayer.fragment.mainactivity.main.PolycentricProfile
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.platformplayer.views.adapters.viewholders.CreatorViewHolder
import com.futo.polycentric.core.toUrl
import kotlinx.coroutines.runBlocking

class ChannelListFragment : Fragment, IChannelTabFragment {
    private var _channels: ArrayList<IPlatformChannel> = arrayListOf();
    private var _authorLinks: ArrayList<PlatformAuthorLink> = arrayListOf();
    private var _currentLoadIndex = 0;
    private var _adapterCreator: InsertedViewAdapterWithLoader<CreatorViewHolder>? = null;
    private var _recyclerCreator: RecyclerView? = null;
    private var _lm: GridLayoutManager? = null;
    private var _lastPolycentricProfile: PolycentricProfile? = null;
    private var _lastChannel : IPlatformChannel? = null;

    val onClickChannel = Event1<PlatformAuthorLink>();

    private var _taskLoadChannel = TaskHandler<String, IPlatformChannel?>({lifecycleScope}, { link ->
        if (!StatePlatform.instance.hasEnabledChannelClient(link)) {
            return@TaskHandler null;
        }

        return@TaskHandler StatePlatform.instance.getChannel(link).await();
    }).success {
        val adapter = _adapterCreator;
        if (it == null || adapter == null || _channels.any { c -> c.url == it.url }) {
            loadNext();
            return@success;
        }

        _channels.add(it);
        _authorLinks.add(PlatformAuthorLink(it.id, it.name, it.url, it.thumbnail));
        adapter.notifyItemInserted(adapter.childToParentPosition(_authorLinks.size - 1));
        loadNext();
    }.exception<ScriptCaptchaRequiredException> {  }
        .exceptionWithParameter<Throwable> { ex, para ->
        Logger.w(ChannelFragment.TAG, "Failed to load results.", ex);
        UIDialogs.toast(requireContext(), "Failed to fetch\n${para}", false)
        loadNext();
    };

    constructor() : super() {

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_channel_list, container, false);

        val recyclerCreator: RecyclerView = view.findViewById(R.id.recycler_creators);
        _adapterCreator = InsertedViewAdapterWithLoader(view.context, arrayListOf(), arrayListOf(),
            childCountGetter = { _authorLinks.size },
            childViewHolderBinder = { viewHolder, position -> viewHolder.bind(_authorLinks[position]); },
            childViewHolderFactory = { viewGroup, _ ->
                val holder = CreatorViewHolder(viewGroup, true);
                holder.onClick.subscribe { c -> onClickChannel.emit(c) };
                return@InsertedViewAdapterWithLoader holder;
            }
        );

        recyclerCreator.adapter = _adapterCreator;

        _lm = GridLayoutManager(view.context, 2);
        recyclerCreator.layoutManager = _lm;
        _recyclerCreator = recyclerCreator;
        _lastChannel?.also { setChannel(it); };
        _lastPolycentricProfile?.also { setPolycentricProfile(it, animate = false); }

        return view;
    }

    override fun onDestroyView() {
        super.onDestroyView();
    }

    override fun setChannel(channel: IPlatformChannel) {
        _lastChannel = channel;
    }

    private fun load() {
        val profile = _lastPolycentricProfile ?: return;
        setLoading(true);

        val url = profile.ownedClaims[_currentLoadIndex].claim.resolveChannelUrl();
        if (url == null) {
            loadNext();
            return;
        }

        _taskLoadChannel.run(url);
    }

    private fun loadNext() {
        val profile = _lastPolycentricProfile;
        if (profile == null) {
            setLoading(false);
            return;
        }

        _currentLoadIndex++;
        if (_currentLoadIndex < profile.ownedClaims.size) {
            load();
        } else {
            setLoading(false);
        }
    }

    fun setPolycentricProfile(polycentricProfile: PolycentricProfile?, animate: Boolean) {
        _taskLoadChannel.cancel();
        _lastPolycentricProfile = polycentricProfile;

        val adapter = _adapterCreator ?: return;
        _channels.clear();
        _authorLinks.clear();
        adapter.notifyDataSetChanged();

        if (polycentricProfile != null) {
            _currentLoadIndex = 0;
            load();
        }
    }

    private fun setLoading(isLoading: Boolean) {
        _adapterCreator?.setLoading(isLoading);
    }

    companion object {
        val TAG = "ChannelListFragment";
        fun newInstance() = ChannelListFragment().apply { }
    }
}