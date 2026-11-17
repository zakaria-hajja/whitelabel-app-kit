package com.whitelabel.core.ads

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
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

    @Test fun `native declares a native placement without a tab`() {
        assertThat(AdsBuilder().apply { native("list") }.build()).containsExactly(AdPlacement("list", null, AdKind.NATIVE))
    }

    @Test fun `placement stays a banner`() {
        assertThat(AdsBuilder().apply { placement("home") }.build().single().kind).isEqualTo(AdKind.BANNER)
    }

    @Test fun `a name is unique across kinds`() {
        assertFailure {
            AdsBuilder().apply {
                placement("x")
                native("x")
            }
        }.isInstanceOf<IllegalArgumentException>().hasMessage("placement 'x' is declared twice")
        assertFailure {
            AdsBuilder().apply {
                native("y")
                placement("y")
            }
        }.isInstanceOf<IllegalArgumentException>().hasMessage("placement 'y' is declared twice")
    }

    @Test fun `native applies the name rule`() {
        listOf("1x", "list.top", "").forEach { name ->
            assertFailure { AdsBuilder().native(name) }
                .isInstanceOf<IllegalArgumentException>()
                .hasMessage("placement '$name' must start with a letter and use only letters, digits, '_' and '-'")
        }
    }
}
