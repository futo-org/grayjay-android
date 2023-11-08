package com.futo.platformplayer.views.others

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1

class Toggle : AppCompatImageView {
    var value: Boolean = false
        private set;

    val onValueChanged = Event1<Boolean>();
    private var _currentDrawable: AnimatedVectorDrawableCompat? = null;

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        isClickable = true;
        setOnClickListener {
            setValue(!value);
            onValueChanged.emit(value);
        };

        setImageResource(R.drawable.toggle_disabled);

        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.Toggle, 0, 0);
        val toggleEnabled = attrArr.getBoolean(R.styleable.Toggle_toggleEnabled, false);
        setValue(toggleEnabled, false);
        scaleType = ScaleType.FIT_CENTER;
    }

    fun setValue(v: Boolean, animated: Boolean = true, withEvent: Boolean = false) {
        if (value == v) {
            return;
        }

        value = v;

        _currentDrawable?.stop();
        if (animated) {
            _currentDrawable = AnimatedVectorDrawableCompat.create(context, if (v) R.drawable.toggle_animated else R.drawable.toggle_animated_reverse);
            setImageDrawable(_currentDrawable);
            _currentDrawable?.start();
        } else {
            setImageResource(if (v) R.drawable.toggle_enabled else R.drawable.toggle_disabled);
        }

        if(withEvent)
            onValueChanged.emit(value);
    }
}