package com.futo.platformplayer.views.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import java.lang.reflect.Constructor

open class BaseAnyAdapter<I, T : AnyAdapter.AnyViewHolder<I>, IT : ViewHolder> {
    protected var _items: ArrayList<I>;
    protected val _holderClass: Class<T>;

    protected val _constructor: Constructor<T>;

    protected val _onCreate: ((T)->Unit)?;

    lateinit var adapter: RecyclerView.Adapter<IT>
        protected set;

    constructor(items: ArrayList<I>, holderClass: Class<T>, onCreate: ((T)->Unit)? = null) : super() {
        _items = items;
        _holderClass = holderClass;
        _constructor = _holderClass.constructors.firstOrNull { it.parameterTypes.size == 1 && it.parameterTypes[0] ==  ViewGroup::class.java } as Constructor<T>?
            ?: throw IllegalStateException("Viewholder [${_holderClass.name}] missing constructor (Context, ViewGroup)");
        _onCreate = onCreate;
    }
    constructor(holderClass: Class<T>, onCreate: ((T)->Unit)? = null) : super() {
        _items = arrayListOf();
        _holderClass = holderClass;
        _constructor = _holderClass.constructors.firstOrNull { it.parameterTypes.size == 1 && it.parameterTypes[0] ==  ViewGroup::class.java } as Constructor<T>?
            ?: throw IllegalStateException("Viewholder [${_holderClass.name}] missing constructor (Context, ViewGroup)");
        _onCreate = onCreate;
    }

    fun setData(newItems: Iterable<I>) {
        _items.clear();
        _items.addAll(newItems);
        adapter.notifyDataSetChanged();
    }
    fun add(item: I) {
        _items.add(item);
        notifyItemInserted(_items.size - 1);
    }

    fun all(cb: (I)->Unit) {
        for(item in _items)
            cb(item);
    }

    fun notifyContentChanged() {
        adapter.notifyDataSetChanged();
    }

    fun notifyContentChanged(position: Int) {
        adapter.notifyItemChanged(position);
    }

    fun notifyItemInserted(position: Int) {
        adapter.notifyItemInserted(position);
    }

    fun notifyItemMoved(fromPosition: Int, toPosition: Int) {
        adapter.notifyItemMoved(fromPosition, toPosition);
    }

    fun notifyItemRangeInserted(positionStart: Int, itemCount: Int) {
        adapter.notifyItemRangeInserted(positionStart, itemCount);
    }
    fun notifyItemRangeChanged(positionStart: Int, itemCount: Int) {
        adapter.notifyItemRangeChanged(positionStart, itemCount);
    }
    fun notifyItemRangeRemoved(positionStart: Int, itemCount: Int) {
        adapter.notifyItemRangeRemoved(positionStart, itemCount);
    }

    fun notifyItemRangeRemoved(position: Int) {
        adapter.notifyItemRemoved(position);
    }


    fun notifyContentChange(item: I) {
        val index = _items.indexOf(item);
        if(index >= 0)
            notifyContentChanged(index);
    }

    companion object {
        inline fun <I, reified T : AnyAdapter.AnyViewHolder<I>> create(prepend: ArrayList<View> = arrayListOf(), append: ArrayList<View> = arrayListOf()) : AnyInsertedAdapter<I, T> {
            return AnyInsertedAdapter(T::class.java, prepend, append);
        }
    }
}

class AnyAdapter<I, T : AnyAdapter.AnyViewHolder<I>> : BaseAnyAdapter<I, T, T> {

    constructor(items: ArrayList<I>, holderClass: Class<T>, onCreate: ((T)->Unit)? = null) : super(items, holderClass, onCreate) {
        adapter = Adapter<I, T>(this);
    }
    constructor(holderClass: Class<T>, onCreate: ((T)->Unit)? = null) : super(holderClass, onCreate) {
        adapter = Adapter<I, T>(this);
    }

    abstract class AnyViewHolder<I>(protected val _view: View) : ViewHolder(_view) {
        abstract fun bind(value: I);
    }

    companion object {
        inline fun <I, reified T : AnyViewHolder<I>> create(list: ArrayList<I>, noinline onCreate: ((T)->Unit)? = null) : AnyAdapter<I, T> {
            return AnyAdapter(list, T::class.java, onCreate);
        }
        inline fun <I, reified T : AnyViewHolder<I>> create(noinline onCreate: ((T)->Unit)? = null) : AnyAdapter<I, T> {
            return AnyAdapter(T::class.java, onCreate);
        }
    }

    private class Adapter<I, T : AnyViewHolder<I>> : RecyclerView.Adapter<T> {
        private val _parent: AnyAdapter<I, T>;

        constructor(parentAdapter: AnyAdapter<I, T>) {
            _parent = parentAdapter;
        }

        override fun getItemCount() = _parent._items.size;

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): T {
            val item = _parent._constructor.newInstance(viewGroup) as T;
            _parent._onCreate?.invoke(item);
            return item;
        }

        override fun onBindViewHolder(viewHolder: T, position: Int) {
            viewHolder.bind(_parent._items[position]);
        }
    }
}

class AnyInsertedAdapter<I, T : AnyAdapter.AnyViewHolder<I>> : BaseAnyAdapter<I, T, InsertedViewHolder<T>>{
    constructor(items: ArrayList<I>, holderClass: Class<T>, prepend: ArrayList<View> = arrayListOf(), append: ArrayList<View> = arrayListOf(), onCreate: ((T)->Unit)? = null)
            : super(items, holderClass, onCreate) {
        adapter = InsertedViewAdapter(prepend, append,
            this::getChildCount,
            this::createChild,
            this::bindChild)
    }
    constructor(holderClass: Class<T>, prepend: ArrayList<View> = arrayListOf(), append: ArrayList<View> = arrayListOf(), onCreate: ((T)->Unit)? = null)
            : super(holderClass, onCreate)  {
        adapter = InsertedViewAdapter(prepend, append,
            this::getChildCount,
            this::createChild,
            this::bindChild)
    }

    fun getChildCount(): Int {
        return _items.size;
    }

    fun createChild(viewGroup: ViewGroup, viewType: Int): T {
        val view = _constructor.newInstance(viewGroup) as T;
        _onCreate?.invoke(view);
        return view;
    }

    fun bindChild(holder: T, pos: Int) {
        holder.bind(_items[pos]);
    }

    companion object {
        inline fun <I, reified T : AnyAdapter.AnyViewHolder<I>> create(list: ArrayList<I>, prepend: ArrayList<View> = arrayListOf(), append: ArrayList<View> = arrayListOf(), noinline onCreate: ((T)->Unit)? = null) : AnyInsertedAdapter<I, T> {
            return AnyInsertedAdapter(list, T::class.java, prepend, append, onCreate);
        }

        inline fun <I, reified T : AnyAdapter.AnyViewHolder<I>> create(prepend: ArrayList<View> = arrayListOf(), noinline onCreate: ((T)->Unit)? = null) : AnyInsertedAdapter<I, T> {
            return AnyInsertedAdapter(T::class.java, prepend, arrayListOf(), onCreate);
        }
        inline fun <I, reified T : AnyAdapter.AnyViewHolder<I>> create(prepend: ArrayList<View> = arrayListOf(), append: ArrayList<View> = arrayListOf(), noinline onCreate: ((T)->Unit)? = null) : AnyInsertedAdapter<I, T> {
            return AnyInsertedAdapter(T::class.java, prepend, append, onCreate);
        }
    }
}