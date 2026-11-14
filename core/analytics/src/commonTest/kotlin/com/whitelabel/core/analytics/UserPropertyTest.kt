package com.whitelabel.core.analytics

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test

class UserPropertyTest {

    @Test fun `a lowercase snake case name up to 24 chars and a value up to 36 chars are accepted`() {
        assertThat(UserProperty("garden_size_bucket", "3_5").name).isEqualTo("garden_size_bucket")
        UserProperty("a".repeat(24), "v".repeat(36))
        UserProperty("a", "x")
    }

    @Test fun `names outside lowercase snake case up to 24 chars are rejected`() {
        listOf("", "a".repeat(25), "Garden", "1bucket", "_bucket", "garden-size", "garden size", "gärten", "😀").forEach { name ->
            assertFailure { UserProperty(name, "v") }.isInstanceOf(IllegalArgumentException::class)
        }
    }

    @Test fun `names with Firebase's reserved prefixes are rejected`() {
        listOf("firebase_x", "google_x", "ga_x").forEach { name ->
            assertFailure { UserProperty(name, "v") }.isInstanceOf(IllegalArgumentException::class)
        }
    }

    @Test fun `values that are empty or longer than 36 chars are rejected`() {
        listOf("", "v".repeat(37)).forEach { value ->
            assertFailure { UserProperty("bucket", value) }.isInstanceOf(IllegalArgumentException::class)
        }
    }

    @Test fun `the value limit counts UTF-16 units so 18 emoji fill it and 19 overflow`() {
        UserProperty("bucket", "😀".repeat(18))
        assertFailure { UserProperty("bucket", "😀".repeat(19)) }.isInstanceOf(IllegalArgumentException::class)
    }

    @Test fun `the name-value extension builds and validates the property`() {
        val recorder = RecordingAnalytics()
        recorder.setUserProperty("bucket", "1_2")
        assertThat(recorder.userProperties).containsExactly(UserProperty("bucket", "1_2"))
        assertFailure { recorder.setUserProperty("Bad", "1") }.isInstanceOf(IllegalArgumentException::class)
    }

    @Test fun `logging analytics prints the user property`() {
        val lines = mutableListOf<String>()
        LoggingAnalytics { lines += it }.setUserProperty(UserProperty("bucket", "1_2"))
        assertThat(lines).containsExactly("user_property bucket=1_2")
    }

    @Test fun `recording analytics keeps events and properties in order`() {
        val recorder = RecordingAnalytics()
        recorder.track(AnalyticsEvent("one"))
        recorder.setUserProperty(UserProperty("bucket", "a"))
        recorder.setUserProperty(UserProperty("bucket", "b"))
        assertThat(recorder.events).containsExactly(AnalyticsEvent("one"))
        assertThat(recorder.userProperties).containsExactly(UserProperty("bucket", "a"), UserProperty("bucket", "b"))
        assertThat(recorder.userPropertyValues).isEqualTo(mapOf("bucket" to "b"))
    }
}
