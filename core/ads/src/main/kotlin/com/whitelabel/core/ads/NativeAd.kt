package com.whitelabel.core.ads

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import org.koin.compose.getKoin

/**
 * Loads and draws native ads for [rememberNativeAd] / [NativeAdItem]. `:core:ads:google` binds it;
 * without it (no SDK linked) a native placement never loads. [load] uses the application context;
 * exactly one of its callbacks arrives, on the main thread. [Item] draws the fixed list-item template.
 */
interface NativeAdHost {
    fun load(context: Context, unitId: String, onLoaded: (NativeAdHandle) -> Unit, onFailed: () -> Unit)

    @Composable
    fun Item(ad: NativeAdHandle, modifier: Modifier)
}

/**
 * A native placement on one screen, from [rememberNativeAd]. [isLoaded] says whether [NativeAdItem]
 * draws anything: show the item in the list only while it is true, so nothing takes space before an
 * ad has loaded (a failed load stays silent).
 */
@Stable
class NativeAdSlot internal constructor(internal val driver: NativeAdSlotDriver?, internal val host: NativeAdHost?) {
    internal val ad: NativeAdHandle? get() = driver?.shown
    val isLoaded: Boolean get() = ad != null
}

/**
 * The native ad slot for [placement] (declared with `ads { native("…") }`). Hoist it at screen level,
 * outside the `LazyColumn`, so loading starts when the screen opens, and show [NativeAdItem] as a
 * list item while [NativeAdSlot.isLoaded]. The rules are a banner's ([AdsController.bannerState]);
 * when to request, show and refresh is [NativeAdRotation]'s. Without an [AdsController] (no `ads { }`)
 * or a [NativeAdHost] (no SDK) it never loads. A [placement] missing from `ads { }` throws in a debug build.
 */
@Composable
fun rememberNativeAd(placement: String): NativeAdSlot {
    val koin = getKoin()
    val controller = remember(koin) { koin.getOrNull<AdsController>() }
    val host = remember(koin) { koin.getOrNull<NativeAdHost>() }
    val context = LocalContext.current
    val slot = remember(controller, host, placement) {
        val driver = if (controller != null && host != null) {
            val appContext = context.applicationContext
            NativeAdSlotDriver(NativeAdRotations.of(placement)) { unitId, onLoaded, onFailed ->
                host.load(appContext, unitId, onLoaded, onFailed)
            }
        } else {
            null
        }
        NativeAdSlot(driver, host)
    }
    val driver = slot.driver
    if (controller != null && driver != null) {
        val state by remember(controller, placement) { controller.bannerState(placement) }.collectAsStateWithLifecycle()
        val debugBuild = remember(context) { context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
        val unitId = bannerUnit(state, placement, debugBuild)
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val started = lifecycle.isAtLeast(Lifecycle.State.STARTED)
        LaunchedEffect(driver, unitId, started) {
            when {
                unitId == null -> driver.hidden()
                !started -> driver.paused()
                else -> driver.ready(unitId)
            }
        }
        DisposableEffect(driver) { onDispose { driver.dispose() } }
    }
    return slot
}

/** The native ad of [slot] in the fixed template, or nothing (no space) until one has loaded. */
@Composable
fun NativeAdItem(slot: NativeAdSlot, modifier: Modifier = Modifier) {
    val ad = slot.ad ?: return
    val host = slot.host ?: return
    host.Item(ad, modifier)
}

/**
 * The logic of one [rememberNativeAd] slot, apart from Compose so it is testable on the JVM. It
 * drives the placement's [rotation] and asks [load] for an ad when a visit says so. [shown] is the
 * ad the slot draws (snapshot state).
 * - [ready]: the rules are `Ready` and the screen is visible: begins a visit, if none, and requests
 *   when the visit says so.
 * - [hidden]: the rules hide the placement: nothing shown, the visit ends, held ads stay.
 * - [paused]: the screen stopped (app in the background): the visit ends but the stopped screen keeps
 *   its ad, so coming back shows the same ad without a gap, or drops it when it is past the age limit.
 * - [dispose]: the slot left the composition: the visit ends; a later load goes to the rotation only.
 */
internal class NativeAdSlotDriver(
    private val rotation: NativeAdRotation,
    private val load: (unitId: String, onLoaded: (NativeAdHandle) -> Unit, onFailed: () -> Unit) -> Unit,
) {
    var shown: NativeAdHandle? by mutableStateOf(null)
        private set
    private var visiting = false
    private var disposed = false

    fun ready(unitId: String) {
        if (disposed || visiting) return
        visiting = true
        val visit = rotation.beginVisit()
        shown = visit.ad
        if (visit.request) load(unitId, ::onLoaded, rotation::failed)
    }

    fun hidden() {
        endVisit()
        shown = null
    }

    fun paused() = endVisit()

    fun dispose() {
        endVisit()
        disposed = true
        shown = null
    }

    private fun endVisit() {
        if (!visiting) return
        visiting = false
        rotation.endVisit()
    }

    private fun onLoaded(ad: NativeAdHandle) {
        val show = rotation.loaded(ad)
        if (show != null && visiting && !disposed) shown = show
    }
}
