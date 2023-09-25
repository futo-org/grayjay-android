package com.futo.platformplayer.views.others

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0

class BulletPointView : LinearLayout {

    val bulletPoint: TextView;
    val bulletPointValue: TextView;

    var onClick = Event0();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_bullet_point, this);

        bulletPointValue = findViewById(R.id.bullet_text)
        bulletPoint = findViewById(R.id.bullet_point)

        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.BulletPointView, 0, 0);
        bulletPointValue.setTextColor(attrArr.getColor(R.styleable.BulletPointView_valueColor, Color.WHITE));
        bulletPoint.setTextColor(attrArr.getColor(R.styleable.BulletPointView_bulletColor, Color.WHITE));
        bulletPointValue.text = attrArr.getText(R.styleable.BulletPointView_bulletText) ?: "";

        this.setOnClickListener { onClick.emit() }
    }

    fun withTextColor(color: Int) : BulletPointView {
        bulletPointValue.setTextColor(color);
        return this;
    }

    fun withText(str: String) : BulletPointView {
        bulletPointValue.text = str;
        return this;
    }
}