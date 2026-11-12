package com.whitelabel.core.ads

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isEmpty
import assertk.assertions.isInstanceOf
import kotlin.test.Test

class AdsDslTest {

    private data object HomeTab

    @Test fun `placements keep their names tabs and declaration order`() {
        val placements = AdsBuilder().apply {
            placement("home", underTab = HomeTab)
            placement("detail_screen-2")
        }.build()

        assertThat(placements).containsExactly(AdPlacement("home", HomeTab), AdPlacement("detail_screen-2", null))
    }

    @Test fun `no placement is allowed`() {
        assertThat(AdsBuilder().build()).isEmpty()
    }

    @Test fun `a name the properties can't hold is rejected`() {
        // ads.android.banner.<name> and the units string (name=unit;…) need these characters only.
        listOf("", "1home", "home page", "home=x", "home;x", "hôme", "home.top").forEach { name ->
            assertFailure { AdsBuilder().placement(name) }
                .isInstanceOf<IllegalArgumentException>()
                .hasMessage("placement '$name' must start with a letter and use only letters, digits, '_' and '-'")
        }
    }

    @Test fun `a placement declared twice is rejected`() {
        assertFailure {
            AdsBuilder().apply {
                placement("home")
                placement("home", underTab = HomeTab)
            }
        }.isInstanceOf<IllegalArgumentException>().hasMessage("placement 'home' is declared twice")
    }
}
