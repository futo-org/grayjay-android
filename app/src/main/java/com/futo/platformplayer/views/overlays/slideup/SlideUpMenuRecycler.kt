package com.futo.platformplayer.views.overlays.slideup

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.views.AnyAdapterView
import com.futo.platformplayer.views.AnyAdapterView.Companion.asAny
import com.futo.platformplayer.views.adapters.AnyAdapter

class SlideUpMenuRecycler<T : Any, VType : AnyAdapter.AnyViewHolder<T>> : LinearLayout {

    private lateinit var recyclerView: RecyclerView;
    private val adapter: AnyAdapterView<T, VType>?;

    var groupTag: Any? = null;

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        init();
        adapter = null;
    }

    constructor(context: Context, tag: Any, creation: (RecyclerView)->AnyAdapterView<T, VType>) : super(context){
        init();
        groupTag = tag;
        adapter = creation(recyclerView);
    }

    private fun init(){
        LayoutInflater.from(context).inflate(R.layout.overlay_slide_up_menu_recycler, this, true);

        recyclerView = findViewById(R.id.slide_up_menu_recycler);
    }
}