package com.futo.platformplayer.views.others

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1

class ToggleTagView : LinearLayout {
    private val _root: FrameLayout;
    private val _textTag: TextView;
    private var _text: String = "";

    var isActive: Boolean = false
        private set;

    var onClick = Event1<Boolean>();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_toggle_tag, this, true);
        _root = findViewById(R.id.root);
        _textTag = findViewById(R.id.text_tag);
        _root.setOnClickListener { setToggle(!isActive); onClick.emit(isActive); }
    }

    fun setToggle(isActive: Boolean) {
        this.isActive = isActive;
        if(isActive) {
            _root.setBackgroundResource(R.drawable.background_pill_toggled);
            _textTag.alpha = 1f;
        }
        else {
            _root.setBackgroundResource(R.drawable.background_pill_untoggled);
            _textTag.alpha = 0.5f;
        }
    }

    fun setInfo(text: String, isActive: Boolean) {
        _text = text;
        _textTag.text = text;
        setToggle(isActive);
    }
}