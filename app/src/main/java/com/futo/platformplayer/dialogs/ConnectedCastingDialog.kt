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
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.casting.AirPlayCastingDevice
import com.futo.platformplayer.casting.CastConnectionState
import com.futo.platformplayer.casting.CastingDevice
import com.futo.platformplayer.casting.ChromecastCastingDevice
import com.futo.platformplayer.casting.FCastCastingDevice
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.fragment.mainactivity.main.VideoDetailFragment
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.google.android.material.slider.Slider
import com.google.android.material.slider.Slider.OnChangeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConnectedCastingDialog(context: Context?) : AlertDialog(context) {
    private lateinit var _buttonClose: Button;
    private lateinit var _imageLoader: ImageView;
    private lateinit var _imageDevice: ImageView;
    private lateinit var _textName: TextView;
    private lateinit var _textType: TextView;
    private lateinit var _buttonDisconnect: LinearLayout;
    private lateinit var _sliderVolume: Slider;
    private lateinit var _sliderPosition: Slider;
    private lateinit var _layoutVolumeAdjustable: LinearLayout;
    private lateinit var _layoutVolumeFixed: LinearLayout;

    private lateinit var _buttonPrevious: ImageButton;
    private lateinit var _buttonPlay: ImageButton;
    private lateinit var _buttonPause: ImageButton;
    private lateinit var _buttonStop: ImageButton;
    private lateinit var _buttonNext: ImageButton;

    private var _device: CastingDevice? = null;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_casting_connected, null));

        _imageLoader = findViewById(R.id.image_loader);
        _buttonClose = findViewById(R.id.button_close);
        _imageDevice = findViewById(R.id.image_device);
        _textName = findViewById(R.id.text_name);
        _textType = findViewById(R.id.text_type);
        _buttonDisconnect = findViewById(R.id.button_disconnect);
        _sliderVolume = findViewById(R.id.slider_volume);
        _sliderPosition = findViewById(R.id.slider_position);
        _layoutVolumeAdjustable = findViewById(R.id.layout_volume_adjustable);
        _layoutVolumeFixed = findViewById(R.id.layout_volume_fixed);

        _buttonPrevious = findViewById(R.id.button_previous);
        _buttonPrevious.setOnClickListener {
            (ownerActivity as MainActivity?)?.getFragment<VideoDetailFragment>()?.previousVideo()
        }

        _buttonPlay = findViewById(R.id.button_play);
        _buttonPlay.setOnClickListener {
            StateCasting.instance.activeDevice?.resumeVideo()
        }

        _buttonPause = findViewById(R.id.button_pause);
        _buttonPause.setOnClickListener {
            StateCasting.instance.activeDevice?.pauseVideo()
        }

        _buttonStop = findViewById(R.id.button_stop);
        _buttonStop.setOnClickListener {
            (ownerActivity as MainActivity?)?.getFragment<VideoDetailFragment>()?.closeVideoDetails()
            StateCasting.instance.activeDevice?.stopVideo()
        }

        _buttonNext = findViewById(R.id.button_next);
        _buttonNext.setOnClickListener {
            (ownerActivity as MainActivity?)?.getFragment<VideoDetailFragment>()?.nextVideo()
        }

        _buttonClose.setOnClickListener { dismiss(); };
        _buttonDisconnect.setOnClickListener {
            StateCasting.instance.activeDevice?.stopCasting();
            dismiss();
        };

        _sliderPosition.addOnChangeListener(OnChangeListener { _, value, fromUser ->
            if (!fromUser) {
                return@OnChangeListener
            }

            val activeDevice = StateCasting.instance.activeDevice ?: return@OnChangeListener;
            try {
                activeDevice.seekVideo(value.toDouble());
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to change volume.", e);
            }
        });

        //TODO: Check if volume slider is properly hidden in all cases
        _sliderVolume.addOnChangeListener(OnChangeListener { _, value, fromUser ->
            if (!fromUser) {
                return@OnChangeListener
            }

            val activeDevice = StateCasting.instance.activeDevice ?: return@OnChangeListener;
            if (activeDevice.canSetVolume) {
                try {
                    activeDevice.changeVolume(value.toDouble());
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to change volume.", e);
                }
            }
        });

        setLoading(false);
        updateDevice();
    }

    override fun show() {
        super.show();
        Logger.i(TAG, "Dialog shown.");

        StateCasting.instance.onActiveDeviceVolumeChanged.remove(this);
        StateCasting.instance.onActiveDeviceVolumeChanged.subscribe {
            _sliderVolume.value = it.toFloat();
        };

        StateCasting.instance.onActiveDeviceTimeChanged.remove(this);
        StateCasting.instance.onActiveDeviceTimeChanged.subscribe {
            _sliderPosition.value = it.toFloat();
        };

        StateCasting.instance.onActiveDeviceDurationChanged.remove(this);
        StateCasting.instance.onActiveDeviceDurationChanged.subscribe {
            _sliderPosition.valueTo = it.toFloat();
        };

        _device = StateCasting.instance.activeDevice;
        val d = _device;
        val isConnected = d != null && d.connectionState == CastConnectionState.CONNECTED;
        setLoading(!isConnected);
        StateCasting.instance.onActiveDeviceConnectionStateChanged.subscribe(this) { _, connectionState ->
            StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) { setLoading(connectionState != CastConnectionState.CONNECTED); };
        };

        updateDevice();
    }

    override fun dismiss() {
        super.dismiss();
        StateCasting.instance.onActiveDeviceVolumeChanged.remove(this);
        StateCasting.instance.onActiveDeviceDurationChanged.remove(this);
        StateCasting.instance.onActiveDeviceTimeChanged.remove(this);
        _device = null;
        StateCasting.instance.onActiveDeviceConnectionStateChanged.remove(this);
    }

    private fun updateDevice() {
        val d = StateCasting.instance.activeDevice ?: return;

        if (d is ChromecastCastingDevice) {
            _imageDevice.setImageResource(R.drawable.ic_chromecast);
            _textType.text = "Chromecast";
        } else if (d is AirPlayCastingDevice) {
            _imageDevice.setImageResource(R.drawable.ic_airplay);
            _textType.text = "AirPlay";
        } else if (d is FCastCastingDevice) {
            _imageDevice.setImageResource(R.drawable.ic_fc);
            _textType.text = "FastCast";
        }

        _textName.text = d.name;
        _sliderVolume.value = d.volume.toFloat();
        _sliderPosition.valueFrom = 0.0f;
        _sliderPosition.valueTo = d.duration.toFloat();
        _sliderPosition.value = d.time.toFloat();

        if (d.canSetVolume) {
            _layoutVolumeAdjustable.visibility = View.VISIBLE;
            _layoutVolumeFixed.visibility = View.GONE;
        } else {
            _layoutVolumeAdjustable.visibility = View.GONE;
            _layoutVolumeFixed.visibility = View.VISIBLE;
        }
    }

    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            _imageLoader.visibility = View.VISIBLE;
            (_imageLoader.drawable as Animatable?)?.start();
        } else {
            (_imageLoader.drawable as Animatable?)?.stop();
            _imageLoader.visibility = View.GONE;
        }
    }

    companion object {
        private val TAG = "CastingDialog";
    }
}