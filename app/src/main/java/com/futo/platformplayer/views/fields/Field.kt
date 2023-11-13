package com.futo.platformplayer.views.fields

import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.constructs.Event3
import java.lang.reflect.Field


@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class FormField(val title: Int, val type: String, val subtitle: Int = -1, val order: Int = 0, val id: String = "")

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class FormFieldWarning(val messageRes: Int)

interface IField {
    var descriptor: FormField?;
    val obj : Any?;
    val field : Field?;

    val value: Any?;
    val onChanged : Event3<IField, Any, Any>;

    var reference: Any?;

    fun fromField(obj : Any, field : Field, formField: FormField? = null) : IField;
    fun setField();

    fun setValue(value: Any);

    companion object {
        fun isValueTrue(value: Any?): Boolean {
            if(value == null)
                return false;
            return when(value) {
                is Int -> value > 0;
                is Boolean -> value;
                is String -> value.toIntOrNull()?.let { it > 0 } ?: false || value.lowercase() == "true";
                else -> false
            };
        }
    }
}