package com.futo.platformplayer.views.buttons

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.collection.emptyLongSet
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.views.pills.PillButton

class ButtonsContainer : LinearLayout {

    val container_buttons: LinearLayout

    var currentButtons: List<Button> = listOf();

    constructor(context: Context, buttons: List<Button>) : super(context) {
        inflate(context, R.layout.view_buttons, this)
        container_buttons = findViewById(R.id.container_buttons);
        setButtons(buttons);
    }

    fun setButtons(buttons: List<Button>) {
        this.currentButtons = buttons;
        container_buttons.removeAllViews();
        for(button in buttons) {
            container_buttons.addView(StandardButton(context, button.name) {
                button?.handler?.invoke();
            }.apply {
                this.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    this.weight = 1f;
                };
                if(button.background != null)
                    this.withBackground(button.background);
            })
        }
    }

    class Button(
        val name: String,
        val background: Int?,
        val handler: (()->Unit),
    );
}