package com.futo.platformplayer.views.adapters

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView.ViewHolder

class InsertedViewHolder<TViewHolder> : ViewHolder where TViewHolder : ViewHolder {
    private val _container: FrameLayout;
    private var _boundView: View? = null;

    val childViewHolder: TViewHolder;

    constructor(context: Context, childViewHolder: TViewHolder) : super(FrameLayout(context)) {
        _container = itemView as FrameLayout;
        this.childViewHolder = childViewHolder;

        _container.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        _container.addView(childViewHolder.itemView);
    }

    fun bindView(view: View) {
        _boundView?.let { _container.removeView(it); }
        childViewHolder.itemView.visibility = View.GONE;

        val parent = view.parent;
        if (parent != null && parent is ViewGroup) {
            parent.removeView(view);
        }

        _container.addView(view);
        _boundView = view;
    }

    fun bindChild() {
        val boundView = _boundView;
        if (boundView != null) {
            _container.removeView(boundView);
            _boundView = null;
        }

        childViewHolder.itemView.visibility = View.VISIBLE;
    }
}