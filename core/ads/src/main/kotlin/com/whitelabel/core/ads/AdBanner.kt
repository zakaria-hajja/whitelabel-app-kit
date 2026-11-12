package com.whitelabel.core.ads

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.getKoin

/**
 * The banner for [placement], labelled "Ad". It takes no space while the rules hide it, while it
 * loads and when it fails (never an error), and always without the ads SDK (no [BannerHost]) or
 * without `ads { }` (no [AdsController]). A [placement] missing from `ads { }` throws in a debug build.
 */
@Composable
fun AdBanner(placement: String, modifier: Modifier = Modifier) {
    val koin = getKoin()
    val controller = remember(koin) { koin.getOrNull<AdsController>() } ?: return
    val host = remember(koin) { koin.getOrNull<BannerHost>() } ?: return
    val state by remember(controller, placement) { controller.bannerState(placement) }.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val debugBuild = remember(context) { context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    val unitId = bannerUnit(state, placement, debugBuild) ?: return
    var load by remember(unitId) { mutableStateOf(BannerLoad.LOADING) }
    // The banner stays composed while it loads (so the SDK can load it) and after a failure, but takes
    // no space until it has an ad.
    Column(modifier.fillMaxWidth().collapsedUnless(load == BannerLoad.LOADED), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.ads_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        host.Banner(
            unitId = unitId,
            onRequested = { load = load.on(BannerEvent.REQUESTED) },
            onLoaded = { load = load.on(BannerEvent.LOADED) },
            onFailed = { load = load.on(BannerEvent.FAILED) },
        )
    }
}

/** Measures the content as usual but, unless [shown], reports (and draws) zero height. */
private fun Modifier.collapsedUnless(shown: Boolean): Modifier = clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    layout(placeable.width, if (shown) placeable.height else 0) { placeable.place(0, 0) }
}
