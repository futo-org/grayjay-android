package com.futo.platformplayer.activities

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.futo.platformplayer.R
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricChannelProfile
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.views.LoaderView
import com.futo.platformplayer.views.buttons.BigButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PolycentricHomeActivity : AppCompatActivity() {
    private lateinit var _buttonHelp: ImageButton;
    private lateinit var _buttonNewProfile: BigButton;
    private lateinit var _buttonImportProfile: BigButton;
    private lateinit var _layoutButtons: LinearLayout;
    private lateinit var _scroll: ScrollView;
    private lateinit var _loader: LoaderView;

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_polycentric_home);
        setNavigationBarColorAndIcons();

        _buttonHelp = findViewById(R.id.button_help);
        _buttonNewProfile = findViewById(R.id.button_new_profile);
        _buttonImportProfile = findViewById(R.id.button_import_profile);
        _layoutButtons = findViewById(R.id.layout_buttons);
        _scroll = findViewById(R.id.scroll);
        _loader = findViewById(R.id.loader);

        // TODO: repurpose this for Harbor's pairing concept
        _buttonImportProfile.visibility = View.GONE;

        findViewById<ImageButton>(R.id.button_back).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java));
            finish();
        };

        _buttonHelp.setOnClickListener {
            startActivity(Intent(this, PolycentricWhyActivity::class.java));
        };

        _buttonNewProfile.onClick.subscribe {
            startActivity(Intent(this, PolycentricCreateProfileActivity::class.java));
            finish();
        };

        _loader.start();
        _loader.visibility = View.VISIBLE;

        lifecycleScope.launch(Dispatchers.IO) {
            val profiles = try {
                StatePolycentric.instance.listIdentities().mapNotNull { identity ->
                    try {
                        identity to StatePolycentric.instance.getIdentityProfile(identity)
                    } catch (e: Throwable) {
                        Logger.w(TAG, "Failed to load profile for identity.", e)
                        null
                    }
                }
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to list identities.", e)
                listOf()
            }

            withContext(Dispatchers.Main) {
                _loader.stop();
                _loader.visibility = View.GONE;
                showProfiles(profiles);
            }
        }
    }

    private fun showProfiles(profiles: List<Pair<String, PolycentricChannelProfile>>) {
        for ((identity, profile) in profiles) {
            val profileButton = BigButton(this);
            profileButton.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                this.setMargins(0, 0, 0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt());
            };
            profileButton.withPrimaryText(profile.name ?: getString(R.string.unknown));
            profileButton.withSecondaryText(getString(R.string.sign_in_to_this_identity));
            profileButton.onClick.subscribe {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        StatePolycentric.instance.switchIdentity(identity);
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to switch identity.", e)
                        return@launch;
                    }

                    withContext(Dispatchers.Main) {
                        startActivity(Intent(this@PolycentricHomeActivity, PolycentricProfileActivity::class.java));
                        finish();
                    }
                }
            }

            Glide.with(profileButton)
                .asBitmap()
                .load(profile.avatarUrl)
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
        _scroll.invalidate();
    }

    companion object {
        private const val TAG = "PolycentricHomeActivity";
    }
}
