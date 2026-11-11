package com.whitelabel.core.analytics

/** Writes each event as `name {k=v, …}` (params sorted by key) to [log]. */
class LoggingAnalytics(private val log: (String) -> Unit) : Analytics {
    override fun track(event: AnalyticsEvent) {
        val params = event.params.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}=${it.value}" }
        log("${event.name} {$params}")
    }
}

/** Writes each recorded failure to [log]. */
class LoggingCrashReporter(private val log: (String, Throwable) -> Unit) : CrashReporter {
    override fun record(throwable: Throwable) = log("Unexpected failure", throwable)
}
