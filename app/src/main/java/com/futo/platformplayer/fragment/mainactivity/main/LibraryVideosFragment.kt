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
import android.widget.LinearLayout.GONE
import android.widget.LinearLayout.VISIBLE
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.allViews
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.UISlideOverlays.Companion.showOrderOverlay
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.structures.AdhocPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.Album
import com.futo.platformplayer.states.StateLibrary
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringArrayStorage
import com.futo.platformplayer.stores.StringStorage
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.ToggleBar
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.platformplayer.views.adapters.SubscriptionAdapter
import com.futo.platformplayer.views.adapters.viewholders.SelectablePlaylist
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.platform.PlatformIndicator

class LibraryVideosFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _toggleBuckets = StateLibrary.instance.getVideoBucketNames().map { it.name }.toMutableList();

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
        view?.onShown();
    }

    override fun onDestroyMainView() {
        view = null;
        super.onDestroyMainView();
    }

    companion object {
        fun newInstance() = LibraryVideosFragment().apply {}
    }

    class FragView : ContentFeedView<LibraryVideosFragment> {
        override val feedStyle: FeedStyle = FeedStyle.THUMBNAIL; //R.layout.list_creator;

        private var _toggleBar: ToggleBar? = null;

        constructor(fragment: LibraryVideosFragment, inflater: LayoutInflater) : super(fragment, inflater) {
            initializeToolbarContent();
            disableRefreshLayout();
        }

        fun onShown() {
            val initialAlbums = StateLibrary.instance.getAlbums();
            Logger.i(TAG, "Initial album count: " + initialAlbums.size);
            setPager(StateLibrary.instance.getVideos(fragment._toggleBuckets));
        }


        private val _filterLock = Object();
        fun initializeToolbarContent() {
            if(_toolbarContentView.allViews.any { it is ToggleBar })
                _toolbarContentView.removeView(_toolbarContentView.allViews.find { it is ToggleBar });
            _toggleBar = ToggleBar(context).apply {
                layoutParams =
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            }

            synchronized(_filterLock) {
                var buttonsPlugins: List<ToggleBar.Toggle> = listOf()
                buttonsPlugins =
                    (StateLibrary.instance.getVideoBucketNames()
                        .map { bucket ->
                            ToggleBar.Toggle(bucket.name, null, fragment._toggleBuckets.contains(bucket.name), { view, active ->
                                var dontSwap = false;
                                if (!active) {
                                    if (fragment._toggleBuckets.contains(bucket.name))
                                        fragment._toggleBuckets.remove(bucket.name);
                                } else {
                                    if (!fragment._toggleBuckets.contains(bucket.name)) {
                                        val enabledClients = StatePlatform.instance.getEnabledClients();
                                        val availableAfterDisable = enabledClients.count { !fragment._toggleBuckets.contains(it.id) && it.id != bucket.name };
                                        if(availableAfterDisable > 0)
                                            fragment._toggleBuckets.add(bucket.name);
                                        else {
                                            UIDialogs.appToast("Select atleast 1 bucket");
                                            dontSwap = true;
                                        }
                                    }
                                }
                                if(!dontSwap)
                                    reloadForFilters();
                                else {
                                    view.setToggle(active);
                                }
                            }, { view, views, enabled ->
                                val toDisable = views.filter { it != view && it.tag == "plugins" };
                                if(!view.isActive)
                                    view.handleClick();
                                for(tag in toDisable) {
                                    if(tag.isActive)
                                        tag.handleClick();
                                }
                            }).withTag("plugins")
                        })
                val buttons = (buttonsPlugins)
                    .sortedBy { it.name }.toTypedArray()

                _toggleBar?.setToggles(*buttons);
            }

            _toolbarContentView.addView(_toggleBar, 0);
        }

        fun reloadForFilters() {
            setPager(StateLibrary.instance.getVideos(fragment._toggleBuckets));
        }

        override fun updateSpanCount(){ }


        companion object {
            private const val TAG = "LibraryAlbumsFragmentsView";
        }
    }
}