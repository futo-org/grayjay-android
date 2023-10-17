package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.futo.platformplayer.*
import com.futo.platformplayer.activities.CaptchaActivity
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.internal.JSHttpClient
import com.futo.platformplayer.api.media.structures.EmptyPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.engine.exceptions.ScriptCaptchaRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptExecutionException
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.fragment.mainactivity.topbar.ImportTopBarFragment
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.others.CaptchaWebViewClient
import com.futo.platformplayer.states.AnnouncementType
import com.futo.platformplayer.states.StateAnnouncement
import com.futo.platformplayer.states.StateMeta
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.views.announcements.AnnouncementView
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.ContentPreviewViewHolder
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.platformplayer.views.adapters.InsertedViewHolder
import java.time.OffsetDateTime
import java.util.UUID

class HomeFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _view: HomeView? = null;
    private var _cachedRecyclerData: FeedView.RecyclerData<InsertedViewAdapterWithLoader<ContentPreviewViewHolder>, LinearLayoutManager, IPager<IPlatformContent>, IPlatformContent, IPlatformContent, InsertedViewHolder<ContentPreviewViewHolder>>? = null;

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        _view?.onShown();
    }

    override fun onResume() {
        super.onResume()
        _view?.onResume();
    }

    override fun onPause() {
        super.onPause()
        _view?.onPause();
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = HomeView(this, inflater, _cachedRecyclerData);
        _view = view;
        return view;
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();

        val view = _view;
        if (view != null) {
            _cachedRecyclerData = view.recyclerData;
            view.cleanup();
            _view = null;
        }
    }

    fun setPreviewsEnabled(previewsEnabled: Boolean) {
        _view?.setPreviewsEnabled(previewsEnabled);
    }

    @SuppressLint("ViewConstructor")
    class HomeView : ContentFeedView<HomeFragment> {
        override val feedStyle: FeedStyle get() = Settings.instance.home.getHomeFeedStyle();

        private var _announcementsView: AnnouncementView;

        private val _taskGetPager: TaskHandler<Boolean, IPager<IPlatformContent>>;

        constructor(fragment: HomeFragment, inflater: LayoutInflater, cachedRecyclerData: RecyclerData<InsertedViewAdapterWithLoader<ContentPreviewViewHolder>, LinearLayoutManager, IPager<IPlatformContent>, IPlatformContent, IPlatformContent, InsertedViewHolder<ContentPreviewViewHolder>>? = null) : super(fragment, inflater, cachedRecyclerData) {
            _announcementsView = AnnouncementView(context, null).apply {
                headerView.addView(this);
            };

            _taskGetPager = TaskHandler<Boolean, IPager<IPlatformContent>>({ fragment.lifecycleScope }, {
                StatePlatform.instance.getHomeRefresh(fragment.lifecycleScope)
            })
            .success { loadedResult(it); }
            .exception<ScriptCaptchaRequiredException> {
                Logger.w(TAG, "Plugin captcha required.", it);

                UIDialogs.showConfirmationDialog(context, "Captcha required\nPlugin [${it.config.name}]", action = {
                    CaptchaActivity.showCaptcha(context, it.url, it.body) {
                        if (it != null) {
                            Logger.i(TAG, "Captcha entered $it")
                            JSHttpClient.exemptionId = it;
                            //TODO: Reload plugin when captcha completed? is it necessary
                            loadResults();
                        }
                    }
                })
            }
            .exception<ScriptExecutionException> {
                Logger.w(ChannelFragment.TAG, "Plugin failure.", it);
                UIDialogs.showDialog(context, R.drawable.ic_error_pred, "Failed to get Home\nPlugin [${it.config.name}]", it.message, null, 0,
                    UIDialogs.Action("Ignore", {}),
                    UIDialogs.Action("Sources", { fragment.navigate<SourcesFragment>() }, UIDialogs.ActionStyle.PRIMARY)
                );
            }
            .exception<ScriptImplementationException> {
                Logger.w(TAG, "Plugin failure.", it);
                UIDialogs.showDialog(context, R.drawable.ic_error_pred, "Failed to get Home\nPlugin [${it.config.name}]", it.message, null, 0,
                    UIDialogs.Action("Ignore", {}),
                    UIDialogs.Action("Sources", { fragment.navigate<SourcesFragment>() }, UIDialogs.ActionStyle.PRIMARY)
                );
            }
            .exception<Throwable> {
                Logger.w(TAG, "Failed to load channel.", it);
                UIDialogs.showGeneralRetryErrorDialog(context, "Failed to get Home", it, {
                    loadResults()
                }) {
                    finishRefreshLayoutLoader();
                    setLoading(false);
                };
            };
        }

        fun onShown() {
            val lastClients = recyclerData.lastClients;
            val clients = StatePlatform.instance.getSortedEnabledClient().filter { if (it is JSClient) it.enableInHome else true };

            val feedstyleChanged = recyclerData.loadedFeedStyle != feedStyle;
            val clientsChanged = lastClients == null || lastClients.size != clients.size || !lastClients.containsAll(clients);
            val outdated = recyclerData.lastLoad.getNowDiffSeconds() > 60;
            Logger.i(TAG, "onShown (recyclerData.loadedFeedStyle=${recyclerData.loadedFeedStyle}, recyclerData.lastLoad=${recyclerData.lastLoad}, feedstyleChanged=$feedstyleChanged, clientsChanged=$clientsChanged, outdated=$outdated)")

            if(feedstyleChanged || outdated || clientsChanged) {
                recyclerData.lastLoad = OffsetDateTime.now();
                recyclerData.loadedFeedStyle = feedStyle;
                recyclerData.lastClients = clients;
                loadResults();
            } else {
                setLoading(false);
            }

            finishRefreshLayoutLoader();
        }

        override fun reload() {
            loadResults();
        }

        override fun filterResults(contents: List<IPlatformContent>): List<IPlatformContent> {
            return contents.filter { it !is IPlatformVideo || !StateMeta.instance.isVideoHidden(it.url) };
        }

        private fun loadResults() {
            setLoading(true);
            _taskGetPager.run(true);
        }
        private fun loadedResult(pager : IPager<IPlatformContent>) {
            if (pager is EmptyPager<IPlatformContent>) {
                StateAnnouncement.instance.registerAnnouncement(UUID.randomUUID().toString(), "No home available", "No home page is available, please check if you are connected to the internet and refresh.", AnnouncementType.SESSION);
            }

            Logger.i(TAG, "Got new home pager ${pager}");
            finishRefreshLayoutLoader();
            setLoading(false);
            setPager(pager);
        }
    }

    companion object {
        val TAG = "HomeFragment";

        fun newInstance() = HomeFragment().apply {}
    }
}