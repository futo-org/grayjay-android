package com.futo.platformplayer.tv.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.format.DateUtils
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.states.StateHistory
import com.futo.platformplayer.views.others.ProgressBar
import java.text.NumberFormat

internal object TvUi {
    val background = Color.rgb(15, 15, 15)
    val muted = Color.rgb(170, 170, 170)

    fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    fun column(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun row(context: Context) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    fun text(context: Context, value: CharSequence, size: Float = 18f, secondary: Boolean = false) =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(if (secondary) muted else Color.WHITE)
            typeface = ResourcesCompat.getFont(context, R.font.inter_regular)
        }

    fun heading(context: Context, value: CharSequence) =
        text(context, value, 24f).apply {
            setTypeface(typeface, Typeface.BOLD)
        }

    fun button(context: Context, label: CharSequence, action: () -> Unit) =
        text(context, label, 14f).apply {
            id = View.generateViewId()
            gravity = Gravity.CENTER
            minHeight = context.dp(36)
            setPadding(context.dp(18), context.dp(8), context.dp(18), context.dp(8))
            setTextColor(ContextCompat.getColorStateList(context, R.color.tv_button_text))
            focusable(this)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply {
                    marginEnd = context.dp(10)
                    topMargin = context.dp(8)
                    bottomMargin = context.dp(8)
                }
        }

    fun iconButton(context: Context, icon: Int, label: String, action: () -> Unit) =
        ImageButton(context).apply {
            id = View.generateViewId()
            contentDescription = label
            setImageResource(icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            imageTintList = ContextCompat.getColorStateList(context, R.color.tv_button_text)
            setPadding(context.dp(10), context.dp(10), context.dp(10), context.dp(10))
            focusable(this)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(context.dp(36), context.dp(36)).apply { marginStart = context.dp(10) }
        }

    fun focusable(view: View) {
        view.isFocusable = true
        view.isClickable = true
        view.setBackgroundResource(R.drawable.tv_focusable)
        view.setOnFocusChangeListener { target, focused ->
            target.animate()
                .scaleX(if (focused) 1.025f else 1f)
                .scaleY(if (focused) 1.025f else 1f)
                .setDuration(120)
                .start()
            target.elevation = if (focused) target.context.dp(4).toFloat() else 0f
        }
    }

    fun image(context: Context) =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setBackgroundResource(R.drawable.tv_thumbnail)
            clipToOutline = true
        }

    fun load(image: ImageView, url: String?) {
        Glide.with(image).load(url).centerCrop().into(image)
    }

    fun TextView.setPublishedDate(video: IPlatformVideo) {
        text = video.datetime?.let {
            DateUtils.getRelativeTimeSpanString(it.toInstant().toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        } ?: ""
        visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
    }

    fun watchProgress(context: Context) = ProgressBar(context).apply {
        id = R.id.time_bar
        inactiveColor = 0x55EEEEEE
        visibility = View.GONE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun ProgressBar.setHistoryProgress(video: IPlatformVideo) {
        val position = StateHistory.instance.getHistoryPosition(video.url)
        progress = if (video.duration > 0) (position.toFloat() / video.duration).coerceIn(0f, 1f) else 0f
        visibility = if (position > 0 && video.duration > 0 && !video.isLive) View.VISIBLE else View.GONE
    }

    fun count(value: Long): String = NumberFormat.getIntegerInstance().format(value)

    fun duration(seconds: Long): String =
        if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)

    fun TextView.lines(count: Int) {
        maxLines = count
        ellipsize = TextUtils.TruncateAt.END
    }
}
