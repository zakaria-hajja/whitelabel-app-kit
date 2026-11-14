package com.whitelabel.core.analytics

/**
 * An in-memory [Analytics] for apps' tests: it records every event and user property in order.
 * Bind it in a test instead of a hand-written fake.
 */
class RecordingAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    val userProperties = mutableListOf<UserProperty>()

    /** The latest value of each user property, as the SDK would hold it. */
    val userPropertyValues: Map<String, String> get() = userProperties.associate { it.name to it.value }

    override fun track(event: AnalyticsEvent) {
        events += event
    }

    override fun setUserProperty(property: UserProperty) {
        userProperties += property
    }
}
