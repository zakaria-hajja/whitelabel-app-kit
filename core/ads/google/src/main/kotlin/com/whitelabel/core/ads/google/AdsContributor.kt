package com.whitelabel.core.ads.google

import com.whitelabel.core.ads.AdRequestPolicy
import com.whitelabel.core.ads.AdsServicesContributor
import com.whitelabel.core.ads.BannerHost
import com.whitelabel.core.ads.NativeAdHost
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Binds the Google Mobile Ads [BannerHost] and [NativeAdHost] when this module is on the classpath (found by
 * `ServiceLoader`). The shell recognizes it by [AdsServicesContributor], which survives R8 renaming.
 * Both hosts build requests with the graph's [AdRequestPolicy] (the ads module binds it).
 */
class AdsContributor : AdsServicesContributor {
    override fun modules(): List<Module> = listOf(
        module {
            single<BannerHost> { GoogleBannerHost(getOrNull<AdRequestPolicy>() ?: AdRequestPolicy.DEFAULT) }
            single<NativeAdHost> { GoogleNativeAdHost(getOrNull<AdRequestPolicy>() ?: AdRequestPolicy.DEFAULT) }
        },
    )
}
