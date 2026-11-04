package com.futo.platformplayer.tv.search

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp

internal class TvSearchKeyboard(
    context: Context,
    private val insert: (String) -> Unit,
    backspace: () -> Unit,
    search: () -> Unit,
    private val navigation: () -> Unit,
    private val suggestions: () -> Unit,
    private val physicalKey: (KeyEvent) -> Boolean
) : LinearLayout(context) {
    private val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".map { it.toString() }
    private val symbols = listOf(
        ".", ",", "?", "!", "'", "\"",
        "-", "_", ":", ";", "/", "\\",
        "@", "#", "$", "%", "&", "+",
        "=", "(", ")", "[", "]", "{",
        "}", "<", ">", "*", "~", "`",
        "|", "^", "€", "£", "¥", "…"
    )
    private val keys = TvUi.column(context)
    private var showingSymbols = false
    private var lastFocused: View? = null

    init {
        id = R.id.tv_search_keyboard
        orientation = VERTICAL
        setPadding(context.dp(2), context.dp(2), context.dp(2), context.dp(2))
        addView(keys)
        showKeys()
        val editing = TvUi.row(context)
        editing.addView(
            TvUi.button(context, context.getString(R.string.tv_search_space)) { insert(" ") }.apply {
                id = R.id.tv_search_space
                textSize = 16f
                edge(this, first = true)
            },
            keyLayout(3f))
        editing.addView(
            TvUi.iconButton(
                context, R.drawable.tv_ic_backspace, context.getString(R.string.tv_search_delete), backspace).apply {
                id = R.id.tv_search_delete
                setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
                edge(this, last = true)
            },
            keyLayout(3f))
        addView(editing, LayoutParams(-1, -2).apply { topMargin = context.dp(12) })
        val actions = TvUi.row(context)
        actions.addView(
            TvUi.button(context, "#+=") {
                showingSymbols = !showingSymbols
                showKeys()
                actions.findViewById<TextView>(R.id.tv_search_symbols).apply {
                    text = if (showingSymbols) "ABC" else "#+="
                    contentDescription = context.getString(
                        if (showingSymbols) R.string.tv_search_letters else R.string.tv_search_symbols)
                }
            }.apply {
                id = R.id.tv_search_symbols
                contentDescription = context.getString(R.string.tv_search_symbols)
                textSize = 16f
                edge(this, first = true, bottom = true)
            },
            keyLayout(2f))
        actions.addView(
            TvUi.button(context, context.getString(R.string.tv_search), search).apply {
                id = R.id.tv_search_keyboard_submit
                textSize = 16f
                edge(this, last = true, bottom = true)
            },
            keyLayout(4f))
        addView(actions)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.isPrintingKey ||
            event.keyCode in
                listOf(
                    KeyEvent.KEYCODE_SPACE,
                    KeyEvent.KEYCODE_DEL,
                    KeyEvent.KEYCODE_FORWARD_DEL,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER))
            return physicalKey(event)
        return super.dispatchKeyEvent(event)
    }

    fun focusLast() =
        (lastFocused?.takeIf { it.isShown } ?: keys.getChildAt(0)?.let { it as LinearLayout }?.getChildAt(0))
            ?.requestFocus() == true

    private fun showKeys() {
        keys.removeAllViews()
        (if (showingSymbols) symbols else letters).chunked(6).forEachIndexed { rowIndex, values ->
            val row = TvUi.row(context)
            values.forEachIndexed { column, value ->
                row.addView(
                    TvUi.button(context, value) { insert(if (showingSymbols) value else value.lowercase()) }.apply {
                        tag = "key:$value"
                        textSize = 20f
                        setPadding(0, 0, 0, 0)
                        setBackgroundResource(R.drawable.tv_channel_tab)
                        edge(this, first = column == 0, last = column == 5, top = rowIndex == 0)
                    },
                    keyLayout())
            }
            keys.addView(row)
        }
    }

    private fun keyLayout(weight: Float = 1f) =
        LayoutParams(0, context.dp(44), weight).apply {
            setMargins(context.dp(2), context.dp(2), context.dp(2), context.dp(2))
        }

    private fun edge(
        view: View,
        first: Boolean = false,
        last: Boolean = false,
        top: Boolean = false,
        bottom: Boolean = false
    ) {
        val animate = view.onFocusChangeListener
        view.setOnFocusChangeListener { target, focused ->
            animate?.onFocusChange(target, focused)
            if (focused) lastFocused = target
        }
        view.setOnKeyListener { _, key, event ->
            when {
                key == KeyEvent.KEYCODE_DPAD_LEFT && first -> {
                    if (event.action == KeyEvent.ACTION_DOWN) navigation()
                    true
                }
                key == KeyEvent.KEYCODE_DPAD_RIGHT && last -> {
                    if (event.action == KeyEvent.ACTION_DOWN) suggestions()
                    true
                }
                key == KeyEvent.KEYCODE_DPAD_UP && top -> true
                key == KeyEvent.KEYCODE_DPAD_DOWN && bottom -> true
                else -> false
            }
        }
    }
}
