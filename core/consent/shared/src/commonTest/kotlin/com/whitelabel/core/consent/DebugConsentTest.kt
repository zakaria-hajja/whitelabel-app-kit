package com.whitelabel.core.consent

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class DebugConsentTest {
    @Test fun `fake consent names map to ump results`() {
        assertThat(fakeConsentResult("notRequired")).isEqualTo(UmpResult(UmpStatus.NOT_REQUIRED, true, null))
        assertThat(fakeConsentResult("declined")).isEqualTo(UmpResult(UmpStatus.OBTAINED, true, "0"))
        assertThat(fakeConsentResult("failed")).isEqualTo(null)
        assertThat(fakeConsentResult("obtained")).isEqualTo(UmpResult(UmpStatus.OBTAINED, true, "1111111111"))
        assertThat(fakeConsentResult("anything else")).isEqualTo(fakeConsentResult("obtained"))
    }

    @Test fun `every named fake state resolves as its name says`() {
        val states = DebugConsent.FAKE_STATES.associateWith { fakeConsentResult(it)?.let(::consentStateOf) ?: ConsentState.Failed }
        assertThat(states).isEqualTo(
            mapOf(
                "obtained" to ConsentState.Resolved(true, true),
                "declined" to ConsentState.Resolved(true, false),
                "failed" to ConsentState.Failed,
                "notRequired" to ConsentState.Resolved(true, true),
            ),
        )
    }
}
