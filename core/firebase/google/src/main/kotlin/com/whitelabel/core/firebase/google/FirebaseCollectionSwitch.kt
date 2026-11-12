package com.whitelabel.core.firebase.google

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.whitelabel.core.firebase.CollectionSwitch
import com.whitelabel.core.firebase.FirebaseCollection

/** The Crashlytics calls the switch needs; lets the crash rule be tested without the SDK. */
internal interface CrashCollection {
    fun setEnabled(enabled: Boolean)
    fun deleteUnsentReports()
}

private class CrashlyticsCollection(private val crashlytics: FirebaseCrashlytics) : CrashCollection {
    override fun setEnabled(enabled: Boolean) = crashlytics.setCrashlyticsCollectionEnabled(enabled)
    override fun deleteUnsentReports() = crashlytics.deleteUnsentReports()
}

/**
 * Crash rule: Crashlytics queues reports while collection is off and sends them when it turns on.
 * So crashes from before consent are deleted when collection goes from off to on (the start of a
 * launch counts as off), and when it is turned off.
 */
internal class CrashRule(private val crash: CrashCollection) {
    private var on = false

    fun apply(enabled: Boolean) {
        if (enabled != on || !enabled) crash.deleteUnsentReports()
        crash.setEnabled(enabled)
        on = enabled
    }
}

/** Turns the Firebase SDKs' collection on or off. The manifest starts everything off. */
class FirebaseCollectionSwitch(
    private val analytics: FirebaseAnalytics,
    crashlytics: FirebaseCrashlytics,
) : CollectionSwitch {
    private val crashRule = CrashRule(CrashlyticsCollection(crashlytics))

    override fun apply(collection: FirebaseCollection) {
        analytics.setAnalyticsCollectionEnabled(collection.analytics)
        // Only analytics storage follows the user's choice; ads signals stay denied (no ads in this module).
        val storage = if (collection.analyticsStorage) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
        analytics.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to storage,
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
            ),
        )
        crashRule.apply(collection.crashes)
    }
}
