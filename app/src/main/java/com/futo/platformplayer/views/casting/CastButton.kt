package com.futo.platformplayer.views.casting

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.casting.CastConnectionState
import com.futo.platformplayer.casting.StateCasting
import com.futo.platformplayer.constructs.Event1

class CastButton : androidx.appcompat.widget.AppCompatImageButton {
    var onClick = Event1<Pair<String, Any>>();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        setOnClickListener { UIDialogs.showCastingDialog(context); };

        if (!isInEditMode) {
            if (!Settings.instance.casting.enabled) {
                visibility = View.GONE;
            }

            StateCasting.instance.onActiveDeviceConnectionStateChanged.subscribe(this) { _, _ ->
                updateCastState();
            };

            updateCastState();
        }
    }

    private fun updateCastState() {
        val c = context ?: return;
        val d = StateCasting.instance.activeDevice;

        val activeColor = ContextCompat.getColor(c, R.color.colorPrimary);
        val connectingColor = ContextCompat.getColor(c, R.color.gray_c3);
        val inactiveColor = ContextCompat.getColor(c, R.color.white);

        if (d != null) {
            when (d.connectionState) {
                CastConnectionState.CONNECTED -> setColorFilter(activeColor)
                CastConnectionState.CONNECTING -> setColorFilter(connectingColor)
                CastConnectionState.DISCONNECTED -> setColorFilter(activeColor)
            }
        } else {
            setColorFilter(inactiveColor);
        }
    }

    fun cleanup() {
        setOnClickListener(null);
        StateCasting.instance.onActiveDeviceConnectionStateChanged.remove(this);
    }
}