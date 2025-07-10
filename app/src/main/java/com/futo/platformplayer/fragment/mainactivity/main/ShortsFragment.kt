package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform

@UnstableApi
class ShortsFragment : MainFragment() {
    override val isMainView: Boolean = true
    override val isTab: Boolean = true
    override val hasBottomBar: Boolean get() = true

    private var loadPagerTask: TaskHandler<ShortsFragment, IPager<IPlatformVideo>>? = null
    private var nextPageTask: TaskHandler<ShortsFragment, List<IPlatformVideo>>? = null

    private var mainShortsPager: IPager<IPlatformVideo>? = null
    private val mainShorts: MutableList<IPlatformVideo> = mutableListOf()

    // the pager to call next on
    private var currentShortsPager: IPager<IPlatformVideo>? = null

    // the shorts array bound to the ViewPager2 adapter
    private val currentShorts: MutableList<IPlatformVideo> = mutableListOf()

    private var channelShortsPager: IPager<IPlatformVideo>? = null
    private val channelShorts: MutableList<IPlatformVideo> = mutableListOf()
    val isChannelShortsMode: Boolean
        get() = channelShortsPager != null

    private var viewPager: ViewPager2? = null
    private lateinit var overlayLoading: FrameLayout
    private lateinit var overlayLoadingSpinner: ImageView
    private lateinit var overlayQualityContainer: FrameLayout
    private var customViewAdapter: CustomViewAdapter? = null

    init {
        loadPager()
    }

    // we just completely reset the data structure so we want to tell the adapter that
    @SuppressLint("NotifyDataSetChanged")
    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        (activity as MainActivity?)?.getFragment<VideoDetailFragment>()?.closeVideoDetails()
        super.onShownWithView(parameter, isBack)

        if (parameter is Triple<*, *, *>) {
            setLoading(false)
            channelShorts.clear()
            @Suppress("UNCHECKED_CAST") // TODO replace with a strongly typed parameter
            channelShorts.addAll(parameter.third as ArrayList<IPlatformVideo>)
            @Suppress("UNCHECKED_CAST") // TODO replace with a strongly typed parameter
            channelShortsPager = parameter.second as IPager<IPlatformVideo>

            currentShorts.clear()
            currentShorts.addAll(channelShorts)
            currentShortsPager = channelShortsPager

            viewPager?.adapter?.notifyDataSetChanged()

            viewPager?.post {
                viewPager?.currentItem = channelShorts.indexOfFirst {
                    return@indexOfFirst (parameter.first as IPlatformVideo).id == it.id
                }
            }
        } else if (isChannelShortsMode) {
            channelShortsPager = null
            channelShorts.clear()
            currentShorts.clear()

            if (loadPagerTask == null) {
                currentShorts.addAll(mainShorts)
                currentShortsPager = mainShortsPager
            } else {
                setLoading(true)
            }

            viewPager?.adapter?.notifyDataSetChanged()
            viewPager?.currentItem = 0
        }
    }

    override fun onCreateMainView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_shorts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.view_pager)
        overlayLoading = view.findViewById(R.id.short_view_loading_overlay)
        overlayLoadingSpinner = view.findViewById(R.id.short_view_loader)
        overlayQualityContainer = view.findViewById(R.id.shorts_quality_overview)

        setLoading(true)

        Logger.i(TAG, "Creating adapter")
        val customViewAdapter =
            CustomViewAdapter(currentShorts, layoutInflater, this@ShortsFragment, overlayQualityContainer, { isChannelShortsMode }) {
                if (!currentShortsPager!!.hasMorePages()) {
                    return@CustomViewAdapter
                }
                nextPage()
            }
        customViewAdapter.onResetTriggered.subscribe {
            setLoading(true)
            loadPager()

            loadPagerTask!!.success {
                setLoading(false)
            }
        }
        val viewPager = viewPager!!
        viewPager.adapter = customViewAdapter

        this.customViewAdapter = customViewAdapter

        if (loadPagerTask == null && currentShorts.isEmpty()) {
            loadPager()

            loadPagerTask!!.success {
                setLoading(false)
            }
        } else {
            setLoading(false)
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            fun play(adapter: CustomViewAdapter, position: Int) {
                val recycler = (viewPager.getChildAt(0) as RecyclerView)
                val viewHolder =
                    recycler.findViewHolderForAdapterPosition(position) as CustomViewHolder?

                if (viewHolder == null) {
                    adapter.needToPlay = position
                } else {
                    val focusedView = viewHolder.shortView
                    focusedView.play()
                    adapter.previousShownView = focusedView
                }
            }

            override fun onPageSelected(position: Int) {
                val adapter = (viewPager.adapter as CustomViewAdapter)
                if (adapter.previousShownView == null) {
                    // play if this page selection didn't trigger by a swipe from another page
                    play(adapter, position)
                } else {
                    adapter.previousShownView?.stop()
                    adapter.previousShownView = null
                    adapter.newPosition = position
                }
            }

            // wait for the state to idle to prevent UI lag
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    val adapter = (viewPager.adapter as CustomViewAdapter)
                    val position = adapter.newPosition ?: return
                    adapter.newPosition = null

                    play(adapter, position)
                }
            }
        })
    }

    private fun nextPage() {
        nextPageTask?.cancel()

        val nextPageTask =
            TaskHandler<ShortsFragment, List<IPlatformVideo>>(StateApp.instance.scopeGetter, {
                currentShortsPager!!.nextPage()

                return@TaskHandler currentShortsPager!!.getResults()
            }).success { newVideos ->
                val prevCount = customViewAdapter!!.itemCount
                currentShorts.addAll(newVideos)
                if (isChannelShortsMode) {
                    channelShorts.addAll(newVideos)
                } else {
                    mainShorts.addAll(newVideos)
                }
                customViewAdapter!!.notifyItemRangeInserted(prevCount, newVideos.size)
                nextPageTask = null
            }

        nextPageTask.run(this)

        this.nextPageTask = nextPageTask
    }

    // we just completely reset the data structure so we want to tell the adapter that
    @SuppressLint("NotifyDataSetChanged")
    private fun loadPager() {
        loadPagerTask?.cancel()

        val loadPagerTask =
            TaskHandler<ShortsFragment, IPager<IPlatformVideo>>(StateApp.instance.scopeGetter, {
                val pager = StatePlatform.instance.getShorts()

                return@TaskHandler pager
            }).success { pager ->
                mainShorts.clear()
                mainShorts.addAll(pager.getResults())
                mainShortsPager = pager

                if (!isChannelShortsMode) {
                    currentShorts.clear()
                    currentShorts.addAll(mainShorts)
                    currentShortsPager = pager

                    // if the view pager exists go back to the beginning
                    viewPager?.adapter?.notifyDataSetChanged()
                    viewPager?.currentItem = 0
                }

                loadPagerTask = null
            }.exception<Throwable> { err ->
                val message = "Unable to load shorts $err"
                Logger.i(TAG, message)
                if (context != null) {
                    UIDialogs.showDialog(
                        requireContext(), R.drawable.ic_sources, message, null, null, 0, UIDialogs.Action(
                            "Close", { }, UIDialogs.ActionStyle.PRIMARY
                        )
                    )
                }
                return@exception
            }

        this.loadPagerTask = loadPagerTask

        loadPagerTask.run(this)
    }

    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            (overlayLoadingSpinner.drawable as Animatable?)?.start()
            overlayLoading.visibility = View.VISIBLE
        } else {
            overlayLoading.visibility = View.GONE
            (overlayLoadingSpinner.drawable as Animatable?)?.stop()
        }
    }

    override fun onPause() {
        super.onPause()
        customViewAdapter?.previousShownView?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        loadPagerTask?.cancel()
        loadPagerTask = null
        nextPageTask?.cancel()
        nextPageTask = null
        customViewAdapter?.previousShownView?.stop()
    }

    companion object {
        private const val TAG = "ShortsFragment"

        fun newInstance() = ShortsFragment()
    }

    class CustomViewAdapter(
        private val videos: MutableList<IPlatformVideo>,
        private val inflater: LayoutInflater,
        private val fragment: MainFragment,
        private val overlayQualityContainer: FrameLayout,
        private val isChannelShortsMode:  () -> Boolean,
        private val onNearEnd: () -> Unit,
    ) : RecyclerView.Adapter<CustomViewHolder>() {
        val onResetTriggered = Event0()
        var previousShownView: ShortView? = null
        var newPosition: Int? = null
        var needToPlay: Int? = null

        @OptIn(UnstableApi::class)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
            val shortView =
                ShortView(inflater, fragment, overlayQualityContainer)
            shortView.onResetTriggered.subscribe {
                onResetTriggered.emit()
            }
            return CustomViewHolder(shortView)
        }

        @OptIn(UnstableApi::class)
        override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
            holder.shortView.changeVideo(videos[position], isChannelShortsMode())

            if (position == itemCount - 1) {
                onNearEnd()
            }
        }

        override fun onViewRecycled(holder: CustomViewHolder) {
            super.onViewRecycled(holder)
            holder.shortView.cancel()
        }

        override fun onViewAttachedToWindow(holder: CustomViewHolder) {
            super.onViewAttachedToWindow(holder)

            if (holder.absoluteAdapterPosition == needToPlay) {
                holder.shortView.play()
                needToPlay = null
                previousShownView = holder.shortView
            }
        }

        override fun getItemCount(): Int = videos.size
    }

    @OptIn(UnstableApi::class)
    class CustomViewHolder(val shortView: ShortView) : RecyclerView.ViewHolder(shortView)
}