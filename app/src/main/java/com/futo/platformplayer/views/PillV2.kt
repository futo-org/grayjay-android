package com.futo.platformplayer.views

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.models.ImageVariable
import com.futo.platformplayer.views.others.ToggleTagView

class PillV2: FrameLayout {

    val root: FrameLayout;
    val text: TextView;

    var isToggled: Boolean = false;

    val onClick = Event1<Boolean>();

    constructor(context: Context, name: String, isActive: Boolean = false, action: (PillV2, Boolean)->Unit, actionLong: ((PillV2, Boolean)->Unit)? = null): super(context) {
        inflate(context, R.layout.view_tag_v2, this);
        root = findViewById(R.id.root);
        text = findViewById(R.id.text_tag);

        text.text = name;
        setIsEnabled(isActive);

        setOnClickListener {
            setIsEnabled(!isToggled);
            onClick.emit(isToggled);
            action(this, isToggled);
        }
        if(actionLong != null)
            setOnLongClickListener {
                actionLong(this, this.isToggled);
                return@setOnLongClickListener true;
            }
    }

    constructor(context: Context, attr: AttributeSet? = null) : super(context, attr) {
        inflate(context, R.layout.view_tag_v2, this);
        root = findViewById(R.id.root);
        text = findViewById(R.id.text_tag);

        val attrArr = context.obtainStyledAttributes(attr, R.styleable.PillV2, 0, 0);
        val attrEnabled = attrArr.getBoolean(R.styleable.PillV2_pillV2Enabled, false);
        val attrText = attrArr.getText(R.styleable.PillV2_pillV2Text) ?: "";
        text.text = attrText;
        setIsEnabled(attrEnabled);

        setOnClickListener {
            setIsEnabled(!isToggled);
            onClick.emit(isToggled);
        }
    }



    fun setText(text: String) {
        this.text.text = text;
    }

    fun setIsEnabled(enabled: Boolean = true) {
        if(enabled)
            root.setBackgroundResource(R.drawable.background_2e_round_4dp)
        else
            root.setBackgroundResource(R.drawable.background_black_2e_round_4dp);
        this.isToggled = enabled;
    }

}