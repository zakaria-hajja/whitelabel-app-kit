package com.whitelabel.core.ads.google

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.google.ads.mediation.admob.AdMobAdapter
import com.whitelabel.core.ads.AdRequestPolicy
import org.junit.Test
import org.junit.runner.RunWith

/** The request the banner sends, built by the real SDK (its Bundle needs a device). */
@RunWith(AndroidJUnit4::class)
class AdRequestDeviceTest {

    @Test fun nonPersonalizedRequestCarriesNpa1ForAdMob() {
        val extras = adRequest(AdRequestPolicy(nonPersonalized = true)).getNetworkExtrasBundle(AdMobAdapter::class.java)
        assertThat(extras?.getString("npa")).isEqualTo("1")
    }

    @Test fun defaultRequestCarriesNoAdMobExtras() {
        val extras = adRequest(AdRequestPolicy.DEFAULT).getNetworkExtrasBundle(AdMobAdapter::class.java)
        assertThat(extras == null || extras.isEmpty).isTrue()
    }
}
