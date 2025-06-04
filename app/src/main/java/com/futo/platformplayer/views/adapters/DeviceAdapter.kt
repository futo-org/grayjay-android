package com.futo.platformplayer.views.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.casting.CastingDevice
import com.futo.platformplayer.constructs.Event1

data class DeviceAdapterEntry(val castingDevice: CastingDevice, val isPinnedDevice: Boolean, val isOnlineDevice: Boolean)

class DeviceAdapter : RecyclerView.Adapter<DeviceViewHolder> {
    private val _devices: List<DeviceAdapterEntry>;

    var onPin = Event1<CastingDevice>();
    var onConnect = Event1<CastingDevice>();

    constructor(devices: List<DeviceAdapterEntry>) : super() {
        _devices = devices;
    }

    override fun getItemCount() = _devices.size;

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_device, viewGroup, false);
        val holder = DeviceViewHolder(view);
        holder.onPin.subscribe { d -> onPin.emit(d); };
        holder.onConnect.subscribe { d -> onConnect.emit(d); }
        return holder;
    }

    override fun onBindViewHolder(viewHolder: DeviceViewHolder, position: Int) {
        val p = _devices[position];
        viewHolder.bind(p.castingDevice, p.isOnlineDevice, p.isPinnedDevice);
    }
}
