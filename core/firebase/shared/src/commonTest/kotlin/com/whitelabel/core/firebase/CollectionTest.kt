package com.whitelabel.core.firebase

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.consent.ConsentState
import kotlin.test.Test

class CollectionTest {
    private val none = FirebaseCollection(analytics = false, analyticsStorage = false, crashes = false)

    @Test fun `unknown and failed collect nothing`() {
        assertThat(collectionFor(ConsentState.Unknown, debugBuild = false, debugAnalytics = true)).isEqualTo(none)
        assertThat(collectionFor(ConsentState.Failed, debugBuild = false, debugAnalytics = true)).isEqualTo(none)
    }

    @Test fun `release and fully consented collects everything`() {
        assertThat(collectionFor(ConsentState.Resolved(true, true), false, false)).isEqualTo(FirebaseCollection(true, true, true))
    }

    @Test fun `release with analytics declined still counts without identifiers`() {
        assertThat(collectionFor(ConsentState.Resolved(true, false), false, false)).isEqualTo(FirebaseCollection(true, false, true))
    }

    @Test fun `debug with the switch off collects no analytics or crashes`() {
        assertThat(collectionFor(ConsentState.Resolved(true, true), true, false)).isEqualTo(FirebaseCollection(false, true, false))
    }

    @Test fun `debug with the switch on behaves like release`() {
        assertThat(collectionFor(ConsentState.Resolved(true, true), true, true)).isEqualTo(FirebaseCollection(true, true, true))
    }
}
