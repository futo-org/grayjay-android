package com.futo.platformplayer.views.fields

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TableRow
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event3
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.views.others.Toggle
import java.lang.reflect.Field

class ToggleField : TableRow, IField {
    override var descriptor: FormField? = null;
    private var _obj : Any? = null;
    private var _field : Field? = null;

    override val obj : Any? get() {
        if(this._obj == null)
            throw java.lang.IllegalStateException("Can only be called if fromField is used");
        return _obj;
    };
    override val field : Field? get() {
        if(this._field == null)
            throw java.lang.IllegalStateException("Can only be called if fromField is used");
        return _field;
    };

    private val _title : TextView;
    private val _description : TextView;
    private val _toggle : Toggle;
    private var _lastValue: Boolean = false;

    override var reference: Any? = null;
    override var isAdvanced: Boolean = false;

    override val onChanged = Event3<IField, Any, Any>();

    override val value: Any get() = _lastValue;

    override val searchContent: String?
        get() = "${_title.text} ${_description.text}";

    constructor(context : Context, attrs : AttributeSet? = null) : super(context, attrs){
        inflate(context, R.layout.field_toggle, this);
        _toggle = findViewById(R.id.field_toggle);
        _title = findViewById(R.id.field_title);
        _description = findViewById(R.id.field_description);

        _toggle.onValueChanged.subscribe {
            val lastVal = _lastValue;
            Logger.i("ToggleField", "Changed: ${lastVal} -> ${it}");
            _lastValue = it;
            onChanged.emit(this, it, lastVal);
        };
    }

    override fun setValue(value: Any) {
        if(value is Boolean)
            _toggle.setValue(value, true, true);
    }

    fun withValue(title: String, description: String?, value: Boolean): ToggleField {

        _title.text = title;
        _description.text = description;
        if(!description.isNullOrEmpty())
            _description.visibility = View.VISIBLE;
        else
            _description.visibility = View.GONE;

        _toggle.setValue(value, true);
        _lastValue = value;

        return this;
    }

    override fun fromField(obj : Any, field : Field, formField: FormField?, advanced: Boolean) : ToggleField {
        this._field = field;
        this._obj = obj;

        val attrField = formField ?: field.getAnnotation(FormField::class.java);
        if(attrField != null) {
            _title.text = context.getString(attrField.title);
            descriptor = attrField;
        }
        else
            _title.text = field.name;

        val advancedFieldAttr = field.getAnnotation(AdvancedField::class.java)
        if(advancedFieldAttr != null || advanced) {
            Logger.w("ToggleField", "Found advanced field: " + field.name);
            isAdvanced = true;
        }

        if(attrField == null || attrField.subtitle == -1)
            _description.visibility = View.GONE;
        else {
            _description.text = context.getString(attrField.subtitle);
            _description.visibility = View.VISIBLE;
        }

        val toggleValue = when (val value = field.get(obj)) {
            is Boolean -> value
            is Number -> value.toInt() > 0
            null -> false
            else -> false
        };

        _toggle.setValue(toggleValue, true);
        _lastValue = toggleValue;

        return this;
    }
    override fun setField() {
        if(this._field == null)
            throw java.lang.IllegalStateException("Can only setField if fromField is used");

        if(_field?.type == Int::class.java)
            _field!!.set(_obj, if(_toggle.value) 1 else 0);
        else if(_field?.type == Boolean::class.java)
            _field!!.set(_obj, _toggle.value);
        else
            _field!!.set(_obj, _toggle.value);
    }
}