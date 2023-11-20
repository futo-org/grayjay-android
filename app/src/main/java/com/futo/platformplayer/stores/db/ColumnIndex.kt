package com.futo.platformplayer.stores.db

import androidx.room.ColumnInfo

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ColumnIndex(val name: String = ColumnInfo.INHERIT_FIELD_NAME)