package com.futo.platformplayer.views.adapters

import android.graphics.drawable.Animatable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.futo.platformplayer.R
import com.futo.platformplayer.casting.*
import com.futo.platformplayer.constructs.Event1

class DeviceViewHolder : ViewHolder {
    private val _imageDevice: ImageView;
    private val _textName: TextView;
    private val _textType: TextView;
    private val _textNotReady: TextView;
    private val _buttonDisconnect: LinearLayout;
    private val _buttonConnect: LinearLayout;
    private val _buttonRemove: LinearLayout;
    private val _imageLoader: ImageView;
    private var _animatableLoader: Animatable? = null;
    private var _isRememberedDevice: Boolean = false;

    var device: CastingDevice? = null
        private set

    var onRemove = Event1<CastingDevice>();

    constructor(view: View) : super(view) {
        _imageDevice = view.findViewById(R.id.image_device);
        _textName = view.findViewById(R.id.text_name);
        _textType = view.findViewById(R.id.text_type);
        _textNotReady = view.findViewById(R.id.text_not_ready);
        _buttonDisconnect = view.findViewById(R.id.button_disconnect);
        _buttonConnect = view.findViewById(R.id.button_connect);
        _buttonRemove = view.findViewById(R.id.button_remove);
        _imageLoader = view.findViewById(R.id.image_loader);

        val d = _imageLoader.drawable;
        if (d is Animatable) {
            _animatableLoader = d;
        }

        _buttonDisconnect.setOnClickListener {
            StateCasting.instance.activeDevice?.stopCasting();
            updateButton();
        };

        _buttonConnect.setOnClickListener {
            val d = device ?: return@setOnClickListener;
            StateCasting.instance.activeDevice?.stopCasting();
            StateCasting.instance.connectDevice(d);
            updateButton();
        };

        _buttonRemove.setOnClickListener {
            val d = device ?: return@setOnClickListener;
            onRemove.emit(d);
        };

        setIsRememberedDevice(false);
    }

    fun setIsRememberedDevice(isRememberedDevice: Boolean) {
        _isRememberedDevice = isRememberedDevice;
        _buttonRemove.visibility = if (isRememberedDevice) View.VISIBLE else View.GONE;
    }

    fun bind(d: CastingDevice) {
        if (d is ChromecastCastingDevice) {
            _imageDevice.setImageResource(R.drawable.ic_chromecast);
            _textType.text = "Chromecast";
        } else if (d is AirPlayCastingDevice) {
            _imageDevice.setImageResource(R.drawable.ic_airplay);
            _textType.text = "AirPlay";
        } else if (d is FastCastCastingDevice) {
            _imageDevice.setImageResource(R.drawable.ic_fc);
            _textType.text = "FastCast";
        }

        _textName.text = d.name;
        device = d;
        updateButton();
    }

    private fun updateButton() {
        val d = device ?: return;

        if (!d.isReady) {
            _buttonConnect.visibility = View.GONE;
            _buttonDisconnect.visibility = View.GONE;
            _imageLoader.visibility = View.GONE;
            _textNotReady.visibility = View.VISIBLE;
            return;
        }

        _textNotReady.visibility = View.GONE;

        val dev = StateCasting.instance.activeDevice;
        if (dev == d) {
            if (dev.connectionState == CastConnectionState.CONNECTED) {
                _buttonConnect.visibility = View.GONE;
                _buttonDisconnect.visibility = View.VISIBLE;
                _imageLoader.visibility = View.GONE;
                _textNotReady.visibility = View.GONE;
            } else {
                _buttonConnect.visibility = View.GONE;
                _buttonDisconnect.visibility = View.VISIBLE;
                _imageLoader.visibility = View.VISIBLE;
                _textNotReady.visibility = View.GONE;
            }
        } else {
            if (d.isReady) {
                _buttonConnect.visibility = View.VISIBLE;
                _buttonDisconnect.visibility = View.GONE;
                _imageLoader.visibility = View.GONE;
                _textNotReady.visibility = View.GONE;
            } else {
                _buttonConnect.visibility = View.GONE;
                _buttonDisconnect.visibility = View.GONE;
                _imageLoader.visibility = View.GONE;
                _textNotReady.visibility = View.VISIBLE;
            }
        }

        if (_imageLoader.visibility == View.VISIBLE) {
            _animatableLoader?.start();
        } else {
            _animatableLoader?.stop();
        }
    }
}