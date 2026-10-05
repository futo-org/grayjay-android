package com.futo.platformplayer.polycentric

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
        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            try {
                StatePolycentric.instance.awaitReady()
                val isLoggedIn = StatePolycentric.instance.isLoggedIn

                withContext(Dispatchers.Main) {
                    if (!isLoggedIn) {
                        UIDialogs.showDialog(context, R.drawable.ic_login,
                            text, null, null,
                            1,
                            UIDialogs.Action("Cancel", { }, UIDialogs.ActionStyle.ACCENT),
                            UIDialogs.Action("OK", {
                                context.startActivity(Intent(context, PolycentricHomeActivity::class.java));
                            }, UIDialogs.ActionStyle.PRIMARY)
                        );
                    } else {
                        action();
                    }
                }
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to await polycentric readiness.", e);
                withContext(Dispatchers.Main) {
                    UIDialogs.toast(context, "Failed to initialize Polycentric: ${e.message}");
                }
            }
        }
    }
}
