package com.whitelabel.core.firebase.google

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.whitelabel.core.analytics.CrashReporter

/** [CrashReporter] that records to Crashlytics. Consent gating is applied around it. */
class FirebaseCrashReporter(private val crashlytics: FirebaseCrashlytics) : CrashReporter {
    override fun record(throwable: Throwable) = crashlytics.recordException(throwable)
}
