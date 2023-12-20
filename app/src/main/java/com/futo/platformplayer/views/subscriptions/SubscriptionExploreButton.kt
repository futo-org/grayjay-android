package com.futo.platformplayer.views.subscriptions

import android.content.Context
import android.graphics.drawable.Animatable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StateSubscriptions
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel

class SubscriptionExploreButton : ConstraintLayout {
    val onClick = Event0();


    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        inflate(context, R.layout.view_subscription_group_bar_explore, this);

        val dp10 = 10.dp(resources);
        findViewById<ShapeableImageView>(R.id.image)
            .apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_CROP;
                shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, dp10.toFloat()).build()
            }

        findViewById<ConstraintLayout>(R.id.root).setOnClickListener {
            onClick.emit();
        }
    }
}