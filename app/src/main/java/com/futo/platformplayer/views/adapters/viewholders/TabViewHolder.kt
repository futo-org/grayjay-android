package com.futo.platformplayer.views.adapters.viewholders

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.fragment.mainactivity.bottombar.MenuBottomBarFragment
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.others.Toggle

data class TabViewHolderData(val buttonDefinition: MenuBottomBarFragment.ButtonDefinition, var enabled: Boolean);

@SuppressLint("ClickableViewAccessibility")
class TabViewHolder(viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<TabViewHolderData>(
    LayoutInflater.from(viewGroup.context).inflate(R.layout.list_tab, viewGroup, false)) {
    var data: TabViewHolderData? = null;

    private val _imageDragDrop: ImageView = _view.findViewById(R.id.image_drag_drop);
    private val _textTabName: TextView = _view.findViewById(R.id.text_tab_name);
    private val _toggleTab: Toggle = _view.findViewById(R.id.toggle_tab);

    val onDragDrop = Event1<ViewHolder>();
    val onEnableChanged = Event1<Boolean>();

    init {
        _toggleTab.onValueChanged.subscribe {
            onEnableChanged.emit(it);
        };
        _view.isClickable = true;
        _view.setOnClickListener {
            val d = data ?: return@setOnClickListener;
            if (!d.buttonDefinition.canToggle) {
                return@setOnClickListener;
            }

            d.enabled = !d.enabled;
            _toggleTab.setValue(d.enabled, true);
            onEnableChanged.emit(d.enabled);
        };
        _imageDragDrop.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onDragDrop.emit(this);
            }
            false
        };
    }

    override fun bind(value: TabViewHolderData) {
        _textTabName.text = _view.context.resources.getString(value.buttonDefinition.string);
        _toggleTab.visibility = if (value.buttonDefinition.canToggle) View.VISIBLE else View.GONE;
        _toggleTab.setValue(value.enabled, false);
        data = value;
    }
}