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
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@UnstableApi
class ShortsFragment : MainFragment() {
    override val isMainView: Boolean = true
    override val isTab: Boolean = true
    override val hasBottomBar: Boolean get() = true

    private var loadPagerJob: Job? = null
    private var nextPageJob: Job? = null

    private var shortsPager: IPager<IPlatformVideo>? = null
    private val videos: MutableList<IPlatformVideo> = mutableListOf()

    private var viewPager: ViewPager2? = null
    private lateinit var overlayLoading: FrameLayout
    private lateinit var overlayLoadingSpinner: ImageView
    private lateinit var overlayQualityContainer: FrameLayout
    private lateinit var customViewAdapter: CustomViewAdapter

    init {
        loadPager()
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

        if (loadPagerJob?.isActive == false && videos.isEmpty()) {
            loadPager()
        }

        loadPagerJob!!.invokeOnCompletion {
            Logger.i(TAG, "Creating adapter")
            customViewAdapter =
                CustomViewAdapter(videos, layoutInflater, this@ShortsFragment, overlayQualityContainer) {
                    if (!shortsPager!!.hasMorePages()) {
                        return@CustomViewAdapter
                    }
                    nextPage()
                }
            customViewAdapter.onResetTriggered.subscribe {
                setLoading(true)
                loadPager()
                loadPagerJob!!.invokeOnCompletion {
                    setLoading(false)
                }
            }
            val viewPager = viewPager!!
            viewPager.adapter = customViewAdapter

            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                @OptIn(UnstableApi::class)
                override fun onPageSelected(position: Int) {
                    val adapter = (viewPager.adapter as CustomViewAdapter)
                    adapter.previousShownView?.stop()
                    adapter.previousShownView = null

                    // the post prevents lag when swiping
                    viewPager.post {
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
                }
            })
            setLoading(false)
        }
    }

    private fun nextPage() {
        nextPageJob?.cancel()

        nextPageJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    shortsPager!!.nextPage()
                }
            } catch (_: CancellationException) {
                return@launch
            }

            // if it's been canceled then don't update the results
            if (!isActive) {
                return@launch
            }

            val newVideos = shortsPager!!.getResults()
            CoroutineScope(Dispatchers.Main).launch {
                val prevCount = customViewAdapter.itemCount
                videos.addAll(newVideos)
                customViewAdapter.notifyItemRangeInserted(prevCount, newVideos.size)
            }
        }
    }

    // we just completely reset the data structure so we want to tell the adapter that
    @SuppressLint("NotifyDataSetChanged")
    private fun loadPager() {
        loadPagerJob?.cancel()

        // if the view pager exists go back to the beginning
        videos.clear()
        viewPager?.adapter?.notifyDataSetChanged()
        viewPager?.currentItem = 0

        loadPagerJob = CoroutineScope(Dispatchers.Main).launch {
            val pager = try {
                withContext(Dispatchers.IO) {
                    StatePlatform.instance.getShorts()
                }
            } catch (_: CancellationException) {
                return@launch
            }

            // if it's been canceled then don't set the video pager
            if (!isActive) {
                return@launch
            }

            videos.clear()
            videos.addAll(pager.getResults())
            shortsPager = pager

            // if the viewPager exists then trigger data changed
            viewPager?.adapter?.notifyDataSetChanged()
        }
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
        customViewAdapter.previousShownView?.pause()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onStop() {
        super.onStop()
        customViewAdapter.previousShownView?.stop()
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
        private val onNearEnd: () -> Unit,
    ) : RecyclerView.Adapter<CustomViewHolder>() {
        val onResetTriggered = Event0()
        var previousShownView: ShortView? = null
        var needToPlay: Int? = null

        @OptIn(UnstableApi::class)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
            val shortView = ShortView(inflater, fragment, overlayQualityContainer)
            shortView.onResetTriggered.subscribe {
                onResetTriggered.emit()
            }
            return CustomViewHolder(shortView)
        }

        @OptIn(UnstableApi::class)
        override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
            holder.shortView.changeVideo(videos[position])

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