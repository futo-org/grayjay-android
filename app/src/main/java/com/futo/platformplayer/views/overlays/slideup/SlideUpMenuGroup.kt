package com.futo.platformplayer.views.overlays.slideup

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R

class SlideUpMenuGroup : LinearLayout {

    private lateinit var title: TextView;
    private lateinit var itemContainer: LinearLayout;
    private var parentClickListener: (()->Unit)? = null;
    private val items: List<SlideUpMenuItem>;

    var groupTag: Any? = null;

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        init();
        this.items = listOf();
    }

    constructor(context: Context, titleText: String, tag: Any, items: List<SlideUpMenuItem>) : super(context){
        init();
        title.text = titleText;
        groupTag = tag;
        this.items = items.toList();
        addItems(items);
    }

    constructor(context: Context, titleText: String, tag: Any, vararg items: SlideUpMenuItem)
        : this(context, titleText, tag, items.asList())

    private fun init(){
        LayoutInflater.from(context).inflate(R.layout.overlay_slide_up_menu_group, this, true);

        title = findViewById(R.id.slide_up_menu_group_title);
        itemContainer = findViewById(R.id.slide_up_menu_group_items);
    }

    fun selectItem(obj: Any?): Boolean {
        var didSelect = false;
        for(item in items) {
            item.setOptionSelected(item.itemTag == obj);
            didSelect =  didSelect || item.itemTag == obj;
        }
        return didSelect;
    }

    private fun addItems(items: List<out SlideUpMenuItem>) {
        for (item in items) {
            item.setParentClickListener { parentClickListener?.invoke() }
            itemContainer.addView(item);
        }
    }

    fun setParentClickListener(listener: (()->Unit)?) {
        parentClickListener = listener;
    }
}