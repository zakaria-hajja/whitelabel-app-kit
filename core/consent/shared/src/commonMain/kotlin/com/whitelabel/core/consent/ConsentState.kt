package com.whitelabel.core.consent

/** What the user's consent choices allow. Starts [Unknown] until the first gather ends. */
sealed interface ConsentState {
    data object Unknown : ConsentState
    data class Resolved(val canRequestAds: Boolean, val analyticsAllowed: Boolean) : ConsentState
    data object Failed : ConsentState
}
