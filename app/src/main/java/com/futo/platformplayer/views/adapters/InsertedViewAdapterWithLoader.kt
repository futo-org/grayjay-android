package com.futo.platformplayer.views.adapters

import android.content.Context
import android.graphics.drawable.Animatable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.futo.platformplayer.R

open class InsertedViewAdapterWithLoader<TViewHolder> : InsertedViewAdapter<TViewHolder> where TViewHolder : ViewHolder {
    private var _loaderView: ImageView? = null;
    private var _loading = false;

    constructor(
        context: Context,
        viewsToPrepend: ArrayList<View>,
        viewsToAppend: ArrayList<View>,
        childCountGetter: () -> Int,
        childViewHolderFactory: (viewGroup: ViewGroup, viewType: Int) -> TViewHolder,
        childViewHolderBinder: (viewHolder: TViewHolder, position: Int) -> Unit) : super(
            viewsToPrepend = viewsToPrepend,
            viewsToAppend = viewsToAppend,
            childCountGetter = childCountGetter,
            childViewHolderFactory = childViewHolderFactory,
            childViewHolderBinder = childViewHolderBinder
        )
    {
        val loaderView = createLoaderView(context);
        this.viewsToAppend.add(loaderView);
        _loaderView = loaderView;
    }

    protected constructor(
        context: Context,
        viewsToPrepend: ArrayList<View>,
        viewsToAppend: ArrayList<View>) : super(
        viewsToPrepend = viewsToPrepend,
        viewsToAppend = viewsToAppend)
    {
        val loaderView = createLoaderView(context);
        this.viewsToAppend.add(loaderView);
        _loaderView = loaderView;
    }

    fun setLoading(loading: Boolean) {
        if (_loading == loading) {
            return;
        }

        _loading = loading;

        if (loading) {
            _loaderView?.let {
                it.visibility = View.VISIBLE;
                (it.drawable as Animatable?)?.start();
            };
        } else {
            _loaderView?.let {
                it.visibility = View.INVISIBLE;
                (it.drawable as Animatable?)?.stop();
            };
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        (_loaderView?.drawable as Animatable?)?.stop();
    }

    companion object {
        private fun createLoaderView(context: Context): ImageView {
            val loaderView = ImageView(context);
            loaderView.visibility = View.GONE;
            loaderView.contentDescription = context.resources.getString(R.string.loading);
            loaderView.setImageResource(R.drawable.ic_loader_animated);

            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50.0f, context.resources.displayMetrics).toInt());
            lp.marginStart = 10;
            lp.marginEnd = 10;
            lp.gravity = Gravity.CENTER;
            loaderView.layoutParams = lp;

            return loaderView;
        }
    }
}
