package com.whitelabel.core.ads

import androidx.compose.runtime.Composable
import com.whitelabel.core.network.ModuleContributor

/**
 * Draws one banner for [AdBanner]. `:core:ads:google` binds it; without it (no SDK linked) the
 * banner takes no space. [Banner] loads an adaptive anchored banner for the available width and
 * reports the outcome: [onLoaded] once an ad is showing, [onFailed] when none could load.
 */
interface BannerHost {
    @Composable
    fun Banner(unitId: String, onLoaded: () -> Unit, onFailed: () -> Unit)
}

/**
 * Marks the contributor that binds the real ads pieces (`:core:ads:google`'s). The shell finds it by
 * type, because R8 renames classes in release builds, so a class-name check would miss it.
 */
interface AdsServicesContributor : ModuleContributor
