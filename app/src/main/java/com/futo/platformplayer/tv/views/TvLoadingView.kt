package com.futo.platformplayer.tv.views

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.views.LoaderView

internal class TvLoadingView(context: Context) : LinearLayout(context) {
    private val loader = LoaderView(context)

    init {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        orientation = VERTICAL
        gravity = Gravity.CENTER
        minimumWidth = context.dp(144)
        setPadding(context.dp(24), context.dp(20), context.dp(24), context.dp(20))
        background = GradientDrawable().apply {
            setColor(0xF21F1F1F.toInt())
            cornerRadius = context.dp(16).toFloat()
            setStroke(context.dp(1), 0xFF444444.toInt())
        }
        addView(loader, LayoutParams(context.dp(48), context.dp(48)))
        addView(TvUi.text(context, context.getString(R.string.tv_loading), 16f),
            LayoutParams(-2, -2).apply { topMargin = context.dp(16) })
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) loader.start() else loader.stop()
    }

    override fun onDetachedFromWindow() {
        loader.stop()
        super.onDetachedFromWindow()
    }
}
