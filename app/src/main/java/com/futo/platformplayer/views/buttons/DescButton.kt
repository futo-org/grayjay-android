package com.futo.platformplayer.views.buttons

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1

class DescButton : LinearLayout {

    val imageIcon: ImageView;
    val textTitle: TextView;
    val textDescription: TextView;

    var onClick = Event0();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_desc_button, this);

        imageIcon = findViewById(R.id.image_icon)
        textTitle = findViewById(R.id.text_title)
        textDescription = findViewById(R.id.text_description)

        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.DescButton, 0, 0);
        imageIcon.setImageResource(attrArr.getResourceId(R.styleable.DescButton_desc_icon, 0))
        textTitle.text = attrArr.getText(R.styleable.DescButton_desc_title) ?: "";
        textDescription.text = attrArr.getText(R.styleable.DescButton_desc_description) ?: "";

        this.setOnClickListener { onClick.emit() }
    }
    constructor(context: Context, icon: Int, title: String, description: String) : super(context) {
        imageIcon = findViewById(R.id.image_icon)
        textTitle = findViewById(R.id.text_title)
        textDescription = findViewById(R.id.text_description)

        imageIcon.setImageResource(icon);
        textTitle.text = title ?: "";
        textDescription.text = description ?: "";

        this.setOnClickListener { onClick.emit() }
    }
}