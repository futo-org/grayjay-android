package com.futo.platformplayer.views.pills

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.states.StateApp

class RoundButton : LinearLayout {
    val icon: ImageView;
    val text: TextView;

    val onClick = Event0();
    val handler: ((RoundButton)->Unit)?;

    val iconResource: Int;
    val tagRef: Any?;

    constructor(context : Context, iconRes: Int, title: String, tag: Any? = null, handler: ((RoundButton)->Unit)? = null) : super(context) {
        LayoutInflater.from(context).inflate(R.layout.button_round, this, true);
        this.tagRef = tag;
        this.handler = handler;
        this.iconResource = iconRes;

        icon = findViewById(R.id.pill_icon);
        text = findViewById(R.id.pill_text);

        icon.setImageResource(iconRes);
        text.text = title;

        icon.setOnClickListener {
            onClick.emit();
            if(handler != null)
                handler(this@RoundButton);
        };
    }

    constructor(context : Context, attrs : AttributeSet) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.button_round, this, true);
        tagRef = null;
        handler = null;
        iconResource = -1;

        icon = findViewById(R.id.pill_icon);
        text = findViewById(R.id.pill_text);

        findViewById<LinearLayout>(R.id.root).setOnClickListener {
            onClick.emit();
        };
    }

    companion object {
        val WIDTH = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 55f, StateApp.instance.context.resources.displayMetrics);
    }
}