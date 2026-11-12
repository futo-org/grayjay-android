package com.futo.platformplayer.views.behavior

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.futo.platformplayer.logging.Logger

class SafeTextView : AppCompatTextView {
    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    override fun performLongClick(): Boolean {
        try {
            return super.performLongClick()
        } catch (e: IllegalStateException) {
            Logger.w(TAG, "Swallowed exception", e)
            return false
        }
    }

    companion object {
        private const val TAG = "SafeTextView"
    }
}
