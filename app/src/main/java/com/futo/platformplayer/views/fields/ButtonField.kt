package com.futo.platformplayer.views.fields

import android.content.Context
import android.util.AttributeSet
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event2
import java.lang.reflect.Field
import java.lang.reflect.Method

class ButtonField : LinearLayout, IField {
    override var descriptor: FormField? = null;
    private var _obj : Any? = null;
    private var _method : Method? = null;

    override var reference: Any? = null;

    override val obj : Any? get() {
        if(this._obj == null)
            throw java.lang.IllegalStateException("Can only be called if fromField is used");
        return _obj;
    };
    override val field : Field? get() {
        return null;
    };

    private val _title : TextView;
    private val _subtitle : TextView;

    override val onChanged = Event2<IField, Any>();

    constructor(context : Context, attrs : AttributeSet? = null) : super(context, attrs){
        inflate(context, R.layout.field_button, this);
        _title = findViewById(R.id.field_title);
        _subtitle = findViewById(R.id.field_subtitle);

        setOnClickListener {
            if(_method?.parameterCount == 1)
                _method?.invoke(_obj, context);
            else if(_method?.parameterCount == 2)
                _method?.invoke(_obj, context, (if(context is AppCompatActivity) context.lifecycleScope else null));
            else
                _method?.invoke(_obj);
        }
    }

    fun fromMethod(obj : Any, method: Method) : ButtonField {
        this._method = method;
        this._obj = obj;

        val attrField = method.getAnnotation(FormField::class.java);
        if(attrField != null) {
            _title.text = attrField.title;
            _subtitle.text = attrField.subtitle;
            descriptor = attrField;
        }
        else
            _title.text = method.name;

        return this;
    }
    override fun fromField(obj : Any, field : Field, formField: FormField?) : ButtonField {
        throw IllegalStateException("ButtonField should only be used for methods");
    }
    override fun setField() {
    }
}