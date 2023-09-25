package com.futo.platformplayer.views.behavior

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0

class TouchInterceptFrameLayout : FrameLayout {
    var shouldInterceptTouches: Boolean = false;
    val onClick = Event0();
    private var _wasDown = false;

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.TouchInterceptFrameLayout, 0, 0);
        shouldInterceptTouches = attrArr.getBoolean(R.styleable.TouchInterceptFrameLayout_shouldInterceptTouches, false);
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (!shouldInterceptTouches) {
            return super.onInterceptTouchEvent(ev);
        }

        if (!_wasDown && ev?.action == MotionEvent.ACTION_DOWN) {
            return true;
        } else if (_wasDown && ev?.action == MotionEvent.ACTION_UP) {
            return true;
        }

        return super.onInterceptTouchEvent(ev);
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        if (!shouldInterceptTouches) {
            return super.onTouchEvent(ev);
        }

        if (!_wasDown && ev?.action == MotionEvent.ACTION_DOWN) {
            _wasDown = true;
            return true;
        } else if (_wasDown && ev?.action == MotionEvent.ACTION_UP) {
            _wasDown = false;
            onClick.emit();
            return true;
        }

        return super.onTouchEvent(ev);
    }

    companion object {
        val TAG = "TouchInterceptFrameLayout";
    }
}