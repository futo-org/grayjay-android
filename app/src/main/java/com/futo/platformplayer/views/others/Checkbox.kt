package com.futo.platformplayer.views.others

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1

class Checkbox : AppCompatImageView {
    var value: Boolean = false
        set(v) {
            field = v;
            if (v) {
                setImageResource(R.drawable.ic_checkbox_checked);
            } else {
                setImageResource(R.drawable.ic_checkbox_unchecked);
            }
        };
    val onValueChanged = Event1<Boolean>();

    constructor(context : Context, attrs : AttributeSet) : super(context, attrs) {
        setImageResource(R.drawable.ic_checkbox_unchecked);

        isClickable = true;
        setOnClickListener {
            value = !value;
            onValueChanged.emit(value);
        };
    }
}