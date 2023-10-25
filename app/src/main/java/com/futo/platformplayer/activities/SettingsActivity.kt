package com.futo.platformplayer.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.*
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.views.Loader
import com.futo.platformplayer.views.fields.FieldForm
import com.futo.platformplayer.views.fields.ReadOnlyTextField
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity(), IWithResultLauncher {
    private lateinit var _form: FieldForm;
    private lateinit var _buttonBack: ImageButton;
    private lateinit var _loader: Loader;

    private lateinit var _devSets: LinearLayout;
    private lateinit var _buttonDev: MaterialButton;

    private var _isFinished = false;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setNavigationBarColorAndIcons();

        _form = findViewById(R.id.settings_form);
        _buttonBack = findViewById(R.id.button_back);
        _buttonDev = findViewById(R.id.button_dev);
        _devSets = findViewById(R.id.dev_settings);
        _loader = findViewById(R.id.loader);

        _form.onChanged.subscribe { field, value ->
            Logger.i("SettingsActivity", "Setting [${field.field?.name}] changed, saving");
            _form.setObjectValues();
            Settings.instance.save();
        };
        _buttonBack.setOnClickListener {
            finish();
        }

        _buttonDev.setOnClickListener {
            startActivity(Intent(this, DeveloperActivity::class.java));
        }

        _lastActivity = this;

        reloadSettings();
    }

    fun reloadSettings() {
        _loader.start();
        _form.fromObject(lifecycleScope, Settings.instance) {
            _loader.stop();

            var devCounter = 0;
            _form.findField("code")?.assume<ReadOnlyTextField>()?.setOnClickListener {
                devCounter++;
                if(devCounter > 5) {
                    devCounter = 0;
                    SettingsDev.instance.developerMode = true;
                    SettingsDev.instance.save();
                    updateDevMode();
                    UIDialogs.toast(this, getString(R.string.you_are_now_in_developer_mode));
                }
            };
        };
    }

    override fun onResume() {
        super.onResume()
        updateDevMode();
    }

    fun updateDevMode() {
        if(SettingsDev.instance.developerMode)
            _devSets.visibility = View.VISIBLE;
        else
            _devSets.visibility = View.GONE;
    }

    override fun finish() {
        super.finish()
        _isFinished = true;
        if(_lastActivity == this)
            _lastActivity = null;
        overridePendingTransition(R.anim.slide_lighten, R.anim.slide_out_up)
    }




    private var resultLauncherMap =  mutableMapOf<Int, (ActivityResult)->Unit>();
    private var requestCode: Int? = -1;
    private val resultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) {
            result: ActivityResult ->
        val handler = synchronized(resultLauncherMap) {
            resultLauncherMap.remove(requestCode);
        }
        if(handler != null)
            handler(result);
    };
    override fun launchForResult(intent: Intent, code: Int, handler: (ActivityResult)->Unit) {
        synchronized(resultLauncherMap) {
            resultLauncherMap[code] = handler;
        }
        requestCode = code;
        resultLauncher.launch(intent);
    }

    companion object {
        //TODO: Temporary for solving Settings issues
        @SuppressLint("StaticFieldLeak")
        private var _lastActivity: SettingsActivity? = null;

        fun getActivity(): SettingsActivity? {
            val act = _lastActivity;
            if(act != null && !act._isFinished)
                return act;
            return null;
        }
    }
}