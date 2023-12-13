package com.futo.platformplayer.views.overlays.slideup

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1

class SlideUpMenuButtonList : LinearLayout {
    private val _root: LinearLayout;

    val onClick = Event1<String>();
    val buttons: HashMap<String, LinearLayout> = hashMapOf();
    var _activeText: String? = null;
    val id: String?

    constructor(context: Context, attrs: AttributeSet? = null, id: String? = null): super(context, attrs) {
        this.id = id

        LayoutInflater.from(context).inflate(R.layout.overlay_slide_up_menu_button_list, this, true);

        _root = findViewById(R.id.root);
    }

    fun setButtons(texts: List<String>, activeText: String? = null) {
        _root.removeAllViews();

        val marginLeft = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3.0f, resources.displayMetrics).toInt();
        val marginRight = marginLeft;

        buttons.clear();
        for (t in texts) {
            val button = LinearLayout(context);
            button.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                weight = 1.0f;
                marginStart = marginLeft;
                marginEnd = marginRight;
            };

            button.background = if (t == activeText) ContextCompat.getDrawable(context, R.drawable.background_slide_up_option_selected) else ContextCompat.getDrawable(context, R.drawable.background_slide_up_option);
            button.gravity = Gravity.CENTER;
            button.setOnClickListener {
                onClick.emit(t);
            };

            button.setPadding(0, 0, 0, 0);

            val text = TextView(context);
            text.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 8f);
            text.text = t;
            text.maxLines = 1;
            text.setTextColor(ContextCompat.getColor(context, R.color.white));
            text.typeface = ResourcesCompat.getFont(context, R.font.inter_light);
            button.addView(text);

            _activeText = activeText;
            buttons[t] = button;
            _root.addView(button);
        }
    }

    fun setSelected(text: String) {
        buttons[_activeText]?.background = ContextCompat.getDrawable(context, R.drawable.background_slide_up_option);
        buttons[text]?.background = ContextCompat.getDrawable(context, R.drawable.background_slide_up_option_selected);
        _activeText = text;
    }
}