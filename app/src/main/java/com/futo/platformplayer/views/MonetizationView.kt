package com.futo.platformplayer.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.views.platform.PlatformIndicator

class MonetizationView : LinearLayout {
    private val _buttonMembership: LinearLayout;
    private val _membershipPlatform: PlatformIndicator;
    private var _membershipUrl: String? = null;

    private val _root: LinearLayout;

    val onUrlTap = Event1<String>();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_monetization, this);
        _buttonMembership = findViewById(R.id.button_membership);
        _membershipPlatform = findViewById(R.id.membership_platform);
        _buttonMembership.setOnClickListener {
            _membershipUrl?.let {
                onUrlTap.emit(it);
            }
        }

        _root = findViewById(R.id.root);
        setPlatformMembership(null, null);
    }

    fun setPlatformMembership(pluginId: String?, url: String? = null) {
        if(pluginId.isNullOrEmpty() || url.isNullOrEmpty()) {
            _root.visibility = View.GONE;
            _membershipUrl = null;
        }
        else {
            _membershipUrl = url;
            _membershipPlatform.setPlatformFromClientID(pluginId);
            _root.visibility = View.VISIBLE;
        }
    }

    companion object {
        const val TAG = "MonetizationView";
    }
}
