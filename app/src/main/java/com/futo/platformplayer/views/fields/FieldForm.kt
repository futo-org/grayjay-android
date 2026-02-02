package com.futo.platformplayer.views.fields

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.widget.addTextChangedListener
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.constructs.Event2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaMethod
import kotlin.streams.asStream

class FieldForm : LinearLayout {

    private val _containerSearch: FrameLayout;
    private val _editSearch: EditText;
    private val _fieldsContainer : LinearLayout;

    val onChanged = Event2<IField, Any>();

    private var _fields : List<IField> = arrayListOf();

    private var _showAdvancedSettings: Boolean = false;

    constructor(context : Context, attrs : AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.field_form, this);
        _containerSearch = findViewById(R.id.container_search);
        _editSearch = findViewById(R.id.edit_search);
        _fieldsContainer = findViewById(R.id.field_form_container);

        _editSearch.addTextChangedListener {
            updateSettingsVisibility();
        }
    }

    fun updateSettingsVisibility(group: GroupField? = null, allowEmptyGroups: Boolean = false) {
        val settings = group?.getFields() ?: _fields;
        val query = _editSearch.text.toString().lowercase();

        var groupVisible = false;
        val isGroupMatch = query.isEmpty() || group?.searchContent?.lowercase()?.contains(query) == true;
        for(field in settings) {
            if(field is GroupField) {
                if(!allowEmptyGroups)
                    updateSettingsVisibility(field);
            } else if(field is View && field.descriptor != null) {
                if(field.isAdvanced && !_showAdvancedSettings)
                {
                    field.visibility = View.GONE;
                }
                else {
                    val txt = field.searchContent?.lowercase();
                    if (txt != null) {
                        val visible = isGroupMatch || txt.contains(query);
                        field.visibility = if (visible) View.VISIBLE else View.GONE;
                        groupVisible = groupVisible || visible;
                    }
                }
            }
            else if(field is View) {
                if(field.isAdvanced && !_showAdvancedSettings)
                    field.visibility = View.GONE;
                else
                    field.visibility = VISIBLE;
            }
        }
        if(group != null) {
            group.visibility = if (groupVisible) View.VISIBLE else View.GONE;
        }
    }

    fun setShowAdvancedSettings(show: Boolean, allowEmptyGroups: Boolean = false) {
        _showAdvancedSettings = show;
        updateSettingsVisibility(null, allowEmptyGroups);
    }
    fun setSearchQuery(query: String) {
        _editSearch.setText(query);
        updateSettingsVisibility();
    }
    fun setSearchVisible(visible: Boolean) {
        _containerSearch.visibility = if(visible) View.VISIBLE else View.GONE;
        _editSearch.setText("");
    }

    fun fromObject(scope: CoroutineScope, obj : Any, onLoaded: (()->Unit)? = null) {
        _fieldsContainer.removeAllViews();

        scope.launch(Dispatchers.Default) {
            val newFields = getFieldsFromObject(context, obj);

            withContext(Dispatchers.Main) {
                for (field in newFields) {
                    if (field !is View) {
                        throw java.lang.IllegalStateException("Only views can be IFields");
                    }

                    if(field is ToggleField && field.descriptor?.id == "advancedSettings") {
                        _showAdvancedSettings = field.value as Boolean;
                    }

                    _fieldsContainer.addView(field as View);
                    field.onChanged.subscribe { a1, a2, _ ->
                        if(field is ToggleField && field.descriptor?.id == "advancedSettings") {
                            setShowAdvancedSettings((a2 as Boolean));
                        }

                        onChanged.emit(a1, a2);
                    };
                }
                _fields = newFields;

                updateSettingsVisibility();
                onLoaded?.invoke();
            }
        }
    }
    fun fromObject(obj : Any) {
        _fieldsContainer.removeAllViews();
        val newFields = getFieldsFromObject(context, obj);
        for(field in newFields) {
            if(field !is View) {
                throw java.lang.IllegalStateException("Only views can be IFields");
            }

            _fieldsContainer.addView(field as View);
            field.onChanged.subscribe { a1, a2, _ ->
                onChanged.emit(a1, a2);
            };
        }
        _fields = newFields;
    }
    fun fromPluginSettings(settings: List<SourcePluginConfig.Setting>, values: HashMap<String, String?>, groupTitle: String? = null, groupDescription: String? = null) {
        _fieldsContainer.removeAllViews();
        val newFields = getFieldsFromPluginSettings(context, settings, values, {
            setShowAdvancedSettings(it, true);
        });
        if (newFields.isEmpty()) {
            return;
        }

        if(groupTitle == null) {
            for(field in newFields) {
                val v = field.second
                if(v !is View) {
                    throw java.lang.IllegalStateException("Only views can be IFields");
                }

                finalizePluginSettingField(field.first, v, newFields);
                _fieldsContainer.addView(v);
            }
            _fields = newFields.map { it.second };
            updateSettingsVisibility(null, true);
        } else {
            for(field in newFields) {
                finalizePluginSettingField(field.first, field.second, newFields);
            }
            val group = GroupField(context, groupTitle, groupDescription)
                .withFields(newFields.map { it.second });
            _fieldsContainer.addView(group as View);
            _fields = newFields.map { it.second };
            updateSettingsVisibility(null, true);
        }
    }
    private fun finalizePluginSettingField(setting: SourcePluginConfig.Setting, field: IField, others: List<Pair<SourcePluginConfig.Setting, IField>>) {
        field.onChanged.subscribe { f, value, oldValue ->
            onChanged.emit(f, value);

            setting.warningDialog?.let {
                if(it.isNotBlank() && IField.isValueTrue(value)) {
                    UIDialogs.showDialog(context, R.drawable.ic_warning_yellow, setting.warningDialog, null, null, 0,
                        UIDialogs.Action("Cancel", {
                            f.setValue(oldValue);
                        }, UIDialogs.ActionStyle.NONE),
                        UIDialogs.Action("Ok", { }, UIDialogs.ActionStyle.PRIMARY));
                }
            }
        }
        if(setting.dependency != null) {
            val dependentField = others.firstOrNull { it.first.variableOrName == setting.dependency };
            if (dependentField == null || dependentField.second !is View) {
                (field as View).visibility = View.GONE;
            } else {
                val dependencyReady = IField.isValueTrue(dependentField.second.value);
                if (!dependencyReady) {
                    (field as View).visibility = View.GONE;
                }

                dependentField.second.onChanged.subscribe { _, value, _ ->
                    val isValid = IField.isValueTrue(value);
                    if (isValid) {
                        (field as View).visibility = View.VISIBLE;
                    } else {
                        (field as View).visibility = View.GONE;
                    }
                }
            }
        }
    }

    fun setObjectValues(){
        val fields = _fields;
        for (field in fields) {
            field.setField();
        }
    }

    fun findField(id: String) : IField? {
        for(field in _fields) {
            if(field.descriptor?.id == id) {
                return field;
            } else if(field is GroupField) {
                val subField = field.findField(id);
                if(subField != null) {
                    return subField;
                }
            }
        }
        return null;
    }

    companion object
    {
        const val DROPDOWN = "dropdown";
        const val GROUP = "group";
        const val READONLYTEXT = "readonlytext";
        const val TOGGLE = "toggle";
        const val BUTTON = "button";

        private val _json = Json;


        fun getFieldsFromPluginSettings(context: Context, settings: List<SourcePluginConfig.Setting>, values: HashMap<String, String?>, onAdvancedChanged: ((newVal: Boolean)->Unit)? = null): List<Pair<SourcePluginConfig.Setting, IField>> {
            val fields = mutableListOf<Pair<SourcePluginConfig.Setting, IField>>()

            for(setting in settings) {
                val value = if(values.containsKey(setting.variableOrName)) values[setting.variableOrName] else setting.default;

                val field = when(setting.type.lowercase()) {
                    "header" -> {
                        val groupField = GroupField(context, setting.name, setting.description);
                        groupField.isAdvanced = (setting.isAdvanced ?: false);
                        groupField;
                    }
                    "boolean" -> {
                        val field = ToggleField(context).withValue(setting.name,
                            setting.description,
                            value == "true" || value == "1" || value == "True");
                        field.onChanged.subscribe { _, v, _ ->
                            values[setting.variableOrName] = _json.encodeToString (v == 1 || v == true);
                        }
                        field.isAdvanced = (setting.isAdvanced ?: false);
                        field;
                    }
                    "dropdown" -> {
                        if(!setting.options.isNullOrEmpty()) {
                            var selected = value?.toIntOrNull()?.coerceAtLeast(0) ?: 0;
                            val field = DropdownField(context).withValue(setting.name, setting.description, setting.options, selected);
                            field.onChanged.subscribe { _, v, _ ->
                                values[setting.variableOrName] = v.toString();
                            }
                            field.isAdvanced = (setting.isAdvanced ?: false);
                            field;
                        }
                        else null;
                    }
                    else -> null;
                }

                if(field != null) {
                    fields.add(Pair(setting, field));
                }
            }

            if(onAdvancedChanged != null && settings.any { it.isAdvanced == true }) {
                val setting = SourcePluginConfig.Setting("Show Advanced", "See advanced settings, which may be counter productive to change", "boolean", "false");
                val field = ToggleField(context).withValue(setting.name, setting.description, false);

                field.onChanged.subscribe { field, new, old ->
                    onAdvancedChanged?.invoke(new as Boolean);
                }
                fields.add(Pair(setting, field));
            }

            return fields;
        }

        fun getFieldsFromObject(context : Context, obj : Any) : List<IField> {
            val objFields = obj::class.declaredMemberProperties
                .asSequence()
                .asStream()
                .filter { it.hasAnnotation<FormField>() && it.javaField != null }
                .map { Pair<KProperty<*>, FormField>(it, it.findAnnotation()!!) }

            //TODO: Rewrite fields to properties so no map is required
            val propertyMap = mutableMapOf<Field, KProperty<*>>();
            val fields = mutableListOf<IField>();
            for(prop in objFields) {
                prop.first.javaField!!.isAccessible = true;

                val advanced = prop.first.hasAnnotation<AdvancedField>();

                val field = when(prop.second.type) {
                    GROUP -> GroupField(context).fromField(obj, prop.first.javaField!!, prop.second);
                    DROPDOWN -> DropdownField(context).fromField(obj, prop.first.javaField!!, prop.second, advanced);
                    TOGGLE -> ToggleField(context).fromField(obj, prop.first.javaField!!, prop.second, advanced);
                    READONLYTEXT -> ReadOnlyTextField(context).fromField(obj, prop.first.javaField!!, prop.second);
                    else -> throw java.lang.IllegalStateException("Unknown field type ${prop.second.type} for ${prop.second.title}")
                }
                fields.add(field as IField);
                propertyMap.put(prop.first.javaField!!, prop.first);
            }

            for(field in fields) {
                if(field.field != null) {
                    val warning = propertyMap[field.field]?.findAnnotation<FormFieldWarning>();
                    if(warning != null) {
                        field.onChanged.subscribe { f, value, oldValue ->
                            if(IField.isValueTrue(value))
                                UIDialogs.showDialog(context, R.drawable.ic_warning_yellow, context.getString(warning.messageRes), null, null, 0,
                                    UIDialogs.Action("Cancel", {
                                        f.setValue(oldValue);
                                    }, UIDialogs.ActionStyle.NONE),
                                    UIDialogs.Action("Ok", {

                                    }, UIDialogs.ActionStyle.PRIMARY));
                        }
                    }
                    val hint = propertyMap[field.field]?.findAnnotation<FormFieldHint>();
                    if(hint != null){
                        field.onChanged.subscribe { f, value, oldValue ->
                            UIDialogs.appToast(context.getString(hint.messageRes), false);
                        }
                    }
                }
            }

            val objProps = obj::class.declaredMemberProperties
                .asSequence()
                .asStream()
                .filter { it.hasAnnotation<FormField>() && it.javaField == null && it.getter.javaMethod != null}
                .map { Pair<Method, FormField>(it.getter.javaMethod!!, it.findAnnotation()!!) }

            for(prop in objProps) {
                prop.first.isAccessible = true;

                val field = when(prop.second.type) {
                    READONLYTEXT -> ReadOnlyTextField(context).fromProp(obj, prop.first, prop.second);
                    else -> continue;
                }
                fields.add(field as IField);
            }

            //TODO: replace java.declaredMethods with declaredMemberFunctions instead of filtering out get/set
            val objMethods = obj::class.java.declaredMethods
                .asSequence()
                .asStream()
                .filter { it.getAnnotation(FormField::class.java) != null && !it.name.startsWith("get") && !it.name.startsWith("set") }
                .map { Pair<Method, FormField?>(it, it.getAnnotation(FormField::class.java)) }

            for(meth in objMethods) {
                if (meth.second == null) {
                    continue
                }

                meth.first.isAccessible = true;

                val field = when(meth.second!!.type) {
                    BUTTON -> ButtonField(context).fromMethod(obj, meth.first);
                    else -> throw java.lang.IllegalStateException("Unknown method type ${meth.second!!.type} for ${meth.second!!.title}")
                }
                fields.add(field as IField);
            }

            return fields.sortedBy { it.descriptor?.order }.toList();
        }
    }
}