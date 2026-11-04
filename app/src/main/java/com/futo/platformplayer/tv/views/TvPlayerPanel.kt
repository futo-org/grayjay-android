package com.futo.platformplayer.tv.views

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.FocusFinder
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.futo.platformplayer.R
import com.futo.platformplayer.tv.views.TvUi.dp

internal open class TvPlayerPanel(context: Context, private val close: () -> Unit) : LinearLayout(context) {
    private val title = TvUi.heading(context, "").apply { textSize = 20f }
    private val back = TvUi.iconButton(context, R.drawable.tv_ic_back, context.getString(R.string.tv_back)) {
        if (!back()) close()
    }
    private var pageScroll: ScrollView? = null

    val body = FrameLayout(context)
    var onBack: (() -> Boolean)? = null

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            setColor(Color.rgb(24, 24, 24))
            cornerRadius = context.dp(18).toFloat()
        }
        clipToOutline = true
        val header = TvUi.row(context).apply { setPadding(context.dp(16), context.dp(18), context.dp(16), context.dp(16)) }
        header.addView(back, LayoutParams(context.dp(32), context.dp(32)).apply { marginEnd = context.dp(8) })
        header.addView(title, LayoutParams(0, -2, 1f))
        header.addView(
            TvUi.iconButton(context, R.drawable.ic_close_thin, context.getString(R.string.tv_close), close).apply {
                id = R.id.tv_panel_close
            })
        addView(header)
        addView(body, LayoutParams(-1, 0, 1f))
    }

    override fun focusSearch(focused: View, direction: Int): View =
        FocusFinder.getInstance().findNextFocus(this, focused, direction) ?: focused

    fun useScrollingPageLayout() {
        if (pageScroll != null) return
        background = null
        clipToOutline = false
        val header = getChildAt(0)
        removeAllViews()
        header.setPadding(context.dp(16), 0, context.dp(16), context.dp(24))
        val page = TvUi.column(context).apply {
            addView(header)
            addView(body, LayoutParams(-1, -2))
        }
        pageScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(page)
        }
        addView(pageScroll, LayoutParams(-1, -1))
    }

    fun title(value: String, canGoBack: Boolean = false) {
        title.text = value
        back.visibility = if (canGoBack) View.VISIBLE else View.GONE
    }

    open fun back(): Boolean = onBack?.invoke() ?: false

    fun menu(): LinearLayout {
        body.removeAllViews()
        pageScroll?.scrollTo(0, 0)
        val column = TvUi.column(context).apply { setPadding(context.dp(12), 0, context.dp(12), context.dp(20)) }
        if (pageScroll != null) body.addView(column, FrameLayout.LayoutParams(-1, -2))
        else
            body.addView(
                ScrollView(context).apply {
                    isVerticalScrollBarEnabled = false
                    addView(column)
                },
                FrameLayout.LayoutParams(-1, -1))
        return column
    }

    fun option(label: String, value: String? = null, checked: Boolean = false, action: () -> Unit): View {
        val row = TvUi.row(context).apply {
            id = View.generateViewId()
            isFocusable = true
            isClickable = true
            setPadding(context.dp(16), context.dp(14), context.dp(16), context.dp(14))
            setBackgroundResource(R.drawable.tv_panel_item)
            setOnClickListener { action() }
            layoutParams = LayoutParams(-1, -2).apply { bottomMargin = context.dp(8) }
        }
        val texts = TvUi.column(context)
        texts.addView(TvUi.text(context, label, 15f))
        if (!value.isNullOrBlank())
            texts.addView(TvUi.text(context, value, 12f, true).apply { setPadding(0, context.dp(6), 0, 0) })
        row.addView(texts, LayoutParams(0, -2, 1f))
        if (checked)
            row.addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_check)
                    setColorFilter(Color.WHITE)
                },
                LayoutParams(context.dp(20), context.dp(20)).apply { marginStart = context.dp(12) })
        row.contentDescription = listOfNotNull(label, value).joinToString(", ")
        return row
    }

    fun message(title: String, description: String = "", retry: (() -> Unit)? = null) {
        body.removeAllViews()
        pageScroll?.scrollTo(0, 0)
        val column = TvUi.column(context).apply {
            gravity = Gravity.CENTER
            if (pageScroll != null) minimumHeight = context.dp(280)
            setPadding(context.dp(24), context.dp(20), context.dp(24), context.dp(32))
        }
        column.addView(
            TvUi.text(context, title, 18f).apply {
                id = R.id.tv_panel_message
                gravity = Gravity.CENTER
            })
        if (description.isNotEmpty())
            column.addView(
                TvUi.text(context, description, 14f, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, context.dp(12), 0, context.dp(16))
                })
        retry?.let { column.addView(TvUi.button(context, context.getString(R.string.tv_retry), it)) }
        body.addView(column, FrameLayout.LayoutParams(-1, if (pageScroll == null) -1 else -2))
        if (retry != null) column.requestFocus()
    }
}
