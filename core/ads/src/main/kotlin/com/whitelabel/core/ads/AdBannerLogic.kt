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

/** What happened to the banner on screen: a new ad view sent its request, or the SDK reported the outcome. */
internal enum class BannerEvent { REQUESTED, LOADED, FAILED }

/** Where a ready banner is: only [LOADED] takes space. */
internal enum class BannerLoad {
    LOADING, LOADED, FAILED;

    /** Whether the banner takes space and is exposed to accessibility (the "Ad" label included). */
    val takesSpace: Boolean get() = this == LOADED

    /**
     * The state after [event]. Once an ad shows, a failed refresh keeps it (the SDK leaves the last
     * ad in place), so the banner never vanishes mid-visit. A new request (a new ad view, after a
     * resize or for another unit) has nothing on screen yet, so it collapses until it loads.
     */
    fun on(event: BannerEvent): BannerLoad = when {
        event == BannerEvent.REQUESTED -> LOADING
        event == BannerEvent.LOADED -> LOADED
        this == LOADED -> LOADED
        else -> FAILED
    }
}
