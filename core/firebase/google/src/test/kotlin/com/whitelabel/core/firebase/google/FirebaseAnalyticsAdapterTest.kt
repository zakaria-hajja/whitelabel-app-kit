package com.whitelabel.core.firebase.google

import assertk.assertThat
import assertk.assertions.containsExactly
import com.whitelabel.core.analytics.UserProperty
import org.junit.jupiter.api.Test

class FirebaseAnalyticsAdapterTest {
    @Test fun `a user property goes to Firebase's setUserProperty under its name`() {
        val sent = mutableListOf<Pair<String, String>>()
        val adapter = FirebaseAnalyticsAdapter(logEvent = { _, _ -> }, sendUserProperty = { name, value -> sent += name to value })
        adapter.setUserProperty(UserProperty("garden_size_bucket", "3_5"))
        assertThat(sent).containsExactly("garden_size_bucket" to "3_5")
    }
}
