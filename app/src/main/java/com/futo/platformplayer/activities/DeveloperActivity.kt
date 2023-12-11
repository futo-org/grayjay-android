package com.futo.platformplayer.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.futo.platformplayer.*
import com.futo.platformplayer.views.fields.FieldForm
import com.futo.platformplayer.views.fields.IField

class DeveloperActivity : AppCompatActivity() {
    private lateinit var _form: FieldForm;
    private lateinit var _buttonBack: ImageButton;

    fun getField(id: String): IField? {
        return _form.findField(id);
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        DeveloperActivity._lastActivity = this;
        setContentView(R.layout.activity_dev);
        setNavigationBarColorAndIcons();

        _buttonBack = findViewById(R.id.button_back);
        _form = findViewById(R.id.settings_form);

        _form.fromObject(SettingsDev.instance);
        _form.onChanged.subscribe { _, _ ->
            _form.setObjectValues();
            SettingsDev.instance.save();
        };

        _buttonBack.setOnClickListener {
            finish();
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_lighten, R.anim.slide_out_up)
    }



    companion object {
        //TODO: Temporary for solving Settings issues
        @SuppressLint("StaticFieldLeak")
        private var _lastActivity: DeveloperActivity? = null;

        fun getActivity(): DeveloperActivity? {
            val act = _lastActivity;
            if(act != null)
                return act;
            return null;
        }
    }
}