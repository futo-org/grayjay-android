package com.futo.platformplayer.polycentric

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.PolycentricHomeActivity
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Login gate for Polycentric/Harbor actions. Ensures the adapter is ready, and then runs the given
 * action if logged-in, or interrupts with the identity log-in UI if not.
 */
object PolycentricLoginFlow {
    private const val TAG = "PolycentricLoginFlow";

    fun requireLogin(context: Context, text: String, action: () -> Unit) {
        if (!StatePolycentric.instance.enabled) {
            UIDialogs.toast(context, "Harbor is disabled")
            return
        }

        Logger.i(TAG, "requireLogin()")
        StateApp.instance.preventPictureInPicture.emit();

        // First, we see if we can immediately report that the adapter is ready, and call the next
        // screen
        if (StatePolycentric.instance.adapter.isReady) {
            runOrPrompt(context, text, StatePolycentric.instance.isLoggedIn, action)
            return
        }

        // Second, if it is not ready, we start the async process of awaiting the adapter's init
        // before the next screen
        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            try {
                StatePolycentric.instance.requireAdapter()
                val isLoggedIn = StatePolycentric.instance.isLoggedIn

                withContext(Dispatchers.Main) {
                    if (context is Activity && (context.isFinishing || context.isDestroyed)) {
                        return@withContext
                    }
                    runOrPrompt(context, text, isLoggedIn, action)
                }
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to await polycentric readiness.", e);
                withContext(Dispatchers.Main) {
                    UIDialogs.toast(context.applicationContext, "Failed to initialize Harbor: ${e.message}");
                }
            }
        }
    }

    private fun runOrPrompt(context: Context, text: String, isLoggedIn: Boolean, action: () -> Unit) {
        if (isLoggedIn) {
            action();
            return
        }

        UIDialogs.showDialog(context, R.drawable.ic_login,
            text, null, null,
            1,
            UIDialogs.Action("Cancel", { }, UIDialogs.ActionStyle.ACCENT),
            UIDialogs.Action("OK", {
                context.startActivity(Intent(context, PolycentricHomeActivity::class.java));
            }, UIDialogs.ActionStyle.PRIMARY)
        );
    }
}
