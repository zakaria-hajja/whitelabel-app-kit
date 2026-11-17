package com.whitelabel.core.ads.google

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.whitelabel.core.ads.AdRequestPolicy
import com.whitelabel.core.ads.AdsServicesContributor
import com.whitelabel.core.ads.BannerHost
import com.whitelabel.core.ads.NativeAdHost
import com.whitelabel.core.network.ModuleContributor
import java.util.ServiceLoader
import org.junit.jupiter.api.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class AdsContributorTest {
    @Test fun `the contributor is discoverable by ServiceLoader`() {
        val found = ServiceLoader.load(ModuleContributor::class.java).map { it::class }
        assertThat(found).contains(AdsContributor::class)
    }

    @Test fun `the contributor carries the marker the shell finds it by`() {
        assertThat(AdsContributor()).isInstanceOf<AdsServicesContributor>()
    }

    @Test fun `the contributor binds the google banner host`() {
        val koin = koinApplication { modules(AdsContributor().modules()) }.koin
        assertThat(koin.get<BannerHost>()).isInstanceOf<GoogleBannerHost>()
    }

    @Test fun `the banner host builds requests with the bound policy`() {
        val koin = koinApplication {
            modules(module { single { AdRequestPolicy(nonPersonalized = true) } }, *AdsContributor().modules().toTypedArray())
        }.koin
        assertThat((koin.get<BannerHost>() as GoogleBannerHost).request).isEqualTo(AdRequestPolicy(nonPersonalized = true))
    }

    @Test fun `without a bound policy the banner host uses the default`() {
        val koin = koinApplication { modules(AdsContributor().modules()) }.koin
        assertThat((koin.get<BannerHost>() as GoogleBannerHost).request).isEqualTo(AdRequestPolicy.DEFAULT)
    }

    @Test fun `the contributor binds the google native host`() {
        val koin = koinApplication { modules(AdsContributor().modules()) }.koin
        assertThat(koin.get<NativeAdHost>()).isInstanceOf<GoogleNativeAdHost>()
    }

    @Test fun `native host builds requests with the graph's policy`() {
        val koin = koinApplication {
            modules(module { single { AdRequestPolicy(nonPersonalized = true) } }, *AdsContributor().modules().toTypedArray())
        }.koin
        assertThat((koin.get<NativeAdHost>() as GoogleNativeAdHost).request.nonPersonalized).isEqualTo(true)
    }

    @Test fun `without a bound policy the native host uses the default`() {
        val koin = koinApplication { modules(AdsContributor().modules()) }.koin
        assertThat((koin.get<NativeAdHost>() as GoogleNativeAdHost).request).isEqualTo(AdRequestPolicy.DEFAULT)
    }
}
