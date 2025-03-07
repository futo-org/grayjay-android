package com.futo.platformplayer.fragment.mainactivity.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.core.view.get
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.futo.platformplayer.R

@UnstableApi
class ShortsFragment : MainFragment() {
    override val isMainView: Boolean = true
    override val isTab: Boolean = true
    override val hasBottomBar: Boolean get() = true

    private var previousShownView: ShortView? = null

    private lateinit var viewPager: ViewPager2
    private lateinit var customViewAdapter: CustomViewAdapter
    private val urls = listOf(
        "https://youtube.com/shorts/fHU6dfPHT-o?si=TVCYnt_mvAxWYACZ", "https://youtube.com/shorts/j9LQ0c4MyGk?si=FVlr90UD42y1ZIO0", "https://youtube.com/shorts/Q8LndW9YZvQ?si=mDrSsm-3Uq7IEXAT", "https://youtube.com/shorts/OIS5qHDOOzs?si=RGYeaAH9M-TRuZSr", "https://youtube.com/shorts/1Cp6EbLWVnI?si=N4QqytC48CTnfJra", "https://youtube.com/shorts/fHU6dfPHT-o?si=TVCYnt_mvAxWYACZ", "https://youtube.com/shorts/j9LQ0c4MyGk?si=FVlr90UD42y1ZIO0", "https://youtube.com/shorts/Q8LndW9YZvQ?si=mDrSsm-3Uq7IEXAT", "https://youtube.com/shorts/OIS5qHDOOzs?si=RGYeaAH9M-TRuZSr", "https://youtube.com/shorts/1Cp6EbLWVnI?si=N4QqytC48CTnfJra"
    )

    override fun onCreateMainView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_shorts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.viewPager)

        customViewAdapter = CustomViewAdapter(urls, layoutInflater, this)
        viewPager.adapter = customViewAdapter

        // TODO something is laggy sometimes when swiping between videos
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            @OptIn(UnstableApi::class)
            override fun onPageSelected(position: Int) {
                previousShownView?.stop()

                val focusedView =
                    ((viewPager[0] as RecyclerView).findViewHolderForAdapterPosition(position) as CustomViewHolder).shortView
                focusedView.play()


                previousShownView = focusedView
            }

        })

    }

    override fun onPause() {
        super.onPause()
        previousShownView?.stop()
    }

    companion object {
        private const val TAG = "ShortsFragment"

        fun newInstance() = ShortsFragment()
    }

    class CustomViewAdapter(
        private val urls: List<String>, private val inflater: LayoutInflater, private val fragment: MainFragment
    ) : RecyclerView.Adapter<CustomViewHolder>() {
        @OptIn(UnstableApi::class)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
            val shortView = ShortView(inflater, fragment)
            return CustomViewHolder(shortView)
        }

        @OptIn(UnstableApi::class)
        override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
            holder.shortView.setVideo(urls[position])
        }

        @OptIn(UnstableApi::class)
        override fun onViewRecycled(holder: CustomViewHolder) {
            super.onViewRecycled(holder)
            holder.shortView.detach()
        }

        override fun getItemCount(): Int = urls.size
    }

    @OptIn(UnstableApi::class)
    class CustomViewHolder(val shortView: ShortView) : RecyclerView.ViewHolder(shortView)
}