package com.futo.platformplayer.views.containers

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.motion.widget.MotionLayout
import com.futo.platformplayer.R

class SingleViewTouchableMotionLayout(context: Context, attributeSet: AttributeSet? = null) : MotionLayout(context, attributeSet) {

    private val viewToDetectTouch by lazy {
        findViewById<View>(R.id.touchContainer) //TODO move to Attributes
    }
    private val viewRect = Rect()
    private var touchStarted = false
    private val transitionListenerList = mutableListOf<TransitionListener?>()

    var allowMotion : Boolean = true;

    init {
        addTransitionListener(object : TransitionListener {
            override fun onTransitionChange(p0: MotionLayout?, p1: Int, p2: Int, p3: Float) {
            }

            override fun onTransitionCompleted(p0: MotionLayout?, p1: Int) {
                touchStarted = false
            }

            override fun onTransitionStarted(p0: MotionLayout?, p1: Int, p2: Int) {
            }

            override fun onTransitionTrigger(p0: MotionLayout?, p1: Int, p2: Boolean, p3: Float) {
            }
        })

        super.setTransitionListener(object : TransitionListener {
            override fun onTransitionChange(p0: MotionLayout?, p1: Int, p2: Int, p3: Float) {
                transitionListenerList.filterNotNull()
                    .forEach { it.onTransitionChange(p0, p1, p2, p3) }
            }

            override fun onTransitionCompleted(p0: MotionLayout?, p1: Int) {
                transitionListenerList.filterNotNull()
                    .forEach { it.onTransitionCompleted(p0, p1) }
            }

            override fun onTransitionStarted(p0: MotionLayout?, p1: Int, p2: Int) {
            }

            override fun onTransitionTrigger(p0: MotionLayout?, p1: Int, p2: Boolean, p3: Float) {
            }
        })

        //isInteractionEnabled = false;
    }

    override fun setTransitionListener(listener: TransitionListener?) {
        addTransitionListener(listener)
    }

    override fun addTransitionListener(listener: TransitionListener?) {
        transitionListenerList += listener
    }

    //This always triggers, workaround calling super.onTouchEvent
    //Blocks click events underneath
    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        if(!allowMotion)
            return false;
        if(event != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchStarted = false
                    return super.onTouchEvent(event) && false;
                }
            }
            if (!touchStarted) {
                viewToDetectTouch.getHitRect(viewRect);
                val isInView = viewRect.contains(event.x.toInt(), event.y.toInt());
                touchStarted = isInView
            }
        }
        return touchStarted && super.onTouchEvent(event) && false;
    }


    //Not triggered on its own due to child views, intercept is used instead.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return false;
    }
}