package com.whitelabel.core.ads.google

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.whitelabel.core.ads.AdRequestPolicy
import com.whitelabel.core.ads.NativeAdHandle
import com.whitelabel.core.ads.NativeAdHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Google Mobile Ads native ads ("Native advanced") for the list-item template. The SDK starts on the
 * first ad the rules let load, as for banners. Every request follows [request] (`ads.nonPersonalized`).
 * AdChoices sits in the top-right corner, and video starts muted with the SDK's own controls.
 */
internal class GoogleNativeAdHost(val request: AdRequestPolicy) : NativeAdHost {
    // Process-wide like the rotation: a load outlives the slot that asked for it (NativeAdRotation keeps it).
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    override fun load(context: Context, unitId: String, onLoaded: (NativeAdHandle) -> Unit, onFailed: () -> Unit) {
        val app = context.applicationContext
        scope.launch {
            if (!MobileAdsStart.await { done -> MobileAds.initialize(app) { done() } }) {
                onFailed()
                return@launch
            }
            AdLoader.Builder(app, unitId)
                .forNativeAd { ad ->
                    // The template needs a headline and media; anything less is a silent failed load.
                    if (isRenderable(ad.headline, ad.mediaContent != null)) {
                        onLoaded(GoogleNativeAd(ad))
                    } else {
                        ad.destroy()
                        onFailed()
                    }
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) = onFailed()
                })
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
                        .build(),
                )
                .build()
                .loadAd(adRequest(request))
        }
    }

    @Composable
    override fun Item(ad: NativeAdHandle, modifier: Modifier) {
        val google = ad as? GoogleNativeAd ?: return
        NativeAdTemplate(google.ad, modifier)
    }
}

/** A loaded Google native ad. [destroy] releases the SDK's ad (the rotation calls it once the ad is done). */
internal class GoogleNativeAd(val ad: NativeAd) : NativeAdHandle {
    override fun destroy() = ad.destroy()
}

/** Whether the template can draw an ad: it needs a headline and media (the SDK's required assets). */
internal fun isRenderable(headline: String?, hasMedia: Boolean): Boolean = !headline.isNullOrEmpty() && hasMedia
