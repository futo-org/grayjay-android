package com.futo.platformplayer.views.fields

import android.content.Context
import android.util.AttributeSet
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.constructs.Event3
import com.futo.platformplayer.dp
import com.futo.platformplayer.views.buttons.BigButton
import java.lang.reflect.Field
import java.lang.reflect.Method


@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class FormFieldButton(val drawable: Int = 0)

class ButtonField : BigButton, IField {
    override var descriptor: FormField? = null;
    private var _obj : Any? = null;
    private var _method : Method? = null;

    override var reference: Any? = null;

    override val value: Any? = null;

    override val searchContent: String?
        get() = "$title $description";


    override val obj : Any? get() {
        if(this._obj == null)
            throw java.lang.IllegalStateException("Can only be called if fromField is used");
        return _obj;
    };
    override val field : Field? get() {
        return null;
    };

    //private val _title : TextView;
    //private val _subtitle : TextView;

    override val onChanged = Event3<IField, Any, Any>();

    constructor(context : Context, attrs : AttributeSet? = null) : super(context, attrs){
        //inflate(context, R.layout.field_button, this);
        //_title = findViewById(R.id.field_title);
        //_subtitle = findViewById(R.id.field_subtitle);

        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            val dp5 = 5.dp(context.resources);
            setMargins(0, dp5, 0, dp5)
        };

        super.onClick.subscribe {
            if(!isEnabled)
                return@subscribe;
            if(_method?.parameterCount == 1)
                _method?.invoke(_obj, context);
            else if(_method?.parameterCount == 2)
                _method?.invoke(_obj, context, (if(context is AppCompatActivity) context.lifecycleScope else null));
            else
                _method?.invoke(_obj);
        }
    }

    override fun setValue(value: Any) {}

    fun fromMethod(obj : Any, method: Method) : ButtonField {
        this._method = method;
        this._obj = obj;

        val attrField = method.getAnnotation(FormField::class.java);
        val attrButtonField = method.getAnnotation(FormFieldButton::class.java);
        if(attrField != null) {
            super.withPrimaryText(context.getString(attrField.title))
                .withSecondaryText(if (attrField.subtitle != -1) context.getString(attrField.subtitle) else "")
                .withSecondaryTextMaxLines(2);
            descriptor = attrField;
        }
        else
            super.withPrimaryText(method.name);
        if(attrButtonField != null)
            super.withIcon(attrButtonField.drawable, false);

        return this;
    }
    override fun fromField(obj : Any, field : Field, formField: FormField?) : ButtonField {
        throw IllegalStateException("ButtonField should only be used for methods");
    }
    override fun setField() {
    }
}