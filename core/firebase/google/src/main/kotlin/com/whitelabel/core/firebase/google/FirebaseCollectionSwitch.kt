package com.whitelabel.core.firebase.google

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.whitelabel.core.firebase.CollectionSwitch
import com.whitelabel.core.firebase.FirebaseCollection

/** Turns the Firebase SDKs' collection on or off. The manifest starts everything off. */
class FirebaseCollectionSwitch(
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
) : CollectionSwitch {
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
        crashlytics.setCrashlyticsCollectionEnabled(collection.crashes)
    }
}
