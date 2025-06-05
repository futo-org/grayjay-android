package com.futo.platformplayer.views.fields

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TableRow
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event3
import com.futo.platformplayer.logging.Logger
import java.lang.reflect.Field

class DropdownField : TableRow, IField {
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

    private var _options : Array<String> = arrayOf("Unset");
    private var _selected : Int = 0;

    private var _isInitFire : Boolean = false;

    private val _title : TextView;
    private val _description : TextView;
    private val _spinner : Spinner;

    override var reference: Any? = null;

    override var isAdvanced: Boolean = false;

    override val onChanged = Event3<IField, Any, Any>();

    override val value: Any? get() = _selected;

    override val searchContent: String?
        get() = "${_title.text} ${_description.text}";

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs){
        inflate(context, R.layout.field_dropdown, this);
        _spinner = findViewById(R.id.field_spinner);
        _title = findViewById(R.id.field_title);
        _description = findViewById(R.id.field_description);

        _isInitFire = true;
        _spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if(_isInitFire) {
                    _isInitFire = false;
                    return;
                }
                Logger.i("DropdownField", "Changed: ${_selected} -> ${pos}");
                val old = _selected;
                _selected = pos;
                onChanged.emit(this@DropdownField, pos, old);
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        };
    }

    override fun setValue(value: Any) {
        if(value is Int) {
            _spinner.setSelection(value);
        }
    }

    fun asBoolean(name: String, description: String?, obj: Boolean) : DropdownField {
        _options = resources.getStringArray(R.array.enabled_disabled_array);
        _spinner.adapter = ArrayAdapter<String>(context, R.layout.spinner_item_simple, _options).also {
            it.setDropDownViewResource(R.layout.spinner_dropdownitem_simple);
        };
        _selected = if(obj) 1 else 0;
        _spinner.isSelected = false;
        _spinner.setSelection(_selected, true);

        _title.text = name;
        if(!description.isNullOrBlank()) {
            _description.text = description;
            _description.visibility = View.VISIBLE;
        }
        else
            _description.visibility = View.GONE;

        return this;
    }

    fun withValue(title: String, description: String?, options: List<String>, value: Int): DropdownField {
        _title.text = title;
        _description.visibility = if(description.isNullOrEmpty()) View.GONE else View.VISIBLE;
        _description.text = description ?: "";

        _options = options.toTypedArray();
        _spinner.adapter = ArrayAdapter<String>(context, R.layout.spinner_item_simple, _options).also {
            it.setDropDownViewResource(R.layout.spinner_dropdownitem_simple);
        };

        _selected = value;
        _spinner.isSelected = false;
        _spinner.setSelection(_selected, true);

        return this;
    }

    override fun fromField(obj: Any, field: Field, formField: FormField?, advanced: Boolean) : DropdownField {
        this._field = field;
        this._obj = obj;

        val attrField = formField ?: field.getAnnotation(FormField::class.java);
        if(attrField != null) {
            _title.text = context.getString(attrField.title);
            descriptor = attrField;

            if(attrField.subtitle != -1) {
                _description.text = context.getString(attrField.subtitle);
                _description.visibility = View.VISIBLE;
            }
            else
                _description.visibility = View.GONE;
        }
        else {
            _title.text = field.name;
            _description.visibility = View.GONE;
        }

        val advancedFieldAttr = field.getAnnotation(AdvancedField::class.java)
        if(advancedFieldAttr != null || advanced)
            isAdvanced = true;

        _options = (field.getAnnotation(DropdownFieldOptions::class.java)?.options ?:
            field.getAnnotation(DropdownFieldOptionsId::class.java)?.optionsId?.let { resources.getStringArray(it) } ?:
            arrayOf("Unset"))
            .toList().toTypedArray();

        _spinner.adapter = ArrayAdapter(context, R.layout.spinner_item_simple, _options).also {
            it.setDropDownViewResource(R.layout.spinner_dropdownitem_simple);
        };

        _selected = if(field.type == Int::class.java) {
            field.get(obj) as Int;
        } else {
            val valStr = field.get(obj)?.toString();
            if (_options.contains(valStr)) _options.indexOf(valStr) else 0;
        }

        _spinner.isSelected = false;
        _spinner.setSelection(_selected, true);
        return this;
    }
    override fun setField() {
        if(this._field == null)
            throw java.lang.IllegalStateException("Can only setField if fromField is used");

        if(_field?.type == Int::class.java)
            _field!!.set(_obj, _selected);
        else
            _field!!.set(_obj, _options[_selected]);
    }
}

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class DropdownFieldOptions(vararg val options : String);
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class DropdownFieldOptionsId(val optionsId : Int);