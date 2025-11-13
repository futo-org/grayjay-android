package com.futo.platformplayer.views

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.views.adapters.*

open class BaseAnyAdapterView<I, T, IT>
        where T : AnyAdapter.AnyViewHolder<I>, IT: RecyclerView.ViewHolder{
    val view: RecyclerView;
    val adapter: BaseAnyAdapter<I, T, IT>;

    constructor(view: RecyclerView, adapter: BaseAnyAdapter<I, T, IT>, orientation: Int, reversed: Boolean) {
        this.view = view;
        this.adapter = adapter;
        view.adapter = adapter.adapter;
        view.layoutManager = LinearLayoutManager(view.context, orientation, reversed);
    }

    fun setData(items: Iterable<I>) {
        adapter.setData(items);
    }
    fun add(item: I) {
        adapter.add(item);
    }

    fun all(cb: (I) -> Unit) {
        adapter.all(cb);
    }
    fun notifyItemRangeInserted(i: Int, itemCount: Int) {
        adapter.notifyItemRangeInserted(i, itemCount)
    }
    fun notifyContentChanged(i: Int) {
        adapter.notifyContentChanged(i);
    }
    fun notifyContentChanged() {
        adapter.notifyContentChanged();
    }
    fun notifyContentChange(item: I) {
        adapter.notifyContentChange(item);
    }
}
class AnyAdapterView<I, T>(view: RecyclerView, adapter: BaseAnyAdapter<I, T, T>, orientation: Int, reversed: Boolean)
    : BaseAnyAdapterView<I, T, T>(view, adapter, orientation, reversed)
        where T : AnyAdapter.AnyViewHolder<I>{

    companion object {
        /*
        inline fun <I, reified T : AnyAdapter.AnyViewHolder<I>> RecyclerView.asAny(list: List<I>, orientation: Int = RecyclerView.VERTICAL, reversed: Boolean = false, noinline onCreate: ((T)->Unit)? = null): AnyAdapterView<I, T> {
            return asAny(ArrayList(list), orientation, reversed, onCreate);
        }*/
        inline fun <I, reified T : AnyAdapter.AnyViewHolder<I>> RecyclerView.asAny(list: ArrayList<I>, orientation: Int = RecyclerView.VERTICAL, reversed: Boolean = false, noinline onCreate: ((T)->Unit)? = null): AnyAdapterView<I, T> {
            return AnyAdapterView(this, AnyAdapter.create(list, onCreate), orientation, reversed);
        }

        inline fun <I, reified T : AnyAdapter.AnyViewHolder<I>> RecyclerView.asAny(orientation: Int = RecyclerView.VERTICAL, reversed: Boolean = false, noinline onCreate: ((T)->Unit)? = null): AnyAdapterView<I, T> {
            return AnyAdapterView(this, AnyAdapter.create(onCreate), orientation, reversed);
        }
    }
}

class AnyInsertedAdapterView<I, T>(view: RecyclerView, adapter: BaseAnyAdapter<I, T, InsertedViewHolder<T>>, orientation: Int, reversed: Boolean)
    : BaseAnyAdapterView<I, T, InsertedViewHolder<T>>(view, adapter, orientation, reversed)
        where T : AnyAdapter.AnyViewHolder<I> {

    companion object {
        inline fun<I, reified T : AnyAdapter.AnyViewHolder<I>> RecyclerView.asAnyWithTop(list: ArrayList<I>, view: View, orientation: Int = RecyclerView.VERTICAL, reversed: Boolean = false, noinline onCreate: ((T)->Unit)? = null) : AnyInsertedAdapterView<I, T>
                = this.asAnyWithViews(list, arrayListOf(view), arrayListOf(), orientation, reversed, onCreate);

        inline fun<I, reified T : AnyAdapter.AnyViewHolder<I>> RecyclerView.asAnyWithTop(view: View, orientation: Int = RecyclerView.VERTICAL, reversed: Boolean = false, noinline onCreate: ((T)->Unit)? = null) : AnyInsertedAdapterView<I, T>
                = this.asAnyWithViews(arrayListOf(view), arrayListOf(), orientation, reversed, onCreate);
        inline fun<I, reified T : AnyAdapter.AnyViewHolder<I>> RecyclerView.asAnyWithViews(prepend: ArrayList<View> = arrayListOf(), append: ArrayList<View> = arrayListOf(), orientation: Int = RecyclerView.VERTICAL, reversed: Boolean = false, noinline onCreate: ((T)->Unit)? = null) : AnyInsertedAdapterView<I, T> {
            for(view in prepend)
                (view.parent as ViewGroup?)?.removeView(view);
            for(view in append)
                (view.parent as ViewGroup?)?.removeView(view);
            return AnyInsertedAdapterView(this, AnyInsertedAdapter.create(prepend, append, onCreate), orientation, reversed);
        }
        inline fun<I, reified T : AnyAdapter.AnyViewHolder<I>> RecyclerView.asAnyWithViews(list: ArrayList<I>, prepend: ArrayList<View> = arrayListOf(), append: ArrayList<View> = arrayListOf(), orientation: Int = RecyclerView.VERTICAL, reversed: Boolean = false, noinline onCreate: ((T)->Unit)? = null) : AnyInsertedAdapterView<I, T> {
            for(view in prepend)
                (view.parent as ViewGroup).removeView(view);
            for(view in append)
                (view.parent as ViewGroup).removeView(view);
            return AnyInsertedAdapterView(this, AnyInsertedAdapter.create(list, prepend, append, onCreate), orientation, reversed);
        }
    }
}