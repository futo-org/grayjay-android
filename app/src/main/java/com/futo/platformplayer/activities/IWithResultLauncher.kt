package com.futo.platformplayer.activities

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher

interface IWithResultLauncher {
    fun launchForResult(intent: Intent, code: Int, handler: (ActivityResult)->Unit);
}