package com.futo.platformplayer.tv.sync

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp

internal class TvSyncPairingDialog(context: Context, name: String, key: String, private val reply: (Boolean) -> Unit) : Dialog(context) {
    private var answered = false

    init {
        val content = TvUi.column(context).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(context.dp(28), context.dp(28), context.dp(28), context.dp(24))
            background = shape(0xFF1B1D20.toInt(), 20)
        }
        val header = TvUi.row(context)
        header.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_device)
            setColorFilter(Color.WHITE)
            setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
            background = shape(0xFF30343A.toInt(), 14)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(48), context.dp(48)).apply { marginEnd = context.dp(16) })
        header.addView(TvUi.column(context).apply {
            addView(TvUi.heading(context, context.getString(R.string.tv_sync_approve_title)))
            addView(TvUi.text(context, name, 14f, true).apply { setPadding(0, context.dp(6), 0, 0) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(header)
        content.addView(TvUi.text(context, context.getString(R.string.tv_install_sync_shares), 15f).apply {
            setLineSpacing(context.dp(3).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(24) })
        val identity = TvUi.column(context).apply {
            setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
            background = shape(0xFF25282D.toInt(), 10)
            addView(TvUi.text(context, context.getString(R.string.tv_install_sync_identity), 11f, true))
            addView(TvUi.text(context, key, 11f).apply {
                typeface = Typeface.MONOSPACE
                setPadding(0, context.dp(6), 0, 0)
            })
        }
        content.addView(identity, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(20) })
        content.addView(TvUi.text(context, context.getString(R.string.tv_sync_approve_hint), 13f, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(16); bottomMargin = context.dp(12) })
        content.addView(action(context.getString(R.string.tv_sync_allow), true, R.id.tv_dialog_confirm))
        content.addView(action(context.getString(R.string.cancel), false, R.id.tv_dialog_cancel))
        setContentView(content)
        setOnDismissListener { answer(false) }
    }

    override fun onStart() {
        super.onStart()
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(.72f)
            setGravity(Gravity.CENTER)
            setLayout(minOf(context.dp(480), context.resources.displayMetrics.widthPixels - context.dp(64)), -2)
        }
        findViewById<View>(R.id.tv_dialog_cancel).requestFocus()
    }

    private fun action(label: String, allow: Boolean, viewId: Int): View = TvUi.button(context, label) {
        answer(allow)
        dismiss()
    }.apply {
        id = viewId
        textSize = 16f
        minHeight = context.dp(48)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(Color.WHITE, 12))
            addState(intArrayOf(android.R.attr.state_pressed), shape(0xFFDCEEFF.toInt(), 12))
            addState(intArrayOf(), shape(if (allow) 0xFF087FC1.toInt() else 0xFF30343A.toInt(), 12))
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(12) }
    }

    private fun answer(allow: Boolean) {
        if (!answered) {
            answered = true
            reply(allow)
        }
    }

    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = context.dp(radius).toFloat()
    }
}
