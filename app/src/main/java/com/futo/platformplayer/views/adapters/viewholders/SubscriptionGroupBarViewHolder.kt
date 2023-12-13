package com.futo.platformplayer.views.adapters.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.dp
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.SubscriptionGroup
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.selectBestImage
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.polycentric.core.toURLInfoSystemLinkUrl
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel

class SubscriptionGroupBarViewHolder(private val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<SubscriptionGroup>(
    LayoutInflater.from(_viewGroup.context).inflate(R.layout.view_subscription_group_bar, _viewGroup, false)) {
    private var _group: SubscriptionGroup? = null;

    private val _image: ShapeableImageView;
    private val _textSubGroup: TextView;

    val onClick = Event1<SubscriptionGroup>();
    val onClickLong = Event1<SubscriptionGroup>();

    init {
        _image = _view.findViewById(R.id.image);
        _textSubGroup = _view.findViewById(R.id.text_sub_group);

        val dp6 = 6.dp(_view.resources);
        _image.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCorners(CornerFamily.ROUNDED, dp6.toFloat())
            .build()

        _viewGroup.setOnClickListener {
            _group?.let {
                onClick.emit(it);
            }
        }
        _viewGroup.setOnLongClickListener {
            _group?.let {
                onClickLong.emit(it);
            }
            true;
        }
    }

    override fun bind(value: SubscriptionGroup) {
        _group = value;
        val img = value.image;
        if(img != null)
            img.setImageView(_image)
        else
            _image.setImageResource(0);
        _textSubGroup.text = value.name;
    }

    companion object {
        private const val TAG = "SubscriptionGroupBarViewHolder";
    }
}