package com.futo.platformplayer.views.overlays.slideup

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.view.children
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0

class SlideUpMenuOverlay : RelativeLayout {
    private var _container: ViewGroup? = null;
    private lateinit var _textTitle: TextView;
    private lateinit var _textCancel: TextView;
    private lateinit var _textOK: TextView;
    private lateinit var _viewBackground: View;
    private lateinit var _viewOverlayContainer: LinearLayout;
    private lateinit var _viewContainer: LinearLayout;
    private var _animated: Boolean = true;

    var groupItems: List<View>;

    var isVisible = false
        private set;

    val onOK = Event0();
    val onCancel = Event0();

    constructor(context: Context, attrs: AttributeSet? = null): super(context, attrs) {
        init(false, null);
        groupItems = listOf();
    }

    constructor(context: Context, parent: ViewGroup, titleText: String, okText: String?, animated: Boolean, items: List<View>, hideButtons: Boolean = false): super(context){
        init(animated, okText);
        _container = parent;
        if(!_container!!.children.contains(this)) {
            _container!!.removeAllViews();
            _container!!.addView(this);
        }
        _textTitle.text = titleText;
        groupItems = items;

        if(hideButtons) {
            _textCancel.visibility = GONE;
            _textOK.visibility = GONE;
            _textTitle.textAlignment = TextView.TEXT_ALIGNMENT_CENTER;
        }

        setItems(items);
    }


    constructor(context: Context, parent: ViewGroup, titleText: String, okText: String?, animated: Boolean, vararg items: View?)
        : this(context, parent, titleText, okText, animated, items.filterNotNull().toList())

    fun setItems(items: List<View>) {
        _viewContainer.removeAllViews();

        for (item in items) {
            _viewContainer.addView(item);

            if (item is SlideUpMenuGroup)
                item.setParentClickListener { hide() };
            else if(item is SlideUpMenuItem)
                item.setParentClickListener { hide() };
        }

        groupItems = items;
    }

    private fun init(animated: Boolean, okText: String?){
        LayoutInflater.from(context).inflate(R.layout.overlay_slide_up_menu, this, true);

        _animated = animated;

        _textTitle = findViewById(R.id.overlay_slide_up_menu_title);
        _viewContainer = findViewById(R.id.overlay_slide_up_menu_items);
        _textCancel = findViewById(R.id.overlay_slide_up_menu_cancel);
        _textOK = findViewById(R.id.overlay_slide_up_menu_ok);
        setOk(okText);

        _viewBackground = findViewById(R.id.overlay_slide_up_menu_background);
        _viewOverlayContainer = findViewById(R.id.overlay_slide_up_menu_ovelay_container);

        _viewBackground.setOnClickListener {
            onCancel.emit();
            hide();
        };

        _textCancel.setOnClickListener {
            onCancel.emit();
            hide();
        };
    }

    fun setOk(textOk: String?) {
        if (textOk == null)
            _textOK.visibility = View.GONE;
        else {
            _textOK.text = textOk;
            _textOK.setOnClickListener {
                onOK.emit();
            };
            _textOK.visibility = View.VISIBLE;
        }
    }

    fun selectOption(groupTag: Any?, itemTag: Any?, multiSelect: Boolean = false, toggle: Boolean = false): Boolean {
        var didSelect = false;
        for(view in groupItems) {
            if(view is SlideUpMenuGroup && view.groupTag == groupTag)
                didSelect = didSelect || view.selectItem(itemTag);
        }
        if(groupTag == null)
            for(item in groupItems)
                if(item is SlideUpMenuItem) {
                    if(multiSelect) {
                        if(item.itemTag == itemTag)
                            didSelect = didSelect || item.setOptionSelected(!toggle || !item.selectedOption);
                    }
                    else
                        didSelect = didSelect || item.setOptionSelected(item.itemTag == itemTag && (!toggle || !item.selectedOption));
                }
        return didSelect;
    }

    fun show(){
        if (isVisible) {
            return;
        }

        isVisible = true;
        _container?.post {
            _container?.visibility = View.VISIBLE;
            _container?.bringToFront();
        }

        if (_animated) {
            _viewOverlayContainer.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            _viewOverlayContainer.translationY = _viewOverlayContainer.measuredHeight.toFloat()
            _viewBackground.alpha = 0f;

            val animations = arrayListOf<Animator>();
            animations.add(ObjectAnimator.ofFloat(_viewBackground, "alpha", 0.0f, 1.0f).setDuration(ANIMATION_DURATION_MS));
            animations.add(ObjectAnimator.ofFloat(_viewOverlayContainer, "translationY", _viewOverlayContainer.measuredHeight.toFloat(), 0.0f).setDuration(ANIMATION_DURATION_MS));

            val animatorSet = AnimatorSet();
            animatorSet.playTogether(animations);
            animatorSet.start();
        } else {
            _viewBackground.alpha = 1.0f;
            _viewOverlayContainer.translationY = 0.0f;
        }
    }

    fun hide(animate: Boolean = true){
        if (!isVisible) {
            return
        }

        isVisible = false;
        if (_animated && animate) {
            val animations = arrayListOf<Animator>();
            animations.add(ObjectAnimator.ofFloat(_viewBackground, "alpha", 1.0f, 0.0f).setDuration(ANIMATION_DURATION_MS));
            animations.add(ObjectAnimator.ofFloat(_viewOverlayContainer, "translationY", 0.0f, _viewOverlayContainer.measuredHeight.toFloat()).setDuration(ANIMATION_DURATION_MS));

            val animatorSet = AnimatorSet();
            animatorSet.doOnEnd {
                _container?.post {
                    _container?.visibility = View.GONE;
                }
            };

            animatorSet.playTogether(animations);
            animatorSet.start();
        } else {
            _viewBackground.alpha = 0.0f;
            _viewOverlayContainer.translationY = _viewOverlayContainer.measuredHeight.toFloat();
            _container?.visibility = View.GONE;
        }
    }

    companion object {
        private const val ANIMATION_DURATION_MS = 350L
    }
}