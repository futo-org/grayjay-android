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

class PillButton : LinearLayout {
    val icon: ImageView;
    val text: TextView;
    val onClick = Event0();

    constructor(context : Context, attrs : AttributeSet) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.pill_button, this, true);
        icon = findViewById(R.id.pill_icon);
        text = findViewById(R.id.pill_text);

        val attrArr = context.obtainStyledAttributes(attrs, R.styleable.PillButton, 0, 0);
        val attrIconRef = attrArr.getResourceId(R.styleable.PillButton_pillIcon, -1);
        if(attrIconRef != -1)
            icon.setImageResource(attrIconRef);
        else
            icon.visibility = View.GONE;

        val attrText = attrArr.getText(R.styleable.PillButton_pillText) ?: "";
        text.text = attrText;

        findViewById<LinearLayout>(R.id.root).setOnClickListener {
            onClick.emit();
        };
    }
}