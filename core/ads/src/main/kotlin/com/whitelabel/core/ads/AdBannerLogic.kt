package com.whitelabel.core.ads

/**
 * The unit [AdBanner] loads for [state], or null to take no space. A placement missing from
 * `ads { }` fails loudly in a debug build and just stays hidden in a release one.
 */
internal fun bannerUnit(state: BannerState, placement: String, debugBuild: Boolean): String? = when (state) {
    is BannerState.Ready -> state.unitId
    is BannerState.Hidden -> {
        check(!(debugBuild && state.reason == HideReason.UNDECLARED)) { "placement '$placement' isn't declared in ads { }" }
        null
    }
}

/** What the SDK reported for the banner on screen. */
internal enum class BannerEvent { LOADED, FAILED }

/** Where a ready banner is: only [LOADED] takes space. */
internal enum class BannerLoad {
    LOADING, LOADED, FAILED;

    /**
     * The state after [event]. Once an ad shows, a failed refresh keeps it (the SDK leaves the last
     * ad in place), so the banner never vanishes mid-visit.
     */
    fun on(event: BannerEvent): BannerLoad = when {
        event == BannerEvent.LOADED -> LOADED
        this == LOADED -> LOADED
        else -> FAILED
    }
}
