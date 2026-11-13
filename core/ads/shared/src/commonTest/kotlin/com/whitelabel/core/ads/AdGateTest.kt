package com.whitelabel.core.ads

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.consent.ConsentState
import kotlin.test.Test
import kotlin.time.Instant

class AdGateTest {
    private val now = Instant.fromEpochSeconds(1_000)
    private val granted = ConsentState.Resolved(canRequestAds = true, analyticsAllowed = true)

    private fun gate(
        adsEnabled: Boolean = true,
        declared: Boolean = true,
        consent: ConsentState = granted,
        launch: Int = 2,
        adFreeUntil: Instant? = null,
        hiddenByApp: Boolean = false,
    ) = adGate(adsEnabled, declared, hiddenByApp, consent, launch, adFreeUntil, now)

    private fun hide(reason: HideReason) = AdDecision.Hide(reason)

    @Test fun `all rules pass shows`() = assertThat(gate()).isEqualTo(AdDecision.Show)
    @Test fun `ads off hides`() = assertThat(gate(adsEnabled = false)).isEqualTo(hide(HideReason.ADS_OFF))
    @Test fun `undeclared hides`() = assertThat(gate(declared = false)).isEqualTo(hide(HideReason.UNDECLARED))
    @Test fun `unknown consent is pending`() =
        assertThat(gate(consent = ConsentState.Unknown)).isEqualTo(hide(HideReason.CONSENT_PENDING))
    @Test fun `failed consent hides`() =
        assertThat(gate(consent = ConsentState.Failed)).isEqualTo(hide(HideReason.CONSENT_FAILED))
    @Test fun `no ads consent hides`() =
        assertThat(gate(consent = ConsentState.Resolved(false, true))).isEqualTo(hide(HideReason.CONSENT_NO_ADS))
    @Test fun `first launch hides`() = assertThat(gate(launch = 1)).isEqualTo(hide(HideReason.FIRST_LAUNCH))
    @Test fun `launch zero hides as first launch`() =
        assertThat(gate(launch = 0)).isEqualTo(hide(HideReason.FIRST_LAUNCH))
    @Test fun `second launch shows`() = assertThat(gate(launch = 2)).isEqualTo(AdDecision.Show)
    @Test fun `active ad-free window hides`() =
        assertThat(gate(adFreeUntil = Instant.fromEpochSeconds(1_001))).isEqualTo(hide(HideReason.AD_FREE))
    @Test fun `window ending exactly now shows`() = assertThat(gate(adFreeUntil = now)).isEqualTo(AdDecision.Show)
    @Test fun `expired window shows`() =
        assertThat(gate(adFreeUntil = Instant.fromEpochSeconds(5))).isEqualTo(AdDecision.Show)

    @Test fun `ads off beats undeclared`() =
        assertThat(gate(adsEnabled = false, declared = false)).isEqualTo(hide(HideReason.ADS_OFF))
    @Test fun `undeclared beats consent`() =
        assertThat(gate(declared = false, consent = ConsentState.Failed)).isEqualTo(hide(HideReason.UNDECLARED))
    @Test fun `consent beats first launch`() =
        assertThat(gate(consent = ConsentState.Unknown, launch = 1)).isEqualTo(hide(HideReason.CONSENT_PENDING))
    @Test fun `first launch beats ad-free`() =
        assertThat(gate(launch = 1, adFreeUntil = Instant.fromEpochSeconds(9_999)))
            .isEqualTo(hide(HideReason.FIRST_LAUNCH))

    @Test fun `app hidden hides`() = assertThat(gate(hiddenByApp = true)).isEqualTo(hide(HideReason.APP_HIDDEN))
    @Test fun `ads off beats app hidden`() =
        assertThat(gate(adsEnabled = false, hiddenByApp = true)).isEqualTo(hide(HideReason.ADS_OFF))
    @Test fun `undeclared beats app hidden`() =
        assertThat(gate(declared = false, hiddenByApp = true)).isEqualTo(hide(HideReason.UNDECLARED))
    @Test fun `app hidden beats pending consent`() =
        assertThat(gate(consent = ConsentState.Unknown, hiddenByApp = true)).isEqualTo(hide(HideReason.APP_HIDDEN))
}
