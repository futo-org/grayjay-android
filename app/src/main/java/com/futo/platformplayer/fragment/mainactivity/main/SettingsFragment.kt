package com.futo.platformplayer.fragment.mainactivity.main

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.SettingsDev
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.assume
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.LoaderView
import com.futo.platformplayer.views.fields.FieldForm
import com.futo.platformplayer.views.fields.ReadOnlyTextField
import com.google.android.material.button.MaterialButton


class SettingsFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var view: FragView? = null;

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        val newView = FragView(this);
        view = newView;
        return newView;
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        _currentView = view;
        view?.onShown(parameter);
    }

    override fun onHide() {
        super.onHide();
        onClosed.emit();
    }

    override fun onDestroyMainView() {
        view = null;
        _currentView = null;
        super.onDestroyMainView();
    }

    companion object {
        fun newInstance() = SettingsFragment().apply {}

        val onClosed = Event0();

        private var _currentView: FragView? = null;
        val currentView: FragView?
            get() = _currentView;

    }


    class FragView: ConstraintLayout {
        val fragment: SettingsFragment;

        private val _form: FieldForm;
        private val _buttonBack: ImageButton;
        private val _loaderView: LoaderView;

        private val _devSets: LinearLayout;
        private val _buttonDev: MaterialButton;

        private var _isFinished = false;

        lateinit var overlay: FrameLayout;

        val notifPermission = "android.permission.POST_NOTIFICATIONS";

        constructor(fragment: SettingsFragment) : super(fragment.requireContext()) {
            inflate(context, R.layout.activity_settings, this);
            this.fragment = fragment;

            val activity = fragment.activity;

            findViewById<LinearLayout>(R.id.container_topbar).isVisible = false;
            _form = findViewById(R.id.settings_form);
            _buttonBack = findViewById(R.id.button_back);
            _buttonDev = findViewById(R.id.button_dev);
            _devSets = findViewById(R.id.dev_settings);
            _loaderView = findViewById(R.id.loader);
            overlay = findViewById(R.id.overlay_container);

            _form.onChanged.subscribe { field, _ ->
                Logger.i("SettingsActivity", "Setting [${field.field?.name}] changed, saving");
                _form.setObjectValues();
                Settings.instance.save();

                if(field.descriptor?.id == "app_language") {
                    Logger.i("SettingsActivity", "App language change detected, propogating to shared preferences");
                    StateApp.instance.setLocaleSetting(context, Settings.instance.language.getAppLanguageLocaleString());
                }

                if(field.descriptor?.id == "background_update" && activity is MainActivity) {
                    Logger.i("SettingsActivity", "Detected change in background work ${field.value}");
                    if(Settings.instance.subscriptions.subscriptionsBackgroundUpdateInterval > 0) {
                        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
                        if(!notifManager.areNotificationsEnabled()) {
                            UIDialogs.toast(context, "Notifications aren't enabled");
                            activity.requestNotificationPermissions("Notifications need to be enabled for background updating to function")
                        }
                    }
                }
            };
            _buttonBack.setOnClickListener {
                //finish();
            }

            _buttonDev.setOnClickListener {
                //startActivity(Intent(this, DeveloperActivity::class.java));
                fragment.navigate<DeveloperFragment>(null, true);
            }

            //_lastActivity = this;

            reloadSettings();
        }

        var isFirstLoad = true;
        fun reloadSettings() {
            val firstLoad = isFirstLoad;
            isFirstLoad = false;
            _form.setSearchVisible(false);
            _loaderView.start();
            _form.fromObject(fragment.lifecycleScope, Settings.instance) {
                _loaderView.stop();
                _form.setSearchVisible(true);

                var devCounter = 0;
                _form.findField("code")?.assume<ReadOnlyTextField>()?.setOnClickListener {
                    devCounter++;
                    if(devCounter > 5) {
                        devCounter = 0;
                        SettingsDev.instance.developerMode = true;
                        SettingsDev.instance.save();
                        updateDevMode();
                        UIDialogs.toast(context, fragment.getString(R.string.you_are_now_in_developer_mode));
                    }
                };

                /*
                if(firstLoad) {
                    val query = intent.getStringExtra("query");
                    if(!query.isNullOrEmpty()) {
                        _form.setSearchQuery(query);
                    }
                }*/
            };
        }


        fun onShown(str: Any? = null) {
            updateDevMode();
            if(str is String)
                _form.setSearchQuery(str);
        }

        fun updateDevMode() {
            if(SettingsDev.instance.developerMode)
                _devSets.visibility = View.VISIBLE;
            else
                _devSets.visibility = View.GONE;
        }

    }
}