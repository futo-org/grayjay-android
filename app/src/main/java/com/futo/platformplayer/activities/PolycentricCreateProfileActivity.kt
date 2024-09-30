package com.futo.platformplayer.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.fullyBackfillServersAnnounceExceptions
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.polycentric.PolycentricStorage
import com.futo.platformplayer.setNavigationBarColorAndIcons
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.views.LoaderView
import com.futo.polycentric.core.ProcessHandle
import com.futo.polycentric.core.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PolycentricCreateProfileActivity : AppCompatActivity() {
    private lateinit var _buttonHelp: ImageButton;
    private lateinit var _profileName: EditText;
    private lateinit var _buttonCreate: LinearLayout;
    private lateinit var _loader: LoaderView;
    private val TAG = "PolycentricCreateProfileActivity";

    private var _creating = false;

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_polycentric_create_profile);
        setNavigationBarColorAndIcons();

        _buttonHelp = findViewById(R.id.button_help);
        _profileName = findViewById(R.id.edit_profile_name);
        _buttonCreate = findViewById(R.id.button_create_profile);
        _loader = findViewById(R.id.loader);
        findViewById<ImageButton>(R.id.button_back).setOnClickListener {
            finish();
        };

        _buttonHelp.setOnClickListener {
            startActivity(Intent(this, PolycentricWhyActivity::class.java));
        };

        _buttonCreate.setOnClickListener {
            if (_creating) {
                return@setOnClickListener;
            }

            _creating = true;

            try {
                val username = _profileName.text.toString();
                if (username.length < 3) {
                    UIDialogs.toast(this@PolycentricCreateProfileActivity, getString(R.string.must_be_at_least_3_characters_long));
                    return@setOnClickListener;
                }

                _profileName.isEnabled = false;
                _buttonCreate.visibility = View.GONE;
                _loader.start();
                _loader.visibility = View.VISIBLE;
                
                lifecycleScope.launch(Dispatchers.IO) {
                    val processHandle: ProcessHandle;
                    try {
                        try {
                            processHandle = ProcessHandle.create();
                            Store.instance.addProcessSecret(processHandle.processSecret);

                            try {
                                PolycentricStorage.instance.addProcessSecret(processHandle.processSecret)
                            } catch (e: Throwable) {
                                Logger.e(TAG, "Failed to save process secret to secret storage.", e)
                            }

                            processHandle.addServer(PolycentricCache.SERVER);
                            processHandle.setUsername(username);
                            StatePolycentric.instance.setProcessHandle(processHandle);
                        } catch (e: Throwable) {
                            Logger.e(TAG, getString(R.string.failed_to_create_profile), e);
                            return@launch;
                        } finally {
                            _creating = false;
                        }

                        try {
                            Logger.i(TAG, "Started backfill");
                            processHandle.fullyBackfillServersAnnounceExceptions();
                            Logger.i(TAG, "Finished backfill");
                        } catch (e: Throwable) {
                            Logger.e(TAG, getString(R.string.failed_to_fully_backfill_servers), e);
                        }
                    }
                    finally {
                        withContext(Dispatchers.Main) {
                            _profileName.isEnabled = true;
                            _buttonCreate.visibility = View.VISIBLE;
                            _loader.stop();
                            _loader.visibility = View.GONE;
                        }
                    }

                    withContext(Dispatchers.Main) {
                        startActivity(Intent(this@PolycentricCreateProfileActivity, PolycentricProfileActivity::class.java));
                        finish();
                    }
                }
            } finally {
                _creating = false;
            }
        };
    }
}