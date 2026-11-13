package com.whitelabel.core.ads

import com.whitelabel.core.consent.ConsentState
import kotlin.time.Instant

sealed interface AdDecision {
    data object Show : AdDecision
    data class Hide(val reason: HideReason) : AdDecision
}

enum class HideReason { ADS_OFF, CONSENT_PENDING, CONSENT_FAILED, CONSENT_NO_ADS, FIRST_LAUNCH, AD_FREE, UNDECLARED,

    /** The app hid the placement ([AdsController.setHidden]), e.g. while its screen is empty. */
    APP_HIDDEN,

    /** Not decided yet: the stored ad-free window is unread, or the tracking prompt is still open. */
    LOADING,
}

/**
 * Whether an ad may show. Rules are checked in a fixed order so the reason is deterministic:
 * ads off, undeclared placement, hidden by the app, consent, first launch, ad-free window. The app's
 * hide only adds a reason to hide: it can never show what the other rules hide.
 */
fun adGate(
    adsEnabled: Boolean,
    placementDeclared: Boolean,
    hiddenByApp: Boolean,
    consent: ConsentState,
    launchNumber: Int,
    adFreeUntil: Instant?,
    now: Instant,
): AdDecision {
    val reason = when {
        !adsEnabled -> HideReason.ADS_OFF
        !placementDeclared -> HideReason.UNDECLARED
        hiddenByApp -> HideReason.APP_HIDDEN
        consent is ConsentState.Unknown -> HideReason.CONSENT_PENDING
        consent is ConsentState.Failed -> HideReason.CONSENT_FAILED
        consent is ConsentState.Resolved && !consent.canRequestAds -> HideReason.CONSENT_NO_ADS
        launchNumber <= 1 -> HideReason.FIRST_LAUNCH
        adFreeUntil != null && adFreeUntil > now -> HideReason.AD_FREE
        else -> null
    }
    return if (reason == null) AdDecision.Show else AdDecision.Hide(reason)
}
