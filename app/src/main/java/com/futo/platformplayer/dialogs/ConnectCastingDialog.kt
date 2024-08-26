package com.futo.platformplayer.dialogs

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.casting.CastConnectionState
import com.futo.platformplayer.casting.CastingDevice
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.adapters.DeviceAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConnectCastingDialog(context: Context?) : AlertDialog(context) {
    private lateinit var _imageLoader: ImageView;
    private lateinit var _buttonClose: Button;
    private lateinit var _buttonAdd: ImageButton;
    private lateinit var _buttonScanQR: ImageButton;
    private lateinit var _textNoDevicesFound: TextView;
    private lateinit var _textNoDevicesRemembered: TextView;
    private lateinit var _recyclerDevices: RecyclerView;
    private lateinit var _recyclerRememberedDevices: RecyclerView;
    private lateinit var _adapter: DeviceAdapter;
    private lateinit var _rememberedAdapter: DeviceAdapter;
    private val _devices: ArrayList<CastingDevice> = arrayListOf();
    private val _rememberedDevices: ArrayList<CastingDevice> = arrayListOf();

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_casting_connect, null));

        _imageLoader = findViewById(R.id.image_loader);
        _buttonClose = findViewById(R.id.button_close);
        _buttonAdd = findViewById(R.id.button_add);
        _buttonScanQR = findViewById(R.id.button_scan_qr);
        _recyclerDevices = findViewById(R.id.recycler_devices);
        _recyclerRememberedDevices = findViewById(R.id.recycler_remembered_devices);
        _textNoDevicesFound = findViewById(R.id.text_no_devices_found);
        _textNoDevicesRemembered = findViewById(R.id.text_no_devices_remembered);

        _adapter = DeviceAdapter(_devices, false);
        _recyclerDevices.adapter = _adapter;
        _recyclerDevices.layoutManager = LinearLayoutManager(context);

        _rememberedAdapter = DeviceAdapter(_rememberedDevices, true);
        _rememberedAdapter.onRemove.subscribe { d ->
            if (StateCasting.instance.activeDevice == d) {
                d.stopCasting();
            }

            StateCasting.instance.removeRememberedDevice(d);
            val index = _rememberedDevices.indexOf(d);
            if (index != -1) {
                _rememberedDevices.removeAt(index);
                _rememberedAdapter.notifyItemRemoved(index);
            }

            _textNoDevicesRemembered.visibility = if (_rememberedDevices.isEmpty()) View.VISIBLE else View.GONE;
            _recyclerRememberedDevices.visibility = if (_rememberedDevices.isNotEmpty()) View.VISIBLE else View.GONE;
        };
        _rememberedAdapter.onConnect.subscribe { _ ->
            dismiss()
            UIDialogs.showCastingDialog(context)
        }
        _adapter.onConnect.subscribe { _ ->
            dismiss()
            UIDialogs.showCastingDialog(context)
        }
        _recyclerRememberedDevices.adapter = _rememberedAdapter;
        _recyclerRememberedDevices.layoutManager = LinearLayoutManager(context);

        _buttonClose.setOnClickListener { dismiss(); };
        _buttonAdd.setOnClickListener {
            UIDialogs.showCastingAddDialog(context);
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

        StateCasting.instance.startDiscovering()

        (_imageLoader.drawable as Animatable?)?.start();

        _devices.clear();
        synchronized (StateCasting.instance.devices) {
            _devices.addAll(StateCasting.instance.devices.values);
        }

        _rememberedDevices.clear();
        synchronized (StateCasting.instance.rememberedDevices) {
            _rememberedDevices.addAll(StateCasting.instance.rememberedDevices);
        }

        _textNoDevicesFound.visibility = if (_devices.isEmpty()) View.VISIBLE else View.GONE;
        _recyclerDevices.visibility = if (_devices.isNotEmpty()) View.VISIBLE else View.GONE;
        _textNoDevicesRemembered.visibility = if (_rememberedDevices.isEmpty()) View.VISIBLE else View.GONE;
        _recyclerRememberedDevices.visibility = if (_rememberedDevices.isNotEmpty()) View.VISIBLE else View.GONE;

        StateCasting.instance.onDeviceAdded.subscribe(this) { d ->
            _devices.add(d);
            _adapter.notifyItemInserted(_devices.size - 1);
            _textNoDevicesFound.visibility = View.GONE;
            _recyclerDevices.visibility = View.VISIBLE;
        };

        StateCasting.instance.onDeviceChanged.subscribe(this) { d ->
            val index = _devices.indexOf(d);
            if (index == -1) {
                return@subscribe;
            }

            _devices[index] = d;
            _adapter.notifyItemChanged(index);
        };

        StateCasting.instance.onDeviceRemoved.subscribe(this) { d ->
            val index = _devices.indexOf(d);
            if (index == -1) {
                return@subscribe;
            }

            _devices.removeAt(index);
            _adapter.notifyItemRemoved(index);
            _textNoDevicesFound.visibility = if (_devices.isEmpty()) View.VISIBLE else View.GONE;
            _recyclerDevices.visibility = if (_devices.isNotEmpty()) View.VISIBLE else View.GONE;
        };

        StateCasting.instance.onActiveDeviceConnectionStateChanged.subscribe(this) { _, connectionState ->
            if (connectionState != CastConnectionState.CONNECTED) {
                return@subscribe;
            }

            StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                dismiss();
            };
        };

        _adapter.notifyDataSetChanged();
        _rememberedAdapter.notifyDataSetChanged();
    }

    override fun dismiss() {
        super.dismiss();

        (_imageLoader.drawable as Animatable?)?.stop();

        StateCasting.instance.stopDiscovering()
        StateCasting.instance.onDeviceAdded.remove(this);
        StateCasting.instance.onDeviceChanged.remove(this);
        StateCasting.instance.onDeviceRemoved.remove(this);
        StateCasting.instance.onActiveDeviceConnectionStateChanged.remove(this);
    }

    companion object {
        private val TAG = "CastingDialog";
    }
}