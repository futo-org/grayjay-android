package com.futo.platformplayer.activities

import android.app.Activity
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricChannelProfile
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.views.LoaderView
import com.futo.platformplayer.views.buttons.BigButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PolycentricHomeActivity : AppCompatActivity() {
    private lateinit var _buttonHelp: ImageButton;
    private lateinit var _buttonNewProfile: BigButton;
    private lateinit var _buttonPairDevice: BigButton;
    private lateinit var _buttonImportProfile: BigButton;
    private lateinit var _layoutButtons: LinearLayout;
    private lateinit var _scroll: ScrollView;
    private lateinit var _loader: LoaderView;
    private var _switching = false;

    private val _signInResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startActivity(Intent(this, PolycentricProfileActivity::class.java));
            finish();
        }
    };
    private val _profileButtons = arrayListOf<BigButton>();

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_polycentric_home);
        setNavigationBarColorAndIcons();

        _buttonHelp = findViewById(R.id.button_help);
        _buttonNewProfile = findViewById(R.id.button_new_profile);
        _buttonPairDevice = findViewById(R.id.button_pair_device);
        _buttonImportProfile = findViewById(R.id.button_import_profile);
        _layoutButtons = findViewById(R.id.layout_buttons);
        _scroll = findViewById(R.id.scroll);
        _loader = findViewById(R.id.loader);

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

        _buttonPairDevice.onClick.subscribe {
            _signInResultLauncher.launch(Intent(this, PolycentricPairClaimerActivity::class.java));
        };

        _buttonImportProfile.onClick.subscribe {
            _signInResultLauncher.launch(Intent(this, PolycentricImportProfileActivity::class.java));
        };

        StatePolycentric.instance.onIdentitiesChanged.subscribe(this) {
            loadProfiles();
        };

        loadProfiles();
    }

    override fun onDestroy() {
        super.onDestroy();
        StatePolycentric.instance.onIdentitiesChanged.remove(this);
    }

    private fun loadProfiles() {
        _loader.start();
        _loader.visibility = View.VISIBLE;

        lifecycleScope.launch(Dispatchers.IO) {
            val profiles = try {
                StatePolycentric.instance.listIdentities().map { identity ->
                    try {
                        identity to StatePolycentric.instance.getIdentityProfile(identity)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        // Keep the identity selectable; signing in only needs its key.
                        Logger.w(TAG, "Failed to load profile for identity.", e)
                        identity to PolycentricChannelProfile(identity, null, null, null, null)
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
        for (button in _profileButtons) {
            _layoutButtons.removeView(button);
        }
        _profileButtons.clear();

        for ((identity, profile) in profiles) {
            val profileButton = BigButton(this);
            profileButton.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                this.setMargins(0, 0, 0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt());
            };
            profileButton.withPrimaryText(profile.name ?: getString(R.string.unknown));
            profileButton.withSecondaryText(getString(R.string.sign_in_to_this_identity));
            profileButton.onClick.subscribe {
                // Ignore further taps until this switch resolves, so a double tap can't run two
                // switches and open the profile screen twice.
                if (_switching) {
                    return@subscribe;
                }
                _switching = true;

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        StatePolycentric.instance.switchIdentity(identity);
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to switch identity.", e)
                        withContext(Dispatchers.Main) {
                            UIDialogs.toast(this@PolycentricHomeActivity, getString(R.string.failed_to_switch_identity));
                            _switching = false;
                        }
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
                .into(object : CustomTarget<Bitmap>() {
                    override fun onLoadStarted(placeholder: Drawable?) {
                        profileButton.withIcon(R.drawable.placeholder_avatar, true)
                    }
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        profileButton.withIcon(resource, true)
                    }
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        // Also reached when the profile has no avatar (null model).
                        profileButton.withIcon(R.drawable.placeholder_avatar, true)
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {
                        profileButton.withIcon(R.drawable.placeholder_avatar, true)
                    }
                })

            _layoutButtons.addView(profileButton, 0);
            _profileButtons.add(profileButton);
        }
        _scroll.invalidate();
    }

    companion object {
        private const val TAG = "PolycentricHomeActivity";
    }
}
