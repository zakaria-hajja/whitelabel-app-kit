package com.whitelabel.core.consent

/** A [ConsentPort] for tests and previews: answers [next] at once and counts calls. */
class FakeConsentPort(var next: UmpResult?, var optionsRequired: Boolean = false) : ConsentPort {
    var gatherCalls = 0
        private set
    var optionsShown = 0
        private set

    override fun gather(onDone: (UmpResult?) -> Unit) {
        gatherCalls++
        onDone(next)
    }

    override fun privacyOptionsRequired(): Boolean = optionsRequired

    override fun showPrivacyOptions(onDone: () -> Unit) {
        optionsShown++
        onDone()
    }
}
