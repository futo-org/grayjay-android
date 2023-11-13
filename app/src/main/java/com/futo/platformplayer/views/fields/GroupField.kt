package com.futo.platformplayer.views.fields

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.constructs.Event3
import java.lang.reflect.Field
import kotlin.reflect.KProperty

class GroupField : LinearLayout, IField {
    override var descriptor : FormField? = null;
    private var _obj : Any? = null;
    private var _field : Field? = null;

    private var _fields : List<IField> = listOf();

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

    override val onChanged = Event3<IField, Any, Any>();

    private val _title : TextView;
    private val _subtitle : TextView;
    private val _container : LinearLayout;

    override var reference: Any? = null;

    override val value: Any? = null;

    constructor(context : Context, attrs : AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.field_group, this);
        _title = findViewById(R.id.field_group_title);
        _subtitle = findViewById(R.id.field_group_subtitle);
        _container = findViewById(R.id.field_group_container);

        _title.visibility = GONE;
    }

    constructor(context: Context, title: String, description: String? = null) : super(context) {
        inflate(context, R.layout.field_group, this);
        _title = findViewById(R.id.field_group_title);
        _subtitle = findViewById(R.id.field_group_subtitle);
        _container = findViewById(R.id.field_group_container);

        _title.text = title;
        _subtitle.text = description ?: "";

        if(!(_title.text?.isEmpty() ?: true))
            _title.visibility = VISIBLE;
        else
            _title.visibility = GONE;
        if(!(_subtitle.text?.isEmpty() ?: true))
            _subtitle.visibility = VISIBLE;
        else
            _subtitle.visibility = GONE;
    }

    fun findField(id: String) : IField? {
        for(field in _fields) {
            if(field.descriptor?.id == id)
                return field;
            else if(field is GroupField)
            {
                val subField = field.findField(id);
                if(subField != null)
                    return subField;
            }
        }
        return null;
    }

    fun withFields(fields: List<IField>): GroupField {
        _container.removeAllViews();
        val newFields = mutableListOf<IField>()
        for(field in fields) {
            if(!(field is View))
                throw java.lang.IllegalStateException("Only views can be IFields");

            field.onChanged.subscribe(onChanged::emit);
            _container.addView(field as View);
            newFields.add(field);
        }
        _fields = newFields;

        return this;
    }

    override fun fromField(obj : Any, field : Field, formField: FormField?) : GroupField {
        this._field = field;
        this._obj = obj;

        val value = field.get(obj);

        val attrField = formField ?: field.getAnnotation(FormField::class.java); //TODO: Get this to work as default
        if(attrField != null) {
            _title.text = context.getString(attrField.title);
            _subtitle.text = if (attrField.subtitle != -1) context.getString(attrField.subtitle) else "";
            descriptor = attrField;
        }
        else
            _title.text = field.name;

        _container.removeAllViews();
        val newFields = mutableListOf<IField>()
        for(field in FieldForm.getFieldsFromObject(context, value)) {
            if(!(field is View))
                throw java.lang.IllegalStateException("Only views can be IFields");

            field.onChanged.subscribe(onChanged::emit);
            _container.addView(field as View);
            newFields.add(field);
        }
        _fields = newFields;

        if(!(_title.text?.isEmpty() ?: true))
            _title.visibility = VISIBLE;
        else
            _title.visibility = GONE;
        if(!(_subtitle.text?.isEmpty() ?: true))
            _subtitle.visibility = VISIBLE;
        else
            _subtitle.visibility = GONE;
        return this;
    }
    override fun setField() {
        if(this._field == null)
            throw java.lang.IllegalStateException("Can only setField if fromField is used");

        for(field in _fields){
            field.setField();
        }
    }

    override fun setValue(value: Any) {}
}