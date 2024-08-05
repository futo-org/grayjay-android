package com.futo.platformplayer.listeners

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings

class AutoRotateObserver(handler: Handler, private val onChangeCallback: () -> Unit) : ContentObserver(handler) {
    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        onChangeCallback()
    }
}

class AutoRotateChangeListener(context: Context, handler: Handler, private val onAutoRotateChanged: (Boolean) -> Unit) {

    private val contentResolver = context.contentResolver
    private val autoRotateObserver = AutoRotateObserver(handler) {
        val isAutoRotateEnabled = isAutoRotateEnabled()
        onAutoRotateChanged(isAutoRotateEnabled)
    }

    init {
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            false,
            autoRotateObserver
        )
    }

    fun unregister() {
        contentResolver.unregisterContentObserver(autoRotateObserver)
    }

    private fun isAutoRotateEnabled(): Boolean {
        return Settings.System.getInt(
            contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0
        ) == 1
    }
}
