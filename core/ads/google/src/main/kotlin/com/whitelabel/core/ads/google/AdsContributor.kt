package com.whitelabel.core.ads.google

import com.whitelabel.core.ads.AdsServicesContributor
import com.whitelabel.core.ads.BannerHost
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Binds the Google Mobile Ads [BannerHost] when this module is on the classpath (found by
 * `ServiceLoader`). The shell recognizes it by [AdsServicesContributor], which survives R8 renaming.
 */
class AdsContributor : AdsServicesContributor {
    override fun modules(): List<Module> = listOf(module { single<BannerHost> { GoogleBannerHost } })
}
