package com.futo.platformplayer.tv

import android.content.Context
import android.content.res.Configuration

object TvMode {
    fun isEnabled(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION
}
