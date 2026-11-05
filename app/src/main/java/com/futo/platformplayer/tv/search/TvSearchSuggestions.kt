package com.futo.platformplayer.tv.search

import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import com.futo.platformplayer.R
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp
import com.futo.platformplayer.tv.views.TvUi.lines

internal class TvSearchSuggestions(
    context: Context,
    private val search: (String) -> Unit,
    private val keyboard: () -> Unit,
    private val emptyTitle: String? = null,
    private val emptyHint: String? = null,
    private val remove: (String) -> Unit
) : ScrollView(context) {
    private val content = TvUi.column(context)

    init {
        id = R.id.tv_search_suggestions
        isVerticalScrollBarEnabled = false
        clipToPadding = false
        setPadding(context.dp(4), context.dp(24), context.dp(4), context.dp(16))
        addView(content)
    }

    fun focusFirst() = (content.getChildAt(1) as? LinearLayout)?.getChildAt(0)?.requestFocus() == true

    fun show(values: List<String>, historical: Boolean, restoreIndex: Int? = null) {
        content.removeAllViews()
        if (values.isEmpty()) {
            content.addView(
                TvUi.heading(context, emptyTitle ?: context.getString(R.string.tv_search_discover)).apply {
                    textSize = 24f
                    setPadding(context.dp(8), context.dp(36), 0, context.dp(12))
                })
            content.addView(
                TvUi.text(context, emptyHint ?: context.getString(R.string.tv_search_discover_hint), 16f, true).apply {
                    setPadding(context.dp(8), 0, 0, 0)
                })
            return
        }
        content.addView(
            TvUi.text(
                context,
                context.getString(if (historical) R.string.tv_search_recent else R.string.tv_search_suggestions),
                15f,
                true).apply {
                setPadding(context.dp(14), 0, 0, context.dp(10))
            })
        values.take(8).forEachIndexed { index, value ->
            val row = TvUi.row(context)
            row.addView(
                TvUi.button(context, value) { search(value) }.apply {
                    textSize = 18f
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    minHeight = context.dp(44)
                    lines(1)
                    setBackgroundResource(R.drawable.tv_channel_tab)
                    val icon = ContextCompat.getDrawable(
                        context, if (historical) R.drawable.ic_history else R.drawable.ic_search)?.apply {
                        setBounds(0, 0, context.dp(22), context.dp(22))
                    }
                    setCompoundDrawablesRelative(icon, null, null, null)
                    compoundDrawableTintList = ContextCompat.getColorStateList(context, R.color.tv_button_text)
                    compoundDrawablePadding = context.dp(16)
                    tag = value
                    setOnKeyListener { _, key, event ->
                        if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                            if (event.action == KeyEvent.ACTION_DOWN) keyboard()
                            true
                        } else false
                    }
                },
                LinearLayout.LayoutParams(0, context.dp(48), 1f).apply {
                    bottomMargin = context.dp(8)
                    marginEnd = context.dp(10)
                })
            if (historical)
                row.addView(
                    TvUi.iconButton(
                        context,
                        R.drawable.ic_close_thin,
                        context.getString(R.string.tv_search_remove_recent, value)) {
                        remove(value)
                    }.apply {
                        tag = "remove:$value"
                    },
                    LinearLayout.LayoutParams(context.dp(40), context.dp(40)).apply { bottomMargin = context.dp(8) })
            content.addView(row, LinearLayout.LayoutParams(-1, -2))
            if (restoreIndex == index) row.getChildAt(0).requestFocus()
        }
    }
}
