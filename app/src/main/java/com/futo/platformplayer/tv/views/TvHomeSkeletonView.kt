package com.futo.platformplayer.tv.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.tv.views.TvUi.dp

internal class TvHomeSkeletonView(context: Context) : LinearLayout(context) {
    private var pulse: ValueAnimator? = null

    init {
        orientation = VERTICAL
        setPadding(0, context.dp(10), 0, context.dp(6))
        contentDescription = context.getString(R.string.tv_loading)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        repeat(2) {
            val row = TvUi.row(context)
            repeat(3) {
                val card = TvUi.column(context).apply {
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    setPadding(context.dp(3), context.dp(3), context.dp(3), context.dp(8))
                }
                val thumbnail = object : View(context) {
                    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                        val width = MeasureSpec.getSize(widthSpec)
                        setMeasuredDimension(width, width * 9 / 16)
                    }
                }.apply { background = shape(12) }
                card.addView(thumbnail, LayoutParams(-1, -2))
                card.addView(block(), LayoutParams(-1, context.dp(12)).apply {
                    setMargins(context.dp(4), context.dp(12), context.dp(24), 0)
                })
                card.addView(block(), LayoutParams(-1, context.dp(12)).apply {
                    setMargins(context.dp(4), context.dp(7), context.dp(64), 0)
                })
                card.addView(block(), LayoutParams(context.dp(112), context.dp(10)).apply {
                    setMargins(context.dp(4), context.dp(12), 0, 0)
                })
                card.addView(block(), LayoutParams(context.dp(144), context.dp(10)).apply {
                    setMargins(context.dp(4), context.dp(7), 0, 0)
                })
                row.addView(card, LayoutParams(0, -2, 1f).apply {
                    setMargins(context.dp(6), context.dp(6), context.dp(10), context.dp(12))
                })
            }
            addView(row, LayoutParams(-1, -2))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        pulse?.cancel()
        pulse = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimation()
    }

    private fun updateAnimation() {
        if (!isAttachedToWindow || !isShown || windowVisibility != VISIBLE || !ValueAnimator.areAnimatorsEnabled()) {
            pulse?.cancel()
            pulse = null
            alpha = 1f
        } else if (pulse == null) {
            pulse = ValueAnimator.ofFloat(0.5f, 1f).apply {
                duration = 900
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { alpha = it.animatedValue as Float }
                start()
            }
        }
    }

    private fun block() = View(context).apply { background = shape(4) }

    private fun shape(radius: Int) = GradientDrawable().apply {
        setColor(Color.rgb(46, 46, 46))
        cornerRadius = context.dp(radius).toFloat()
    }
}
