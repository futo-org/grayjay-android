package com.futo.platformplayer.activities

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.futo.platformplayer.*
import com.futo.platformplayer.views.fields.FieldForm

class DeveloperActivity : AppCompatActivity() {
    private lateinit var _form: FieldForm;
    private lateinit var _buttonBack: ImageButton;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dev);
        setNavigationBarColorAndIcons();

        _buttonBack = findViewById(R.id.button_back);
        _form = findViewById(R.id.settings_form);

        _form.fromObject(SettingsDev.instance);
        _form.onChanged.subscribe { field, value ->
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
}