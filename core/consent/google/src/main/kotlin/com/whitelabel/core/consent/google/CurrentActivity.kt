package com.whitelabel.core.consent.google

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/** Tracks the resumed activity (held weakly), because UMP needs one to show its form. */
class CurrentActivity : Application.ActivityLifecycleCallbacks {
    private var resumed: WeakReference<Activity>? = null

    /** The resumed activity, or null when none is resumed or it is finishing. */
    fun get(): Activity? = resumed?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
