package com.whitelabel.core.firebase.google

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.whitelabel.core.firebase.CollectionSwitch
import com.whitelabel.core.firebase.FirebaseCollection

/** The Crashlytics calls the switch needs; lets the crash rule be tested without the SDK. */
internal interface CrashCollection {
    /** Crashlytics' own (persisted) collection flag. */
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
    fun deleteUnsentReports()
}

private class CrashlyticsCollection(private val crashlytics: FirebaseCrashlytics) : CrashCollection {
    override fun isEnabled() = crashlytics.isCrashlyticsCollectionEnabled
    override fun setEnabled(enabled: Boolean) = crashlytics.setCrashlyticsCollectionEnabled(enabled)
    override fun deleteUnsentReports() = crashlytics.deleteUnsentReports()
}

/**
 * Crash rule: reports captured while collection was off are pre-consent and are dropped when
 * collection is first turned on; reports captured while it was on are kept (Crashlytics sends a
 * fatal crash on the next launch, so a consented session's report must survive the restart).
 * "Off" is Crashlytics' own persisted flag, read before it is set. Turning collection off deletes
 * nothing: a failed consent gather is a form or network error, not a refusal.
 */
internal class CrashRule(private val crash: CrashCollection) {
    fun apply(enabled: Boolean) {
        if (enabled && !crash.isEnabled()) crash.deleteUnsentReports()
        crash.setEnabled(enabled)
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
