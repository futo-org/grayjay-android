package com.futo.platformplayer.views.others

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import com.futo.platformplayer.constructs.Event1
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import android.os.Handler

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

    fun setOptions(
        options: List<Pair<String, Any?>>,
        initiallySelectedOptions: List<Any?>,
        multiSelect: Boolean,
        atLeastOne: Boolean,
        tapDelay: Long = if (multiSelect) 500 else 0,
    ) {
        selectedOptions.clear();
        selectedOptions.addAll(initiallySelectedOptions);

        removeAllViews();

        val handler = Handler();
        val sendEvent = Runnable { onSelectedChange.emit(selectedOptions); };
        var lastClickOption: String = "";
        var lastClickTime: Long = System.currentTimeMillis();
        val radioViews = arrayListOf<RadioView>();
        for (option in options) {
            val radioView = RadioView(context);
            radioViews.add(radioView);
            radioView.setHandleClick(false);
            radioView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            radioView.setInfo(option.first, initiallySelectedOptions.contains(option.second));
            radioView.setPadding(_padding_px, _padding_px, _padding_px, _padding_px);
            radioView.onClick.subscribe {
                val selected = radioView.selected;
                val nSelected = selectedOptions.size;
                var changed: Boolean = true;
                when {
                    ( //double tap
                        lastClickOption == option.first
                        && lastClickTime + tapDelay > System.currentTimeMillis()
                        && multiSelect)
                    -> {
                        synchronized(selectedOptions) {
                            //set all
                            if(nSelected <= 1 && selected || nSelected <= 0) {
                                radioViews.forEach { it.setIsSelected(true) };
                                selectedOptions.clear();
                                selectedOptions.addAll(options.map {it.second});
                            } else { //set only one
                                radioViews.forEach { it.setIsSelected(false) };
                                radioView.setIsSelected(true);
                                selectedOptions.clear();
                                selectedOptions.add(option.second);
                            }
                        }
                    }

                    (!selected) -> { //select
                        if (nSelected > 0 && !multiSelect) {
                            radioViews.forEach { it.setIsSelected(false) };
                            selectedOptions.clear();
                        }

                        radioView.setIsSelected(true);
                        selectedOptions.add(option.second);
                    }

                    //unselect
                    (!atLeastOne || nSelected > 1) -> {
                        radioView.setIsSelected(false);
                        selectedOptions.remove(option.second);
                    }

                    //keep last selection
                    else -> changed = false
                }
                lastClickTime = System.currentTimeMillis();
                lastClickOption = option.first;


                //delay replacement of RadioGroupView by users
                //if that happens too early we loose lastClickOption
                //and the layout shifts while double tapping
                //maybe it is beter if done in SlideUpMenuFilters or SlideUpMenuRadioGroup
                handler.removeCallbacks(sendEvent);
                if(changed) handler.postDelayed(sendEvent, tapDelay);
            };
            addView(radioView);
        }
    }
}