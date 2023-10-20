package com.futo.platformplayer.views.buttons

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R

class BigButtonGroup : LinearLayout {
    private val _header: TextView;
    private val _buttons: LinearLayout;

    constructor(context: Context) : super(context) {
        inflate(context, R.layout.big_button_group, this);
        _header = findViewById(R.id.header_title);
        _buttons = findViewById(R.id.buttons);
    }
    constructor(context: Context, header: String, vararg buttons: BigButton?) : super(context) {
        inflate(context, R.layout.big_button_group, this);
        _header = findViewById(R.id.header_title);
        _buttons = findViewById(R.id.buttons);

        _header.text = header;
        for(button in buttons.filterNotNull())
            _buttons.addView(button);
    }

    fun setText(text: String) {
        _header.text = text;
    }
    fun setButtons(vararg buttons: BigButton) {
        _buttons.removeAllViews();
        for(button in buttons)
            _buttons.addView(button);
    }
}