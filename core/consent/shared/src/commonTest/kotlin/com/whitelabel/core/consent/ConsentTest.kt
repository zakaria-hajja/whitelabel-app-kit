package com.whitelabel.core.consent

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test

class ConsentTest {
    private fun consent(port: ConsentPort?) = Consent(port, TestScope())

    private fun gathered(result: UmpResult?): ConsentState {
        val c = consent(FakeConsentPort(result))
        c.gatherOnLaunch()
        return c.state.value
    }

    @Test fun `obtained with purpose 1 allows analytics`() {
        assertThat(gathered(UmpResult(UmpStatus.OBTAINED, true, "1011"))).isEqualTo(ConsentState.Resolved(true, true))
    }

    @Test fun `obtained without purpose 1 is resolved with analytics denied and ads allowed`() {
        assertThat(gathered(UmpResult(UmpStatus.OBTAINED, true, "0111"))).isEqualTo(ConsentState.Resolved(true, false))
    }

    @Test fun `not required allows analytics`() {
        assertThat(gathered(UmpResult(UmpStatus.NOT_REQUIRED, true, null))).isEqualTo(ConsentState.Resolved(true, true))
    }

    @Test fun `required after gathering means the form was dismissed`() {
        assertThat(gathered(UmpResult(UmpStatus.REQUIRED, false, null))).isEqualTo(ConsentState.Failed)
        assertThat(gathered(UmpResult(UmpStatus.UNKNOWN, false, null))).isEqualTo(ConsentState.Failed)
    }

    @Test fun `an error fails`() {
        assertThat(gathered(null)).isEqualTo(ConsentState.Failed)
    }

    @Test fun `no port resolves without ads or analytics`() {
        val c = consent(null)
        c.gatherOnLaunch()
        assertThat(c.state.value).isEqualTo(ConsentState.Resolved(canRequestAds = false, analyticsAllowed = false))
    }

    @Test fun `gathering twice in one launch asks once`() {
        val port = FakeConsentPort(UmpResult(UmpStatus.NOT_REQUIRED, true, null))
        val c = consent(port)
        c.gatherOnLaunch()
        c.gatherOnLaunch()
        assertThat(port.gatherCalls).isEqualTo(1)
    }

    @Test fun `gathering twice before the first callback asks once`() {
        val port = HoldingPort()
        val c = consent(port)
        c.gatherOnLaunch()
        c.gatherOnLaunch()
        assertThat(port.gatherCalls).isEqualTo(1)
    }

    @Test fun `state is unknown until gathering ends`() {
        val port = HoldingPort()
        val c = consent(port)
        c.gatherOnLaunch()
        assertThat(c.state.value).isEqualTo(ConsentState.Unknown)
        port.callback!!(UmpResult(UmpStatus.NOT_REQUIRED, true, null))
        assertThat(c.state.value).isEqualTo(ConsentState.Resolved(true, true))
    }

    @Test fun `privacy options follow the port after gathering and after showing`() {
        val port = FakeConsentPort(UmpResult(UmpStatus.OBTAINED, true, "1"), optionsRequired = true)
        val c = consent(port)
        assertThat(c.privacyOptionsRequired.value).isEqualTo(false)
        c.gatherOnLaunch()
        assertThat(c.privacyOptionsRequired.value).isEqualTo(true)
        port.optionsRequired = false
        c.showPrivacyOptions()
        assertThat(port.optionsShown).isEqualTo(1)
        assertThat(c.privacyOptionsRequired.value).isEqualTo(false)
    }

    private class HoldingPort : ConsentPort {
        var gatherCalls = 0
        var callback: ((UmpResult?) -> Unit)? = null
        override fun gather(onDone: (UmpResult?) -> Unit) { gatherCalls++; callback = onDone }
        override fun privacyOptionsRequired() = false
        override fun showPrivacyOptions(onDone: () -> Unit) = onDone()
    }
}
