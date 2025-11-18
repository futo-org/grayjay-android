package com.futo.platformplayer.views.buttons

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R

class StandardButton : LinearLayout {
    private val _root: LinearLayout;
    private val _text: TextView;

    constructor(context: Context, text: String, onClick: ()->Unit) : super(context) {
        inflate(context, R.layout.view_button_standard, this);
        _root = findViewById(R.id.root);
        _text = findViewById(R.id.text_button);
        _text.text = text;
        _root.setOnClickListener {
            onClick.invoke();
        }
    }

    fun withPrimaryBackground(): StandardButton {
        _root.setBackgroundResource(R.drawable.background_button_primary)
        return this;
    }
    fun withAccentBackground(): StandardButton {
        _root.setBackgroundResource(R.drawable.background_button_accent)
        return this;
    }
    fun withBackground(id: Int): StandardButton {
        _root.setBackgroundResource(id);
        return this;
    }
}