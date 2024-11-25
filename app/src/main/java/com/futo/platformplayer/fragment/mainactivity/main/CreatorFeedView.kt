package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.structures.*
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.*
import com.futo.platformplayer.views.adapters.viewholders.CreatorViewHolder

abstract class CreatorFeedView<TFragment> : FeedView<TFragment, PlatformAuthorLink, PlatformAuthorLink, IPager<PlatformAuthorLink>, CreatorViewHolder> where TFragment : MainFragment {
    override val feedStyle: FeedStyle = FeedStyle.THUMBNAIL; //R.layout.list_creator;

    constructor(fragment: TFragment, inflater: LayoutInflater) : super(fragment, inflater)

    override fun createAdapter(recyclerResults: RecyclerView, context: Context, dataset: ArrayList<PlatformAuthorLink>): InsertedViewAdapterWithLoader<CreatorViewHolder> {
        return InsertedViewAdapterWithLoader(context, arrayListOf(), arrayListOf(),
            childCountGetter = { dataset.size },
            childViewHolderBinder = { viewHolder, position -> viewHolder.bind(dataset[position]); },
            childViewHolderFactory = { viewGroup, _ ->
                val holder = CreatorViewHolder(viewGroup, false);
                holder.onClick.subscribe { c -> fragment.navigate<ChannelFragment>(c) };
                return@InsertedViewAdapterWithLoader holder;
            }
        );
    }

    /*
     * An empty override to remove the inherited span count update functionality
     */
    override fun updateSpanCount(){

    }

    override fun createLayoutManager(
        recyclerResults: RecyclerView,
        context: Context
    ): GridLayoutManager {
        val glmResults = GridLayoutManager(context, 2)

        _swipeRefresh.layoutParams = (_swipeRefresh.layoutParams as MarginLayoutParams?)?.apply {
            rightMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8.0f,
                context.resources.displayMetrics
            ).toInt()
        }

        return glmResults
    }

    companion object {
        private const val TAG = "CreatorFeedView";
    }
}