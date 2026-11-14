package com.futo.platformplayer.tv.playlists

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.futo.platformplayer.R
import com.futo.platformplayer.tv.search.TvSearchKeyboard
import com.futo.platformplayer.tv.views.TvUi
import com.futo.platformplayer.tv.views.TvUi.dp

internal class TvPlaylistNameDialog(
    context: Context,
    title: String,
    name: String,
    action: String,
    label: String = context.getString(R.string.tv_playlist_name),
    hint: String = label,
    emptyError: String = context.getString(R.string.tv_playlist_name_required),
    allowUppercase: Boolean = false,
    submit: (String) -> Unit
) : Dialog(context) {
    private val input = EditText(context).apply {
        id = R.id.tv_playlist_name
        setTextColor(Color.WHITE)
        setHintTextColor(TvUi.muted)
        textSize = 23f
        this.hint = hint
        contentDescription = label
        setSingleLine(true)
        showSoftInputOnFocus = false
        imeOptions = EditorInfo.IME_ACTION_DONE
        setText(name)
        selectAll()
    }
    private val error = TvUi.text(context, "", 14f, true).apply {
        id = R.id.tv_playlist_name_error
        visibility = View.GONE
    }
    private val keyboard: TvSearchKeyboard
    private val save: TextView
    private var busy = false

    init {
        fun save() {
            if (busy) return
            val value = input.text.toString().trim()
            if (value.isEmpty()) showError(emptyError) else submit(value)
        }
        fun insert(value: String) {
            if (busy) return
            val start = input.selectionStart.coerceAtLeast(0)
            val end = input.selectionEnd.coerceAtLeast(0)
            input.text.replace(minOf(start, end), maxOf(start, end), value)
            input.setSelection(minOf(start, end) + value.length)
        }
        fun delete() {
            if (busy) return
            val start = input.selectionStart.coerceAtLeast(0)
            val end = input.selectionEnd.coerceAtLeast(0)
            if (start != end) insert("")
            else if (start > 0) {
                val previous = Character.offsetByCodePoints(input.text, start, -1)
                input.text.delete(previous, start)
                input.setSelection(previous)
            }
        }
        val root = TvUi.column(context).apply {
            setPadding(context.dp(28), context.dp(24), context.dp(28), context.dp(24))
            background = GradientDrawable().apply {
                setColor(TvUi.background)
                cornerRadius = context.dp(18).toFloat()
            }
        }
        val heading = TvUi.row(context)
        heading.addView(TvUi.heading(context, title), LinearLayout.LayoutParams(0, -2, 1f))
        val close = TvUi.iconButton(context, R.drawable.ic_close_thin, context.getString(R.string.tv_close), ::dismiss)
        heading.addView(close)
        root.addView(heading)
        keyboard = TvSearchKeyboard(context, ::insert, ::delete, ::save, { close.requestFocus() }, { input.requestFocus() }, allowUppercase = allowUppercase) {
            event ->
            when {
                busy -> true
                event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (event.action == KeyEvent.ACTION_UP) save()
                    true
                }
                else -> input.dispatchKeyEvent(event)
            }
        }
        save = keyboard.findViewById<TextView>(R.id.tv_search_keyboard_submit).apply { text = action }
        val row = TvUi.row(context).apply {
            gravity = Gravity.TOP
            setPadding(0, context.dp(24), 0, 0)
        }
        row.addView(keyboard, LinearLayout.LayoutParams(context.dp(292), -2).apply { marginEnd = context.dp(32) })
        val editor = TvUi.column(context)
        editor.addView(TvUi.text(context, label, 14f, true))
        editor.addView(input, LinearLayout.LayoutParams(-1, context.dp(64)))
        editor.addView(error, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(12) })
        editor.addView(
            TvUi.button(context, context.getString(R.string.clear)) {
                if (!busy) {
                    input.setText("")
                    keyboard.focusLast()
                }
            }.apply { id = R.id.tv_playlist_name_clear })
        row.addView(editor, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)
        input.doAfterTextChanged { error.visibility = View.GONE }
        input.setOnClickListener { keyboard.focusLast() }
        input.setOnEditorActionListener { _, _, _ ->
            save()
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
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(context.dp(800), -2)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
    }

    fun setSaving(saving: Boolean) {
        busy = saving
        input.isEnabled = !saving
        save.isEnabled = !saving
        save.alpha = if (saving) .4f else 1f
    }

    fun showError(message: String) {
        error.text = message
        error.visibility = View.VISIBLE
    }
}
