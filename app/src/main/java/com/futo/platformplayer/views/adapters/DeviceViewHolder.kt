package com.futo.platformplayer.views.adapters

import android.graphics.drawable.Animatable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.futo.platformplayer.R
import com.futo.platformplayer.casting.AirPlayCastingDevice
import com.futo.platformplayer.casting.CastConnectionState
import com.futo.platformplayer.casting.CastingDevice
import com.futo.platformplayer.casting.ChromecastCastingDevice
import com.futo.platformplayer.casting.FCastCastingDevice
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import androidx.core.view.isVisible
import com.futo.platformplayer.UIDialogs

class DeviceViewHolder : ViewHolder {
    private val _layoutDevice: FrameLayout;
    private val _imageDevice: ImageView;
    private val _textName: TextView;
    private val _textType: TextView;
    private val _textNotReady: TextView;
    private val _imageLoader: ImageView;
    private val _imageOnline: ImageView;
    private val _root: ConstraintLayout;
    private var _animatableLoader: Animatable? = null;
    private var _imagePin: ImageView;

    var device: CastingDevice? = null
        private set

    var onPin = Event1<CastingDevice>();
    val onConnect = Event1<CastingDevice>();

    constructor(view: View) : super(view) {
        _root = view.findViewById(R.id.layout_root);
        _layoutDevice = view.findViewById(R.id.layout_device);
        _imageDevice = view.findViewById(R.id.image_device);
        _textName = view.findViewById(R.id.text_name);
        _textType = view.findViewById(R.id.text_type);
        _textNotReady = view.findViewById(R.id.text_not_ready);
        _imageLoader = view.findViewById(R.id.image_loader);
        _imageOnline = view.findViewById(R.id.image_online);
        _imagePin = view.findViewById(R.id.image_pin);

        val d = _imageLoader.drawable;
        if (d is Animatable) {
            _animatableLoader = d;
        }

        val connect = {
            device?.let { dev ->
                if (dev.isReady) {
                    StateCasting.instance.activeDevice?.stopCasting()
                    StateCasting.instance.connectDevice(dev)
                    onConnect.emit(dev)
                } else {
                    try {
                        view.context?.let { UIDialogs.toast(it, "Device not ready, may be offline") }
                    } catch (e: Throwable) {
                        //Ignored
                    }
                }
            }
        }

        _textName.setOnClickListener { connect() };
        _textType.setOnClickListener { connect() };
        _layoutDevice.setOnClickListener { connect() };

        _imagePin.setOnClickListener {
            val dev = device ?: return@setOnClickListener;
            onPin.emit(dev);
        }
    }

    fun bind(d: CastingDevice, isOnlineDevice: Boolean, isPinnedDevice: Boolean) {
        if (d is ChromecastCastingDevice) {
            _imageDevice.setImageResource(R.drawable.ic_chromecast);
            _textType.text = "Chromecast";
        } else if (d is AirPlayCastingDevice) {
            _imageDevice.setImageResource(R.drawable.ic_airplay);
            _textType.text = "AirPlay";
        } else if (d is FCastCastingDevice) {
            _imageDevice.setImageResource(R.drawable.ic_fc);
            _textType.text = "FCast";
        }

        _textName.text = d.name;
        _imageOnline.visibility = if (isOnlineDevice && d.isReady) View.VISIBLE else View.GONE

        if (!d.isReady) {
            _imageLoader.visibility = View.GONE;
            _textNotReady.visibility = View.VISIBLE;
            _imagePin.visibility = View.GONE;
        } else {
            _textNotReady.visibility = View.GONE;

            val dev = StateCasting.instance.activeDevice;
            if (dev == d) {
                if (dev.connectionState == CastConnectionState.CONNECTED) {
                    _imageLoader.visibility = View.GONE;
                    _textNotReady.visibility = View.GONE;
                    _imagePin.visibility = View.VISIBLE;
                } else {
                    _imageLoader.visibility = View.VISIBLE;
                    _textNotReady.visibility = View.GONE;
                    _imagePin.visibility = View.VISIBLE;
                }
            } else {
                if (d.isReady) {
                    _imageLoader.visibility = View.GONE;
                    _textNotReady.visibility = View.GONE;
                    _imagePin.visibility = View.VISIBLE;
                } else {
                    _imageLoader.visibility = View.GONE;
                    _textNotReady.visibility = View.VISIBLE;
                    _imagePin.visibility = View.VISIBLE;
                }
            }

            _imagePin.setImageResource(if (isPinnedDevice) R.drawable.keep_24px else R.drawable.ic_pin)

            if (_imageLoader.isVisible) {
                _animatableLoader?.start();
            } else {
                _animatableLoader?.stop();
            }
        }

        device = d;
    }
}