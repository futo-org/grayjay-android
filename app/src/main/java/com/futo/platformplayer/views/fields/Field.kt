package com.futo.platformplayer.views.fields

import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.constructs.Event3
import java.lang.reflect.Field


@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class FormField(val title: Int, val type: String, val subtitle: Int = -1, val order: Int = 0, val id: String = "")

interface IField {
    var descriptor: FormField?;
    val obj : Any?;
    val field : Field?;

    val onChanged : Event3<IField, Any, Any>;

    var reference: Any?;

    fun fromField(obj : Any, field : Field, formField: FormField? = null) : IField;
    fun setField();

    fun setValue(value: Any);
}