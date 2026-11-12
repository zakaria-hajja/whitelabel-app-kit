package com.whitelabel.core.consent

enum class UmpStatus { UNKNOWN, REQUIRED, NOT_REQUIRED, OBTAINED }

/** The outcome of one UMP gather, as reported by the platform. [purposeConsents] is the TCF purpose-consent string. */
data class UmpResult(val status: UmpStatus, val canRequestAds: Boolean, val purposeConsents: String?)

fun consentStateOf(result: UmpResult): ConsentState = when (result.status) {
    UmpStatus.OBTAINED -> ConsentState.Resolved(result.canRequestAds, storageAllowed(result.purposeConsents))
    UmpStatus.NOT_REQUIRED -> ConsentState.Resolved(result.canRequestAds, analyticsAllowed = true)
    // After a gather these mean the form was dismissed without a choice.
    UmpStatus.REQUIRED, UmpStatus.UNKNOWN -> ConsentState.Failed
}
