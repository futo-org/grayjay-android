package com.futo.platformplayer.views.containers

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet.Constraint
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1

class DoubleTapLayout : ConstraintLayout {
    private var _detector : GestureDetector? = null;

    val onDoubleTap = Event1<MotionEvent>();

    constructor(context: Context) : super(context) {
        init();
    }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init();
    }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init();
    }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init();
    }

    fun init(){
        if(!isInEditMode) {
            _detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(p0: MotionEvent): Boolean {
                    onDoubleTap.emit(p0);
                    return true;
                }
            });
        }
    }
}