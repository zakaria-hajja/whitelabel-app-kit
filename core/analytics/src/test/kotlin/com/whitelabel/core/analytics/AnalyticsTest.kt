package com.whitelabel.core.analytics

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test

class AnalyticsTest {

    @Test fun `event name must be lowercase snake case up to 40 chars`() {
        listOf("Login", "a".repeat(41), "1x", "", "view-item").forEach { name ->
            assertFailure { AnalyticsEvent(name) }.isInstanceOf(IllegalArgumentException::class)
        }
        AnalyticsEvent("view_item")
        AnalyticsEvent("a".repeat(40))
    }

    @Test fun `logging analytics prints name and sorted params`() {
        val lines = mutableListOf<String>()
        LoggingAnalytics { lines += it }.track(AnalyticsEvent("search", mapOf("b" to "2", "a" to "1")))
        assertThat(lines).containsExactly("search {a=1, b=2}")
    }
}
