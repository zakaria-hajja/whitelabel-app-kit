package com.whitelabel.core.ads.google

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isInstanceOf
import com.whitelabel.core.ads.AdsServicesContributor
import com.whitelabel.core.ads.BannerHost
import com.whitelabel.core.network.ModuleContributor
import java.util.ServiceLoader
import org.junit.jupiter.api.Test
import org.koin.dsl.koinApplication

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
}
