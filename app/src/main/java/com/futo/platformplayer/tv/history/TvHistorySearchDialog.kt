package com.futo.platformplayer.tv.history

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.tv.search.TvSearchKeyboard
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp

internal class TvHistorySearchDialog(context: Context, query: String, submit: (String) -> Unit) : Dialog(context) {
    private val input = EditText(context).apply {
        id = R.id.tv_history_search_input
        setTextColor(Color.WHITE)
        setHintTextColor(0xFFAAAAAA.toInt())
        textSize = 23f
        hint = context.getString(R.string.tv_history_search_hint)
        setSingleLine(true)
        showSoftInputOnFocus = false
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        setText(query)
        setSelection(text.length)
    }
    private lateinit var keyboard: TvSearchKeyboard

    init {
        fun search() {
            submit(input.text.toString().trim())
            dismiss()
        }
        fun insert(value: String) {
            val start = input.selectionStart.coerceAtLeast(0)
            val end = input.selectionEnd.coerceAtLeast(0)
            input.text.replace(minOf(start, end), maxOf(start, end), value)
        }
        fun delete() {
            val start = input.selectionStart.coerceAtLeast(0)
            val end = input.selectionEnd.coerceAtLeast(0)
            if (start != end) insert("")
            else if (start > 0) input.text.delete(Character.offsetByCodePoints(input.text, start, -1), start)
        }
        val root = TvUi.column(context).apply {
            setPadding(context.dp(28), context.dp(24), context.dp(28), context.dp(24))
            setBackgroundColor(TvUi.background)
        }
        val heading = TvUi.row(context)
        heading.addView(
            TvUi.heading(context, context.getString(R.string.tv_history_search)), LinearLayout.LayoutParams(0, -2, 1f))
        val close = TvUi.iconButton(context, R.drawable.ic_close_thin, context.getString(R.string.tv_close), ::dismiss)
        heading.addView(close)
        root.addView(heading)
        val row = TvUi.row(context).apply {
            gravity = Gravity.TOP
            setPadding(0, context.dp(24), 0, 0)
        }
        val editor = TvUi.column(context)
        val clear = TvUi.button(context, context.getString(R.string.tv_search_clear)) {
            input.setText("")
            keyboard.focusLast()
        }.apply { id = R.id.tv_history_search_clear }
        keyboard = TvSearchKeyboard(
            context, ::insert, ::delete, ::search, { close.requestFocus() }, { input.requestFocus() }) { event ->
            if (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (event.action == KeyEvent.ACTION_UP) search()
                true
            } else input.dispatchKeyEvent(event)
        }
        row.addView(keyboard, LinearLayout.LayoutParams(context.dp(292), -2).apply { marginEnd = context.dp(32) })
        editor.addView(input, LinearLayout.LayoutParams(-1, context.dp(64)))
        editor.addView(
            TvUi.text(context, context.getString(R.string.tv_history_search_hint), 15f, true).apply {
                setPadding(0, context.dp(12), 0, context.dp(24))
            })
        editor.addView(clear)
        row.addView(editor, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)
        input.setOnClickListener { keyboard.focusLast() }
        input.setOnEditorActionListener { _, _, _ ->
            search()
            true
        }
        input.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_DPAD_LEFT && input.selectionStart == 0) {
                if (event.action == KeyEvent.ACTION_DOWN) keyboard.focusLast()
                true
            } else false
        }
        setContentView(root)
        keyboard.focusLast()
    }

    override fun onStart() {
        super.onStart()
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(context.dp(800), -2)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
    }
}
