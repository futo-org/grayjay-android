package com.futo.platformplayer.tv.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.futo.platformplayer.R
import com.futo.platformplayer.tv.views.TvUi.dp

internal class TvNavigationRail(context: Context, private val select: (Int) -> Unit) : LinearLayout(context) {
    private val buttons = mutableListOf<LinearLayout>()
    private val labels = mutableListOf<View>()
    private var selected = 0
    private val tabOrder = listOf(0, 1, 2, 3, 7, 5, 4, 6)
    private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, focused -> expand(contains(focused)) }

    init {
        orientation = VERTICAL
        setPadding(context.dp(16), context.dp(25), context.dp(16), context.dp(24))
        val brand = TvUi.row(context)
        brand.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.ic_launcher_monochrome)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
            },
            LayoutParams(context.dp(48), context.dp(36)))
        val wordmark = TvUi.text(context, "Grayjay", 21f).apply {
            setTypeface(typeface, Typeface.BOLD)
            visibility = GONE
        }
        labels.add(wordmark)
        brand.addView(wordmark)
        addView(brand, LayoutParams(-1, context.dp(58)))
        val titles = listOf(
            R.string.tv_home,
            R.string.tv_search,
            R.string.tv_subscriptions,
            R.string.tv_library,
            R.string.playlists,
            R.string.history,
            R.string.tv_sources,
            R.string.sync)
        val icons = listOf(
            R.drawable.ic_home,
            R.drawable.ic_search,
            R.drawable.ic_subscriptions,
            R.drawable.ic_schedule,
            R.drawable.ic_playlist,
            R.drawable.ic_history,
            R.drawable.ic_sources,
            R.drawable.ic_update)
        val ids = listOf(
            R.id.tv_nav_home,
            R.id.tv_nav_search,
            R.id.tv_nav_subscriptions,
            R.id.tv_nav_library,
            R.id.tv_nav_playlists,
            R.id.tv_nav_history,
            R.id.tv_nav_sources,
            R.id.tv_nav_sync)
        titles.forEachIndexed { index, title ->
            val row = TvUi.row(context).apply {
                id = ids[index]
                isFocusable = true
                isClickable = true
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                contentDescription = context.getString(title)
                setBackgroundResource(R.drawable.tv_nav_focus)
                setOnClickListener { select(tabOrder[index]) }
            }
            val icon = ImageView(context).apply {
                setImageResource(icons[index])
                imageTintList = ContextCompat.getColorStateList(context, R.color.tv_nav_text)
                isDuplicateParentStateEnabled = true
                setPadding(context.dp(13), context.dp(13), context.dp(13), context.dp(13))
            }
            row.addView(icon, LayoutParams(context.dp(48), context.dp(48)))
            val label = TvUi.text(context, context.getString(title), 15f).apply {
                isDuplicateParentStateEnabled = true
                setTextColor(ContextCompat.getColorStateList(context, R.color.tv_nav_text))
                setSingleLine(true)
                visibility = GONE
            }
            labels.add(label)
            row.addView(label, LayoutParams(-1, -2))
            addView(row, LayoutParams(-1, context.dp(48)).apply { bottomMargin = context.dp(4) })
            buttons.add(row)
        }
        expand(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener)
        super.onDetachedFromWindow()
    }

    fun selectTab(index: Int) {
        selected = index
        buttons.forEachIndexed { i, view -> view.isSelected = tabOrder[i] == index }
    }

    fun focusSelected() {
        buttons[tabOrder.indexOf(selected)].requestFocus()
    }

    fun contains(view: View?): Boolean {
        var current = view
        while (current != null) {
            if (current == this) return true
            current = current.parent as? View
        }
        return false
    }

    private fun expand(expanded: Boolean) {
        labels.forEach { it.visibility = if (expanded) VISIBLE else GONE }
        layoutParams = (layoutParams ?: FrameLayout.LayoutParams(context.dp(80), -1)).apply {
            width = context.dp(if (expanded) 220 else 80)
        }
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            if (expanded) intArrayOf(0xFF0F0F0F.toInt(), 0xFF0F0F0F.toInt(), 0xF00F0F0F.toInt())
            else intArrayOf(TvUi.background, TvUi.background))
    }
}
