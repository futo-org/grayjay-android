package com.futo.platformplayer.views.adapters

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder

open class InsertedViewAdapter<TViewHolder> : RecyclerView.Adapter<InsertedViewHolder<TViewHolder>> where TViewHolder : ViewHolder {
    val viewsToPrepend: ArrayList<View>;
    var viewsToAppend: ArrayList<View>;
    protected var childViewHolderFactory: ((viewGroup: ViewGroup, viewType: Int) -> TViewHolder)? = null;
    protected var childViewHolderBinder: ((viewHolder: TViewHolder, position: Int) -> Unit)? = null;
    protected var childCountGetter: (() -> Int)? = null;

    constructor(viewsToPrepend: ArrayList<View>,
                viewsToAppend: ArrayList<View>,
                childCountGetter: () -> Int,
                childViewHolderFactory: (viewGroup: ViewGroup, viewType: Int) -> TViewHolder,
                childViewHolderBinder: (viewHolder: TViewHolder, position: Int) -> Unit) : super()
    {
        this.viewsToPrepend = viewsToPrepend;
        this.viewsToAppend = viewsToAppend;
        this.childCountGetter = childCountGetter;
        this.childViewHolderFactory = childViewHolderFactory;
        this.childViewHolderBinder = childViewHolderBinder;
    }

    protected constructor(viewsToPrepend: ArrayList<View>, viewsToAppend: ArrayList<View>) {
        this.viewsToPrepend = viewsToPrepend;
        this.viewsToAppend = viewsToAppend;
    }

    open fun getChildCount(): Int = childCountGetter!!();
    override fun getItemCount() = viewsToPrepend.size + getChildCount() + viewsToAppend.size;

    open fun createChild(viewGroup: ViewGroup, viewType: Int): TViewHolder = childViewHolderFactory!!.invoke(viewGroup, viewType);
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): InsertedViewHolder<TViewHolder> {
        return InsertedViewHolder(viewGroup.context, createChild(viewGroup, viewType));
    }

    open fun bindChild(holder: TViewHolder, pos: Int) = childViewHolderBinder!!(holder, pos);
    override fun onBindViewHolder(viewHolder: InsertedViewHolder<TViewHolder>, position: Int) {
        if (position < viewsToPrepend.size) {
            viewHolder.bindView(viewsToPrepend[position]);
            return;
        }

        val childCount = getChildCount();
        val originalAdapterPosition = position - viewsToPrepend.size;
        if (originalAdapterPosition < childCount) {
            bindChild(viewHolder.childViewHolder, originalAdapterPosition);
            viewHolder.bindChild();
            return;
        }

        val viewsToAppendIndex = position - childCount - viewsToPrepend.size;
        if (viewsToAppendIndex < viewsToAppend.size) {
            viewHolder.bindView(viewsToAppend[viewsToAppendIndex]);
            return;
        }
    }

    fun childToParentPosition(position: Int): Int {
        return position + viewsToPrepend.size;
    }

    fun parentToChildPosition(position: Int): Int {
        return position - viewsToPrepend.size;
    }
}
