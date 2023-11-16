package com.futo.platformplayer.stores.db

import androidx.room.PrimaryKey

open class ManagedDBIndex(
    @PrimaryKey(true)
    val id: Int? = null
)