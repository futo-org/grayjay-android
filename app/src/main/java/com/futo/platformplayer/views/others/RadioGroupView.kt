package com.futo.platformplayer.views.others

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import com.futo.platformplayer.constructs.Event1
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout

class RadioGroupView : FlexboxLayout {
    private val _padding_dp: Float = 4.0f;
    private val _padding_px: Int;

    val selectedOptions = arrayListOf<Any?>();
    val onSelectedChange = Event1<List<Any?>>();
    constructor(context: Context) : super(context) {
        flexWrap = FlexWrap.WRAP;
        _padding_px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, _padding_dp, context.resources.displayMetrics).toInt();

        if (isInEditMode) {
            setOptions(listOf("Example 1" to 1, "Example 2" to 2, "Example 3" to 3, "Example 4" to 4, "Example 5" to 5), listOf("Example 1", "Example 2"),
                multiSelect = true,
                atLeastOne = false
            );
        }
    }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        flexWrap = FlexWrap.WRAP;
        _padding_px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, _padding_dp, context.resources.displayMetrics).toInt();

        if (isInEditMode) {
            setOptions(listOf("Example 1" to 1, "Example 2" to 2, "Example 3" to 3, "Example 4" to 4, "Example 5" to 5), listOf("Example 1", "Example 2"),
                multiSelect = true,
                atLeastOne = false
            );
        }
    }

    fun setOptions(options: List<Pair<String, Any?>>, initiallySelectedOptions: List<Any?>, multiSelect: Boolean, atLeastOne: Boolean) {
        selectedOptions.clear();
        selectedOptions.addAll(initiallySelectedOptions);

        removeAllViews();

        val radioViews = arrayListOf<RadioView>();
        for (option in options) {
            val radioView = RadioView(context);
            radioViews.add(radioView);
            radioView.setHandleClick(false);
            radioView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            radioView.setInfo(option.first, initiallySelectedOptions.contains(option.second));
            radioView.setPadding(_padding_px, _padding_px, _padding_px, _padding_px);
            if(multiSelect)
                radioView.onLongClick.subscribe {
                    val selected = !radioView.selected;
                    if (selected) {
                        selectedOptions.clear();
                        for(v in radioViews)
                            v.setIsSelected(true);
                        selectedOptions.addAll(options.map { it.second });
                    } else {
                        if(atLeastOne) {
                            for(v in radioViews)
                                v.setIsSelected(false);
                            selectedOptions.clear();
                            selectedOptions.add(option.second);
                        }
                        else {
                            for(v in radioViews)
                                v.setIsSelected(false);
                            selectedOptions.clear();
                        }
                    }
                    onSelectedChange.emit(selectedOptions);
                }
            radioView.onClick.subscribe {
                val selected = !radioView.selected;
                if (selected) {
                    if (selectedOptions.size > 0 && !multiSelect) {
                        for (v in radioViews) {
                            v.setIsSelected(false);
                        }

                        selectedOptions.clear();
                    }

                    radioView.setIsSelected(true);
                    selectedOptions.add(option.second);
                } else {
                    if (selectedOptions.size < 2 && atLeastOne) {
                        return@subscribe;
                    }

                    radioView.setIsSelected(false);
                    selectedOptions.remove(option.second);
                }

                onSelectedChange.emit(selectedOptions);
            };
            addView(radioView);
        }
    }
}