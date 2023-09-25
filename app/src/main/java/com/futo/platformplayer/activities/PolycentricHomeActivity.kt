package com.futo.platformplayer.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.futo.platformplayer.R
import com.futo.platformplayer.dp
import com.futo.platformplayer.selectBestImage
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.views.buttons.BigButton
import com.futo.polycentric.core.Store
import com.futo.polycentric.core.SystemState
import com.futo.polycentric.core.toURLInfoSystemLinkUrl

class PolycentricHomeActivity : AppCompatActivity() {
    private lateinit var _buttonHelp: ImageButton;
    private lateinit var _buttonNewProfile: BigButton;
    private lateinit var _buttonImportProfile: BigButton;
    private lateinit var _layoutButtons: LinearLayout;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_polycentric_home);
        setNavigationBarColorAndIcons();

        _buttonHelp = findViewById(R.id.button_help);
        _buttonNewProfile = findViewById(R.id.button_new_profile);
        _buttonImportProfile = findViewById(R.id.button_import_profile);
        _layoutButtons = findViewById(R.id.layout_buttons);
        findViewById<ImageButton>(R.id.button_back).setOnClickListener {
            finish();
        };

        for (processHandle in StatePolycentric.instance.getProcessHandles()) {
            val systemState = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(processHandle.system));
            val profileButton = BigButton(this);
            profileButton.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                this.setMargins(0, 0, 0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt());
            };
            profileButton.withPrimaryText(systemState.username);
            profileButton.withSecondaryText("Sign in to this identity");
            profileButton.onClick.subscribe {
                StatePolycentric.instance.setProcessHandle(processHandle);
                startActivity(Intent(this@PolycentricHomeActivity, PolycentricProfileActivity::class.java));
                finish();
            }

            val dp_32 = 32.dp(resources)
            val avatarUrl = systemState.avatar.selectBestImage(dp_32 * dp_32)?.toURLInfoSystemLinkUrl(processHandle, systemState.servers.toList());
            Glide.with(profileButton)
                .asBitmap()
                .load(avatarUrl)
                .placeholder(R.drawable.ic_loader)
                .fallback(R.drawable.placeholder_profile)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        profileButton.withIcon(resource, true)
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {
                        profileButton.withIcon(R.drawable.placeholder_profile)
                    }
                })

            _layoutButtons.addView(profileButton, 0);
        }

        _buttonHelp.setOnClickListener {
            startActivity(Intent(this, PolycentricWhyActivity::class.java));
        };

        _buttonNewProfile.onClick.subscribe {
            startActivity(Intent(this, PolycentricCreateProfileActivity::class.java));
            finish();
        };

        _buttonImportProfile.onClick.subscribe {
            startActivity(Intent(this, PolycentricImportProfileActivity::class.java));
            finish();
        }
    }

    companion object {
        private const val TAG = "PolycentricHomeActivity";
    }
}