package com.whitelabel.core.ads

import com.whitelabel.core.consent.Consent
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

sealed interface BannerState {
    data class Hidden(val reason: HideReason) : BannerState
    data class Ready(val unitId: String) : BannerState
}

/**
 * Decides, per placement, whether a banner may load. [units] maps a placement to its unit ID (already
 * resolved for the build type); a placement not in it is undeclared. Counts this launch on creation.
 */
class AdsController(
    private val enabled: Boolean,
    private val units: Map<String, String>,
    private val consent: Consent,
    private val launches: LaunchCounter,
    private val adFree: AdFree,
    private val tracking: TrackingPort,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    // Lazies are swapped in by compare-and-set; only the winner is ever forced, so one flow exists per placement.
    private val banners = MutableStateFlow<Map<String, Lazy<StateFlow<BannerState>>>>(emptyMap())
    private val trackingStarted = MutableStateFlow(false)
    private val trackingDone = MutableStateFlow(false)
    // Placements the app hid; one more input to every banner's decision. Not stored: the app re-derives it.
    private val hidden = MutableStateFlow<Set<String>>(emptySet())

    private val undeclared = MutableStateFlow<BannerState>(BannerState.Hidden(HideReason.UNDECLARED)).asStateFlow()
    private val adsOff = MutableStateFlow<BannerState>(BannerState.Hidden(HideReason.ADS_OFF)).asStateFlow()

    init {
        // A failing store leaves the launch uncounted, which keeps banners hidden (launch 0 is FIRST_LAUNCH).
        scope.launch { runCatching { launches.countThisLaunch() } }
    }

    /** One shared state per placement: every caller of the same placement sees the same flow. */
    fun bannerState(placement: String): StateFlow<BannerState> =
        if (placement !in units) {
            // One constant per reason, so arbitrary names never cache or launch anything.
            if (enabled) undeclared else adsOff
        } else run {
            banners.value[placement]?.let { return@run it }
            val mine = lazy { createBanner(placement) }
            banners.update { if (placement in it) it else it + (placement to mine) }
            banners.value.getValue(placement)
        }.value

    /**
     * Hides [placement] while [hidden] is true (only hides: the house rules still decide when it may show).
     * Idempotent; a no-op for an undeclared placement or with ads off. Lasts for this process.
     */
    fun setHidden(placement: String, hidden: Boolean) {
        this.hidden.update { if (hidden) it + placement else it - placement }
    }

    private fun decision(placement: String): AdDecision {
        val now = clock.now()
        val gated = adGate(
            adsEnabled = enabled,
            placementDeclared = placement in units,
            hiddenByApp = placement in hidden.value,
            consent = consent.state.value,
            launchNumber = launches.launchNumber.value,
            adFreeUntil = (adFree.state.value as? AdFreeState.Active)?.until,
            now = now,
        )
        return withLoaded(gated, adFree.loaded.value)
    }

    // Everything up to the ad-free check passed (the result is Show or AD_FREE): an unread window is not "none".
    private fun withLoaded(gated: AdDecision, loaded: Boolean): AdDecision =
        if (!loaded && (gated is AdDecision.Show || gated == AdDecision.Hide(HideReason.AD_FREE))) {
            AdDecision.Hide(HideReason.LOADING)
        } else {
            gated
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createBanner(placement: String): StateFlow<BannerState> {
        val unitId = units[placement]
        val initial = decision(placement).let {
            // Show with the tracking prompt still to come is not ready yet.
            if (it is AdDecision.Hide) BannerState.Hidden(it.reason) else BannerState.Hidden(HideReason.LOADING)
        }
        return combine(
            consent.state, launches.launchNumber, adFree.state, adFree.loaded, hidden,
        ) { _, _, _, _, _ -> decision(placement) }
            .transformLatest { decision ->
                when (decision) {
                    is AdDecision.Hide -> emit(BannerState.Hidden(decision.reason))
                    AdDecision.Show -> {
                        awaitTracking()
                        emit(BannerState.Ready(checkNotNull(unitId)))
                    }
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, initial)
    }

    /** Requests tracking permission once per launch, whichever placement gets here first, and waits for it. */
    private suspend fun awaitTracking() {
        if (trackingStarted.compareAndSet(expect = false, update = true)) {
            tracking.requestIfNeeded { trackingDone.value = true }
        }
        trackingDone.first { it }
    }
}
