package com.futo.platformplayer.views.adapters.viewholders

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.futo.platformplayer.*
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.fragment.mainactivity.bottombar.MenuBottomBarFragment
import com.futo.platformplayer.views.others.Toggle
import com.futo.platformplayer.views.adapters.AnyAdapter

data class TabViewHolderData(val buttonDefinition: MenuBottomBarFragment.ButtonDefinition, var enabled: Boolean);

class TabViewHolder(_viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<TabViewHolderData>(
    LayoutInflater.from(_viewGroup.context).inflate(R.layout.list_tab, _viewGroup, false)) {
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
        _imageDragDrop.setOnTouchListener(View.OnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onDragDrop.emit(this);
            }
            false
        });
    }

    override fun bind(i: TabViewHolderData) {
        _textTabName.text = _view.context.resources.getString(i.buttonDefinition.string);
        _toggleTab.visibility = if (i.buttonDefinition.canToggle) View.VISIBLE else View.GONE;
        _toggleTab.setValue(i.enabled, false);
        data = i;
    }
}