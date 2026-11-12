package com.whitelabel.core.ads.google

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.whitelabel.core.ads.BannerHost
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Google Mobile Ads banners: an adaptive anchored [AdView] for the available width. The SDK starts
 * on the first banner the rules let load (consent is resolved by then), never at app startup.
 */
internal object GoogleBannerHost : BannerHost {
    @Composable
    override fun Banner(unitId: String, onLoaded: () -> Unit, onFailed: () -> Unit) {
        val context = LocalContext.current
        val currentOnLoaded by rememberUpdatedState(onLoaded)
        val currentOnFailed by rememberUpdatedState(onFailed)
        var started by remember { mutableStateOf(MobileAdsStart.isDone) }
        LaunchedEffect(Unit) {
            val app = context.applicationContext
            if (MobileAdsStart.await { done -> MobileAds.initialize(app) { done() } }) started = true else currentOnFailed()
        }
        if (!started) return
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val widthDp = maxWidth.value.toInt()
            val size = remember(widthDp) { AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp) }
            // Google's current anchored adaptive size (it replaced the deprecated current-orientation one).
            // A new unit or width is a new ad view and a new request.
            key(unitId, size) {
                var view by remember { mutableStateOf<AdView?>(null) }
                AndroidView(
                    factory = { viewContext ->
                        AdView(viewContext).apply {
                            setAdSize(size)
                            adUnitId = unitId
                            adListener = object : AdListener() {
                                override fun onAdLoaded() = currentOnLoaded()
                                override fun onAdFailedToLoad(error: LoadAdError) = currentOnFailed()
                            }
                            loadAd(AdRequest.Builder().build())
                        }.also { view = it }
                    },
                    onRelease = { it.destroy() },
                    modifier = Modifier.fillMaxWidth().height(size.height.dp),
                )
                PauseWithLifecycle(view)
            }
        }
    }
}

/** Pauses [view] while the screen is in the background, as the SDK asks. */
@Composable
private fun PauseWithLifecycle(view: AdView?) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> view?.pause()
                Lifecycle.Event.ON_RESUME -> view?.resume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/** The Google Mobile Ads SDK's start, shared by every banner in the process. */
private val MobileAdsStart = StartOnce()

/**
 * Runs a start once and lets every caller wait for it, off the main thread. A start that throws
 * reports failure to its waiters and is forgotten, so a later caller tries again.
 */
internal class StartOnce {
    private val start = AtomicReference<CompletableDeferred<Unit>?>(null)

    val isDone: Boolean get() = start.get()?.let { it.isCompleted && !it.isCancelled && it.getCompletionExceptionOrNull() == null } == true

    /** Starts with [begin] (which calls its argument once started) unless already started; true once started. */
    suspend fun await(begin: (onDone: () -> Unit) -> Unit): Boolean {
        val mine = CompletableDeferred<Unit>()
        val current = if (start.compareAndSet(null, mine)) {
            // Not cancellable: a banner that leaves first must not leave the start half done for the others.
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    begin { mine.complete(Unit) }
                } catch (e: Exception) {
                    start.compareAndSet(mine, null)
                    mine.completeExceptionally(e)
                }
            }
            mine
        } else {
            start.get() ?: return false
        }
        return try {
            current.await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}
