package com.futo.platformplayer.views.overlays.slideup

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R

class SlideUpMenuTitle : LinearLayout {
    private val _title: TextView;

    constructor(context: Context, attrs: AttributeSet? = null): super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.overlay_slide_up_menu_title, this, true);

        _title = findViewById(R.id.slide_up_menu_group_title);
    }

    fun setTitle(title: String) {
        _title.text = title;
    }
}