package com.futo.platformplayer.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.futo.platformplayer.R

class NoResultsView: ConstraintLayout {

    val textTitle: TextView;
    val textCentered: TextView;
    val icon: ImageView;
    val containerExtraViews: LinearLayout;

    constructor(context: Context, attributes: AttributeSet? = null) : super(context, attributes){
        inflate(context, R.layout.view_no_results, this);
        textTitle = findViewById(R.id.text_title)
        textCentered = findViewById(R.id.text_centered);
        icon = findViewById(R.id.icon);
        containerExtraViews = findViewById(R.id.container_extra_views);
    }

    constructor(context: Context, title: String, text: String, iconId: Int, extraViews: List<View>) : super(context) {
        inflate(context, R.layout.view_no_results, this);
        textTitle = findViewById(R.id.text_title)
        textCentered = findViewById(R.id.text_centered);
        icon = findViewById(R.id.icon);
        containerExtraViews = findViewById(R.id.container_extra_views);

        setText(title, text, iconId, extraViews);
    }


    fun setText(title: String, text: String, iconId: Int = -1, extraViews: List<View>? = null) {
        textTitle.text = title;
        textCentered.text = text;
        if(iconId < 0)
            icon.visibility = GONE;
        else
            icon.setImageResource(iconId);

        if(extraViews != null)
            for(view in extraViews)
                containerExtraViews.addView(view);
    }
}