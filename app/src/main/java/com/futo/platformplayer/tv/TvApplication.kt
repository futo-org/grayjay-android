package com.futo.platformplayer.tv

import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Bundle

class TvApplication : Application(), Application.ActivityLifecycleCallbacks {
    override fun onCreate() {
        super.onCreate()
        if (TvMode.isEnabled(this)) registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (activity !is TvActivity) TvFocusNavigation.attach(activity.window)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is TvActivity) TvFocusNavigation.attach(activity.window)
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
