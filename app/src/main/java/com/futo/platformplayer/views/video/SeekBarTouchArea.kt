package com.futo.platformplayer.views.video

import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import com.futo.platformplayer.dp


class SeekBarTouchDelegate(val bounds: Rect, val seekBar: View) : TouchDelegate(bounds, seekBar) {
    private var targeted = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN)
            targeted = bounds.contains(event.x.toInt(), event.y.toInt())

        if (!targeted)
            return false

        val forwarded = MotionEvent.obtain(event)
        forwarded.offsetLocation(-seekBar.left.toFloat(), -seekBar.top.toFloat())

        if (event.actionMasked == MotionEvent.ACTION_DOWN)
            forwarded.setLocation(forwarded.x, seekBar.height / 2f)

        val handled = seekBar.dispatchTouchEvent(forwarded)
        forwarded.recycle()

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)
            targeted = false
        return handled
    }

    companion object {
        fun expandSeekBarTouchArea(seekBar: View) {
            val parent = seekBar.parent as View
            val targetHeight = 60.dp(seekBar.resources)

            fun updateTouchArea() {
                val bounds = Rect()
                seekBar.getHitRect(bounds)
                bounds.top -= (targetHeight - bounds.height()).coerceAtLeast(0)
                parent.touchDelegate = SeekBarTouchDelegate(bounds, seekBar)
            }

            seekBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateTouchArea() }
            seekBar.post { updateTouchArea() }
        }
    }
}