package com.whitelabel.core.analytics

/** Writes each event as `name {k=v, …}` (params sorted by key) to [log]. */
class LoggingAnalytics(private val log: (String) -> Unit) : Analytics {
    override fun track(event: AnalyticsEvent) {
        val params = event.params.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}=${it.value}" }
        log("${event.name} {$params}")
    }

    /** Writes `user_property name=value`. */
    override fun setUserProperty(property: UserProperty) = log("user_property ${property.name}=${property.value}")
}

/** Writes each recorded failure to [log]. */
class LoggingCrashReporter(private val log: (String, Throwable) -> Unit) : CrashReporter {
    override fun record(throwable: Throwable) = log("Unexpected failure", throwable)
}
