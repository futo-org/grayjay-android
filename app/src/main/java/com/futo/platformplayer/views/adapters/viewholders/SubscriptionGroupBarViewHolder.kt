package com.futo.platformplayer.views.adapters.viewholders

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.models.SubscriptionGroup
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.google.android.material.imageview.ShapeableImageView

class SubscriptionGroupBarViewHolder(private val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<SubscriptionGroup>(
    LayoutInflater.from(_viewGroup.context).inflate(R.layout.view_subscription_group_bar, _viewGroup, false)) {
    private var _group: SubscriptionGroup? = null;

    private val _root: FrameLayout;
    private val _image: ShapeableImageView;
    private val _textSubGroup: TextView;


    val onClick = Event1<SubscriptionGroup>();
    val onClickLong = Event1<SubscriptionGroup>();

    init {
        _root = _view.findViewById(R.id.root);
        _image = _view.findViewById(R.id.image);
        _textSubGroup = _view.findViewById(R.id.text_sub_group);

        _view.setOnClickListener {
            _group?.let {
                onClick.emit(it);
            }
        }
        _view.setOnLongClickListener {
            _group?.let {
                onClickLong.emit(it);
            }
            true;
        }
    }

    override fun bind(value: SubscriptionGroup) {
        _group = value;
        val img = value.image;
        if(img != null) {
            img.setImageView(_image)
        } else {
            _image.setImageResource(0);

            if(value is SubscriptionGroup.Add)
                _image.setBackgroundColor(Color.DKGRAY);
        }
        _textSubGroup.text = value.name;

        if (value is SubscriptionGroup.Selectable && value.selected) {
            _root.setBackgroundResource(R.drawable.background_primary_round_6dp)
        } else {
            _root.background = null
        }
    }

    companion object {
        private const val TAG = "SubscriptionGroupBarViewHolder";
    }
}