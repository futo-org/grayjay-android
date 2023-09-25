package com.futo.platformplayer.views.fields

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.*
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event2
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

    override var reference: Any? = null;

    override val onChanged = Event2<IField, Any>();

    constructor(context : Context, attrs : AttributeSet? = null) : super(context, attrs){
        inflate(context, R.layout.field_toggle, this);
        _toggle = findViewById(R.id.field_toggle);
        _title = findViewById(R.id.field_title);
        _description = findViewById(R.id.field_description);

        _toggle.onValueChanged.subscribe {
            onChanged.emit(this, it);
        };
    }

    fun withValue(title: String, description: String?, value: Boolean): ToggleField {

        _title.text = title;
        _description.text = description;
        if(!description.isNullOrEmpty())
            _description.visibility = View.VISIBLE;
        else
            _description.visibility = View.GONE;

        _toggle.setValue(value, true);

        return this;
    }

    override fun fromField(obj : Any, field : Field, formField: FormField?) : ToggleField {
        this._field = field;
        this._obj = obj;

        val attrField = formField ?: field.getAnnotation(FormField::class.java);
        if(attrField != null) {
            _title.text = attrField.title;
            descriptor = attrField;
        }
        else
            _title.text = field.name;

        if(attrField?.subtitle?.isEmpty() != false)
            _description.visibility = View.GONE;
        else {
            _description.text = attrField.subtitle;
            _description.visibility = View.VISIBLE;
        }

        val value = field.get(obj);
        if(value is Boolean)
            _toggle.setValue(value, true);
        else if(value is Number)
            _toggle.setValue((value as Number).toInt() > 0, true);
        else if(value == null)
            _toggle.setValue(false, true);
        else
            _toggle.setValue(false, true);

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