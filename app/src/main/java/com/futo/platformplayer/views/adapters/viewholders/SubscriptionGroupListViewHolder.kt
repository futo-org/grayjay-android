package com.futo.platformplayer.views.adapters.viewholders

import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.channels.SerializedChannel
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.dp
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.models.SubscriptionGroup
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.selectBestImage
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.adapters.ItemMoveCallback
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.polycentric.core.toURLInfoSystemLinkUrl
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel

class SubscriptionGroupListViewHolder(private val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<SubscriptionGroup>(
    LayoutInflater.from(_viewGroup.context).inflate(R.layout.list_subscription_group, _viewGroup, false)) {
    private var _group: SubscriptionGroup? = null;

    private val _thumb: ImageView;
    private val _image: ShapeableImageView;
    private val _textSubGroup: TextView;
    private val _textSubGroupMeta: TextView;

    private val _buttonSettings: ImageButton;
    private val _buttonDelete: ImageButton;

    val onClick = Event1<SubscriptionGroup>();
    val onSettings = Event1<SubscriptionGroup>();
    val onDelete = Event1<SubscriptionGroup>();
    val onDragDrop = Event1<RecyclerView.ViewHolder>();

    init {
        _thumb = _view.findViewById(R.id.thumb);
        _image = _view.findViewById(R.id.image);
        _textSubGroup = _view.findViewById(R.id.text_sub_group);
        _textSubGroupMeta = _view.findViewById(R.id.text_sub_group_meta);
        _buttonSettings = _view.findViewById(R.id.button_settings);
        _buttonDelete = _view.findViewById(R.id.button_trash);

        val dp6 = 6.dp(_view.resources);
        _image.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCorners(CornerFamily.ROUNDED, dp6.toFloat())
            .build()

        _view.setOnClickListener {
            _group?.let {
                onClick.emit(it);
            }
        }
        _thumb.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onDragDrop.emit(this);
            }
            false
        };
        _buttonSettings.setOnClickListener {
            _group?.let {
                onSettings.emit(it);
            };
        }
        _buttonDelete.setOnClickListener {
            _group?.let {
                onDelete.emit(it);
            };
        }
    }

    override fun bind(value: SubscriptionGroup) {
        _group = value;
        val img = value.image;
        if(img != null)
            img.setImageView(_image)
        else {
            _image.setImageResource(0);

            if(value is SubscriptionGroup.Add)
                _image.setBackgroundColor(Color.DKGRAY);
        }
        _textSubGroup.text = value.name;
        _textSubGroupMeta.text = "${value.urls.size} subscriptions";

        if(value is SubscriptionGroup.Selectable && value.selected)
            _view.setBackgroundColor(_view.context.resources.getColor(R.color.colorPrimary, null));
        else
            _view.setBackgroundColor(_view.context.resources.getColor(R.color.transparent, null));
    }

    companion object {
        private const val TAG = "SubscriptionGroupBarViewHolder";
    }
}