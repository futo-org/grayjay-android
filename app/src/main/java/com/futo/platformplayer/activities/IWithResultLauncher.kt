package com.futo.platformplayer.activities

import android.content.Intent
import androidx.activity.result.ActivityResult

interface IWithResultLauncher {
    fun launchForResult(intent: Intent, code: Int, handler: (ActivityResult)->Unit);
}