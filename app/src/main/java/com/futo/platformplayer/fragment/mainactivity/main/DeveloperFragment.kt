package com.futo.platformplayer.fragment.mainactivity.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.futo.platformplayer.R
import com.futo.platformplayer.SettingsDev
import com.futo.platformplayer.views.fields.FieldForm
import com.futo.platformplayer.views.fields.IField


class DeveloperFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var view: FragView? = null;


    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val newView = FragView(this);
        view = newView;
        _currentView = view;
        return newView;
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        view?.onShown();
    }

    override fun onDestroyMainView() {
        view = null;
        _currentView = null;
        super.onDestroyMainView();
    }

    companion object {
        fun newInstance() = DeveloperFragment().apply {}

        private var _currentView: FragView? = null;
        val currentView: FragView?
            get() = _currentView;
    }


    class FragView: ConstraintLayout {
        val fragment: DeveloperFragment;

        private lateinit var _form: FieldForm;
        private lateinit var _buttonBack: ImageButton;

        private var _isFinished = false;

        lateinit var overlay: FrameLayout;

        val notifPermission = "android.permission.POST_NOTIFICATIONS";

        constructor(fragment: DeveloperFragment) : super(fragment.requireContext()) {
            inflate(context, R.layout.activity_dev, this);
            this.fragment = fragment;

            val activity = fragment.activity;
            findViewById<LinearLayout>(R.id.container_topbar).isVisible = false;

            _buttonBack = findViewById(R.id.button_back);
            _form = findViewById(R.id.settings_form);

            _form.fromObject(SettingsDev.instance);
            _form.onChanged.subscribe { _, _ ->
                _form.setObjectValues();
                SettingsDev.instance.save();
            };
        }

        fun getField(id: String): IField? {
            return _form.findField(id);
        }

        fun onShown() {

        }

    }
}