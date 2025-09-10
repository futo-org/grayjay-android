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
import com.futo.platformplayer.Settings
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.casting.CastConnectionState
import com.futo.platformplayer.casting.CastProtocolType
import com.futo.platformplayer.casting.CastingDevice
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
            StateCasting.instance.resumeVideo()
        }

        _buttonPause = findViewById(R.id.button_pause);
        _buttonPause.setOnClickListener {
            StateCasting.instance.pauseVideo()
        }

        _buttonStop = findViewById(R.id.button_stop);
        _buttonStop.setOnClickListener {
            (ownerActivity as MainActivity?)?.getFragment<VideoDetailFragment>()?.closeVideoDetails()
            StateCasting.instance.stopVideo()
        }

        _buttonNext = findViewById(R.id.button_next);
        _buttonNext.setOnClickListener {
            (ownerActivity as MainActivity?)?.getFragment<VideoDetailFragment>()?.nextVideo()
        }

        _buttonClose.setOnClickListener { dismiss(); };
        _buttonDisconnect.setOnClickListener {
            try {
                StateCasting.instance.activeDevice?.disconnect()
            } catch (e: Throwable) {
                Logger.e(TAG, "Active device failed to disconnect: $e")
            }
            dismiss();
        };

        _sliderPosition.addOnChangeListener(OnChangeListener { _, value, fromUser ->
            if (!fromUser) {
                return@OnChangeListener
            }

            StateCasting.instance.videoSeekTo(value.toDouble())
        });

        //TODO: Check if volume slider is properly hidden in all cases
        _sliderVolume.addOnChangeListener(OnChangeListener { _, value, fromUser ->
            if (!fromUser) {
                return@OnChangeListener
            }

            StateCasting.instance.changeVolume(value.toDouble())
        });

        setLoading(false);
        updateDevice();
    }

    override fun show() {
        super.show();
        Logger.i(TAG, "Dialog shown.");

        StateCasting.instance.onActiveDeviceVolumeChanged.remove(this);
        StateCasting.instance.onActiveDeviceVolumeChanged.subscribe {
            _sliderVolume.value = it.toFloat().coerceAtLeast(0.0f).coerceAtMost(_sliderVolume.valueTo);
        };

        StateCasting.instance.onActiveDeviceTimeChanged.remove(this);
        StateCasting.instance.onActiveDeviceTimeChanged.subscribe {
            _sliderPosition.value = it.toFloat().coerceAtLeast(0.0f).coerceAtMost(_sliderPosition.valueTo);
        };

        StateCasting.instance.onActiveDeviceDurationChanged.remove(this);
        StateCasting.instance.onActiveDeviceDurationChanged.subscribe {
            val dur = it.toFloat().coerceAtLeast(1.0f)
            _sliderPosition.value = _sliderPosition.value.coerceAtLeast(0.0f).coerceAtMost(dur);
            _sliderPosition.valueTo = dur
        };

        _device = StateCasting.instance.activeDevice;
        val d = _device;
        val isConnected = d != null && d.connectionState == CastConnectionState.CONNECTED;
        setLoading(!isConnected);
        StateCasting.instance.onActiveDeviceConnectionStateChanged.subscribe(this) { _, connectionState ->
            StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) { setLoading(connectionState != CastConnectionState.CONNECTED); };
            updateDevice();
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

        when (d.protocolType) {
            CastProtocolType.CHROMECAST -> {
                _imageDevice.setImageResource(R.drawable.ic_chromecast);
                _textType.text = "Chromecast";
            }
            CastProtocolType.AIRPLAY -> {
                _imageDevice.setImageResource(R.drawable.ic_airplay);
                _textType.text = "AirPlay";
            }
            CastProtocolType.FCAST -> {
                _imageDevice.setImageResource(
                    if (Settings.instance.casting.experimentalCasting) {
                        R.drawable.ic_exp_fc
                    } else {
                        R.drawable.ic_fc
                    }
                )
                _textType.text = "FCast";
            }
        }

        _textName.text = d.name;
        _sliderPosition.valueFrom = 0.0f;
        _sliderVolume.valueFrom = 0.0f;
        _sliderVolume.value = d.volume.toFloat().coerceAtLeast(0.0f).coerceAtMost(_sliderVolume.valueTo);

        val dur = d.duration.toFloat().coerceAtLeast(1.0f)
        _sliderPosition.value = d.time.toFloat().coerceAtLeast(0.0f).coerceAtMost(dur)
        _sliderPosition.valueTo = dur

        if (d.canSetVolume()) {
            _layoutVolumeAdjustable.visibility = View.VISIBLE;
            _layoutVolumeFixed.visibility = View.GONE;
        } else {
            _layoutVolumeAdjustable.visibility = View.GONE;
            _layoutVolumeFixed.visibility = View.VISIBLE;
        }

        val interactiveControls = listOf(
            _sliderPosition,
            _sliderVolume,
            _buttonPrevious,
            _buttonPlay,
            _buttonPause,
            _buttonStop,
            _buttonNext
        )

        when (d.connectionState) {
            CastConnectionState.CONNECTED -> {
                enableControls(interactiveControls)
            }
            CastConnectionState.CONNECTING, CastConnectionState.DISCONNECTED -> {
                disableControls(interactiveControls)
            }
        }
    }

    private fun enableControls(views: List<View>) {
        views.forEach { enableControl(it) }
    }

    private fun enableControl(view: View) {
        view.alpha = 1.0f
        view.isEnabled = true
    }

    private fun disableControls(views: List<View>) {
        views.forEach { disableControl(it) }
    }

    private fun disableControl(view: View) {
        view.alpha = 0.4f
        view.isEnabled = false
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