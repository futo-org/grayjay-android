package com.futo.platformplayer.dialogs

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.casting.CastConnectionState
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.adapters.DeviceAdapter
import com.futo.platformplayer.views.adapters.DeviceAdapterEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConnectCastingDialog(context: Context?) : AlertDialog(context) {
    private lateinit var _imageLoader: ImageView;
    private lateinit var _buttonClose: Button;
    private lateinit var _buttonAdd: LinearLayout;
    private lateinit var _buttonScanQR: LinearLayout;
    private lateinit var _textNoDevicesFound: TextView;
    private lateinit var _recyclerDevices: RecyclerView;
    private lateinit var _adapter: DeviceAdapter;
    private val _devices: MutableSet<String> = mutableSetOf()
    private val _rememberedDevices: MutableSet<String> = mutableSetOf()
    private val _unifiedDevices: MutableList<DeviceAdapterEntry> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_casting_connect, null));

        _imageLoader = findViewById(R.id.image_loader);
        _buttonClose = findViewById(R.id.button_close);
        _buttonAdd = findViewById(R.id.button_add);
        _buttonScanQR = findViewById(R.id.button_qr);
        _recyclerDevices = findViewById(R.id.recycler_devices);
        _textNoDevicesFound = findViewById(R.id.text_no_devices_found);

        _adapter = DeviceAdapter(_unifiedDevices)
        _recyclerDevices.adapter = _adapter;
        _recyclerDevices.layoutManager = LinearLayoutManager(context);

        _adapter.onPin.subscribe { d ->
            val isRemembered = _rememberedDevices.contains(d.name)
            val newIsRemembered = !isRemembered
            if (newIsRemembered) {
                StateCasting.instance.addRememberedDevice(d)
                val name = d.name
                if (name != null) {
                    _rememberedDevices.add(name)
                }
            } else {
                StateCasting.instance.removeRememberedDevice(d)
                _rememberedDevices.remove(d.name)
            }
            updateUnifiedList()
        }

        //TODO: Integrate remembered into the main list
        //TODO: Add green indicator to indicate a device is oneline
        //TODO: Add pinning
        //TODO: Implement QR code as an option in add manually
        //TODO: Remove start button

        _adapter.onConnect.subscribe { _ ->
            dismiss()
            //UIDialogs.showCastingDialog(context)
        }

        _buttonClose.setOnClickListener { dismiss(); };
        _buttonAdd.setOnClickListener {
            UIDialogs.showCastingAddDialog(context, ownerActivity);
            dismiss();
        };

        val c = ownerActivity
        if (c is MainActivity) {
            _buttonScanQR.visibility = View.VISIBLE
            _buttonScanQR.setOnClickListener {
                c.showUrlQrCodeScanner()
                dismiss()
            };
        } else {
            _buttonScanQR.visibility = View.GONE
        }
    }

    override fun show() {
        super.show();
        Logger.i(TAG, "Dialog shown.");

        (_imageLoader.drawable as Animatable?)?.start();

        synchronized(StateCasting.instance.devices) {
            _devices.addAll(StateCasting.instance.devices.values.mapNotNull { it.name })
        }
        _rememberedDevices.addAll(StateCasting.instance.getRememberedCastingDeviceNames())

        updateUnifiedList()

        StateCasting.instance.onDeviceAdded.subscribe(this) { d ->
            val name = d.name
            if (name != null) {
                _devices.add(name)
                updateUnifiedList()
            }
        }

        StateCasting.instance.onDeviceChanged.subscribe(this) { d ->
            val index = _unifiedDevices.indexOfFirst { it.castingDevice.name == d.name }
            if (index != -1) {
                _unifiedDevices[index] = DeviceAdapterEntry(d, _unifiedDevices[index].isPinnedDevice, _unifiedDevices[index].isOnlineDevice)
                _adapter.notifyItemChanged(index)
            }
        }

        StateCasting.instance.onDeviceRemoved.subscribe(this) { d ->
            _devices.remove(d.name)
            updateUnifiedList()
        }

        StateCasting.instance.onActiveDeviceConnectionStateChanged.subscribe(this) { _, connectionState ->
            if (connectionState == CastConnectionState.CONNECTED) {
                StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                    dismiss()
                }
            }
        }
    }

    override fun dismiss() {
        super.dismiss()
        (_imageLoader.drawable as Animatable?)?.stop()
        StateCasting.instance.onDeviceAdded.remove(this)
        StateCasting.instance.onDeviceChanged.remove(this)
        StateCasting.instance.onDeviceRemoved.remove(this)
        StateCasting.instance.onActiveDeviceConnectionStateChanged.remove(this)
    }

    private fun updateUnifiedList() {
        val oldList = ArrayList(_unifiedDevices)
        val newList = buildUnifiedList()

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldList[oldItemPosition]
                val newItem = newList[newItemPosition]
                return oldItem.castingDevice.name == newItem.castingDevice.name
                        && oldItem.castingDevice.isReady == newItem.castingDevice.isReady
                        && oldItem.isOnlineDevice == newItem.isOnlineDevice
                        && oldItem.isPinnedDevice == newItem.isPinnedDevice
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldList[oldItemPosition]
                val newItem = newList[newItemPosition]
                return oldItem.castingDevice.name == newItem.castingDevice.name
                        && oldItem.castingDevice.isReady == newItem.castingDevice.isReady
                        && oldItem.isOnlineDevice == newItem.isOnlineDevice
                        && oldItem.isPinnedDevice == newItem.isPinnedDevice
            }
        })

        _unifiedDevices.clear()
        _unifiedDevices.addAll(newList)
        diffResult.dispatchUpdatesTo(_adapter)

        _textNoDevicesFound.visibility = if (_unifiedDevices.isEmpty()) View.VISIBLE else View.GONE
        _recyclerDevices.visibility = if (_unifiedDevices.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildUnifiedList(): List<DeviceAdapterEntry> {
        val onlineDevices = StateCasting.instance.devices.values.associateBy { it.name }
        val rememberedDevices = StateCasting.instance.getRememberedCastingDevices().associateBy { it.name }

        val unifiedList = mutableListOf<DeviceAdapterEntry>()

        val intersectionNames = _devices.intersect(_rememberedDevices)
        for (name in intersectionNames) {
            onlineDevices[name]?.let { unifiedList.add(DeviceAdapterEntry(it, true, true)) }
        }

        val onlineOnlyNames = _devices - _rememberedDevices
        for (name in onlineOnlyNames) {
            onlineDevices[name]?.let { unifiedList.add(DeviceAdapterEntry(it, false, true)) }
        }

        val rememberedOnlyNames = _rememberedDevices - _devices
        for (name in rememberedOnlyNames) {
            rememberedDevices[name]?.let { unifiedList.add(DeviceAdapterEntry(it, true, false)) }
        }

        return unifiedList
    }

    companion object {
        private val TAG = "CastingDialog";
    }
}