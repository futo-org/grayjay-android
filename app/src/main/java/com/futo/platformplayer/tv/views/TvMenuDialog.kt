package com.futo.platformplayer.tv.views

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import com.futo.platformplayer.R
import com.futo.platformplayer.tv.views.TvUi.dp

internal class TvMenuDialog(context: Context) : Dialog(context) {
    val panel = TvPlayerPanel(context, ::dismiss)

    init {
        setContentView(panel)
    }

    override fun onStart() {
        super.onStart()
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.END or Gravity.CENTER_VERTICAL)
            setLayout(context.dp(400), context.resources.displayMetrics.heightPixels - context.dp(40))
            attributes = attributes.apply { x = context.dp(20) }
        }
    }

    @Deprecated("Uses the app's existing back dispatch configuration")
    override fun onBackPressed() {
        if (!panel.back()) super.onBackPressed()
    }

    fun confirm(title: String, description: String, action: String, confirm: () -> Unit) {
        panel.title(title)
        val menu = panel.menu()
        menu.addView(
            TvUi.text(context, description, 16f).apply {
                setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(24))
                isFocusable = true
                setBackgroundResource(R.drawable.tv_channel_tab)
            })
        menu.addView(
            TvUi.button(context, context.getString(R.string.cancel), ::dismiss).apply { id = R.id.tv_dialog_cancel })
        menu.addView(
            TvUi.button(context, action) {
                dismiss()
                confirm()
            }.apply { id = R.id.tv_dialog_confirm })
        menu.findViewById<View>(R.id.tv_dialog_cancel).requestFocus()
    }
}
