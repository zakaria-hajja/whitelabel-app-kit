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

/** Strict opt-in (`analyticsOptIn = true`): a decline turns analytics collection fully off; everything else is unchanged. */
class StrictOptInCollectionTest {
    private val none = FirebaseCollection(analytics = false, analyticsStorage = false, crashes = false)
    private val obtained = ConsentState.Resolved(canRequestAds = true, analyticsAllowed = true)
    private val declined = ConsentState.Resolved(canRequestAds = true, analyticsAllowed = false)
    // UMP's NOT_REQUIRED maps to analytics allowed (UmpResult); ads may or may not be requestable.
    private val notRequired = ConsentState.Resolved(canRequestAds = false, analyticsAllowed = true)

    private fun both(consent: ConsentState, debugBuild: Boolean, debugAnalytics: Boolean) =
        collectionFor(consent, debugBuild, debugAnalytics, analyticsOptIn = false) to
            collectionFor(consent, debugBuild, debugAnalytics, analyticsOptIn = true)

    @Test fun `obtained collects everything with the flag on or off`() {
        val (off, on) = both(obtained, debugBuild = false, debugAnalytics = false)
        assertThat(off).isEqualTo(FirebaseCollection(true, true, true))
        assertThat(on).isEqualTo(FirebaseCollection(true, true, true))
    }

    @Test fun `declined with the flag off still counts without identifiers`() {
        assertThat(both(declined, debugBuild = false, debugAnalytics = false).first).isEqualTo(FirebaseCollection(true, false, true))
    }

    @Test fun `declined with the flag on turns analytics fully off but keeps crashes following the build`() {
        assertThat(both(declined, debugBuild = false, debugAnalytics = false).second).isEqualTo(FirebaseCollection(false, false, true))
    }

    @Test fun `not required collects everything with the flag on or off`() {
        val (off, on) = both(notRequired, debugBuild = false, debugAnalytics = false)
        assertThat(off).isEqualTo(FirebaseCollection(true, true, true))
        assertThat(on).isEqualTo(FirebaseCollection(true, true, true))
    }

    @Test fun `failed and unknown collect nothing with the flag on or off`() {
        for (state in listOf(ConsentState.Failed, ConsentState.Unknown)) {
            val (off, on) = both(state, debugBuild = false, debugAnalytics = true)
            assertThat(off).isEqualTo(none)
            assertThat(on).isEqualTo(none)
        }
    }

    @Test fun `debug without the switch collects no analytics or crashes with the flag on or off`() {
        assertThat(both(obtained, debugBuild = true, debugAnalytics = false)).isEqualTo(
            FirebaseCollection(false, true, false) to FirebaseCollection(false, true, false),
        )
        assertThat(both(declined, debugBuild = true, debugAnalytics = false)).isEqualTo(
            FirebaseCollection(false, false, false) to FirebaseCollection(false, false, false),
        )
    }

    @Test fun `debug with the switch behaves like release with the flag on or off`() {
        assertThat(both(obtained, debugBuild = true, debugAnalytics = true)).isEqualTo(
            FirebaseCollection(true, true, true) to FirebaseCollection(true, true, true),
        )
        assertThat(both(declined, debugBuild = true, debugAnalytics = true)).isEqualTo(
            FirebaseCollection(true, false, true) to FirebaseCollection(false, false, true),
        )
    }
}
