package com.futo.platformplayer.views.sources

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.views.others.BulletPointView

class SourceInfoView : LinearLayout {
    val image: ImageView;
    val textTitle: TextView;
    val textDescription: TextView;
    val bulletPoints: LinearLayout;

    var onClick = Event1<Pair<String, Any>>();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_source_info, this);
        image = findViewById(R.id.icon);
        textTitle = findViewById(R.id.title);
        textDescription = findViewById(R.id.description);
        bulletPoints = findViewById(R.id.bullet_points);
        bulletPoints.removeAllViews();
    }
    constructor(context: Context, iconId: Int, title: String, description: String, points: List<String> = listOf(), isLinks: Boolean = false) : super(context)  {
        inflate(context, R.layout.view_source_info, this);
        image = findViewById(R.id.icon);
        textTitle = findViewById(R.id.title);
        textDescription = findViewById(R.id.description);
        bulletPoints = findViewById(R.id.bullet_points);

        image.setImageResource(iconId);
        textTitle.text = title;
        textDescription.text = description;

        val primaryColor = ContextCompat.getColor(context, R.color.colorPrimary);

        bulletPoints.removeAllViews();
        for(point in points) {
            bulletPoints.addView(
                BulletPointView(context)
                .withText(point)
                .withTextColor(if(!isLinks) Color.WHITE else primaryColor));
        }
    }

    fun withDescriptionColor(color: Int) : SourceInfoView {
        textDescription.setTextColor(color);
        return this;
    }
}