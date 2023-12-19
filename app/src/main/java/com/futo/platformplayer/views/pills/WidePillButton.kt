package com.futo.platformplayer.views.pills

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0

class WidePillButton : LinearLayout {
    private val _iconPrefix: ImageView
    private val _iconSuffix: ImageView
    private val _text: TextView
    val onClick = Event0()

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_wide_pill_button, this, true)
        _iconPrefix = findViewById(R.id.image_prefix)
        _iconSuffix = findViewById(R.id.image_suffix)
        _text = findViewById(R.id.text)

        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.WidePillButton, 0, 0)
        setIconPrefix(attrArr.getResourceId(R.styleable.WidePillButton_widePillIconPrefix, -1))
        setIconSuffix(attrArr.getResourceId(R.styleable.WidePillButton_widePillIconSuffix, -1))
        setText(attrArr.getText(R.styleable.PillButton_pillText) ?: "")
        attrArr.recycle()

        findViewById<LinearLayout>(R.id.root).setOnClickListener {
            onClick.emit()
        }
    }

    fun setIconPrefix(drawable: Int) {
        if (drawable != -1) {
            _iconPrefix.setImageResource(drawable)
            _iconPrefix.visibility = View.VISIBLE
        } else {
            _iconPrefix.visibility = View.GONE
        }
    }

    fun setIconSuffix(drawable: Int) {
        if (drawable != -1) {
            _iconSuffix.setImageResource(drawable)
            _iconSuffix.visibility = View.VISIBLE
        } else {
            _iconSuffix.visibility = View.GONE
        }
    }

    fun setText(t: CharSequence) {
        _text.text = t
    }
}