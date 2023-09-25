package com.futo.platformplayer.views.others

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import com.futo.platformplayer.constructs.Event1
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout

class TagsView : FlexboxLayout {
    private val _padding_dp: Float = 4.0f;
    private val _padding_px: Int;

    var onClick = Event1<Pair<String, Any>>();

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        flexWrap = FlexWrap.WRAP;
        _padding_px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, _padding_dp, context.resources.displayMetrics).toInt();

        if (isInEditMode) {
            setTags(listOf("Example 1", "Example 2", "Example 3", "Example 4", "Example 5", "Example 5"));
        }
    }

    fun setTags(tags: List<String>) {
        setPairs(tags.map { t -> Pair(t, t) });
    }

    fun setPairs(tags: List<Pair<String, Any>>) {
        removeAllViews();
        for (tag in tags) {
            val tagView = TagView(context);
            tagView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            tagView.setInfo(tag.first, tag.second);
            tagView.setPadding(_padding_px, _padding_px, _padding_px, _padding_px);
            tagView.onClick.subscribe { onClick.emit(it) };
            addView(tagView);
        }
    }
}