package com.futo.platformplayer.views.fields

import android.content.Context
import android.util.AttributeSet
import android.widget.*
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event2
import java.lang.reflect.Field
import java.lang.reflect.Method

class ReadOnlyTextField : TableRow, IField {
    override var descriptor : FormField? = null;
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
    private val _value : TextView;

    override val onChanged = Event2<IField, Any>();

    override var reference: Any? = null;
    constructor(context : Context, attrs : AttributeSet? = null) : super(context, attrs){
        inflate(context, R.layout.field_readonly_text, this);
        _title = findViewById(R.id.field_title);
        _value = findViewById(R.id.field_value);
    }

    override fun fromField(obj : Any, field : Field, formField: FormField?) : ReadOnlyTextField {
        this._field = field;
        this._obj = obj;

        val attrField = formField ?: field.getAnnotation(FormField::class.java);
        if(attrField != null) {
            _title.text = attrField.title;
            descriptor = attrField;
        }
        else
            _title.text = field.name;

        if(field.type == String::class.java)
            _value.text = field.get(obj) as String;
        else
            _value.text = field.get(obj).toString();
        return this;
    }
    fun fromProp(obj : Any, field : Method, formField: FormField?) : ReadOnlyTextField {
        this._field = null;
        this._obj = obj;

        val attrField = formField ?: field.getAnnotation(FormField::class.java);
        if(attrField != null) {
            _title.text = attrField.title;
            descriptor = attrField;
        }
        else
            _title.text = field.name;

        if(field.returnType == String::class.java)
            _value.text = field.invoke(obj) as String;
        else
            _value.text = field.invoke(obj)?.toString() ?: "";
        return this;
    }
    override fun setField() {
    }
}