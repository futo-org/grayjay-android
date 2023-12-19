package com.futo.platformplayer.views.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.casting.CastingDevice
import com.futo.platformplayer.constructs.Event1

class DeviceAdapter : RecyclerView.Adapter<DeviceViewHolder> {
    private val _devices: ArrayList<CastingDevice>;
    private val _isRememberedDevice: Boolean;

    var onRemove = Event1<CastingDevice>();
    var onConnect = Event1<CastingDevice>();

    constructor(devices: ArrayList<CastingDevice>, isRememberedDevice: Boolean) : super() {
        _devices = devices;
        _isRememberedDevice = isRememberedDevice;
    }

    override fun getItemCount() = _devices.size;

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_device, viewGroup, false);
        val holder = DeviceViewHolder(view);
        holder.setIsRememberedDevice(_isRememberedDevice);
        holder.onRemove.subscribe { d -> onRemove.emit(d); };
        holder.onConnect.subscribe { d -> onConnect.emit(d); }
        return holder;
    }

    override fun onBindViewHolder(viewHolder: DeviceViewHolder, position: Int) {
        viewHolder.bind(_devices[position]);
    }
}
