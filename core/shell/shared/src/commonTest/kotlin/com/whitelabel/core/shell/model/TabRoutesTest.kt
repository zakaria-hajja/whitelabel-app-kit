package com.whitelabel.core.shell.model

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class TabRoutesTest {

    private val spec = DefinitionSpec(
        listOf(feature("a", tab = "a"), feature("catalog", tab = "catalog", flagKey = "catalog"), feature("profile", tab = "profile", flagKey = "profile")),
        startTabRoute = null,
    )

    @Test fun `unflagged features are always visible`() {
        assertThat(spec.visibleTabRoutes(emptyMap(), emptySet())).containsExactly("a")
    }

    @Test fun `a remote flag wins over the defaults`() {
        assertThat(spec.visibleTabRoutes(mapOf("catalog" to false), setOf("catalog"))).containsExactly("a")
        assertThat(spec.visibleTabRoutes(mapOf("catalog" to true), emptySet())).containsExactly("a", "catalog")
    }

    @Test fun `a missing remote key falls back to the defaults then off`() {
        assertThat(spec.visibleTabRoutes(emptyMap(), setOf("profile"))).containsExactly("a", "profile")
    }

    @Test fun `the start tab falls back to the first visible one`() {
        val withStart = spec.copy(startTabRoute = "catalog")
        assertThat(withStart.resolveStartTab(withStart.visibleTabRoutes(mapOf("catalog" to false), emptySet()))).isEqualTo("a")
        assertThat(withStart.resolveStartTab(listOf("a", "catalog"))).isEqualTo("catalog")
    }

    @Test fun `no visible tab resolves to null`() {
        val flaggedOnly = DefinitionSpec(listOf(feature("catalog", tab = "catalog", flagKey = "catalog")), null)
        val visible = flaggedOnly.visibleTabRoutes(emptyMap(), emptySet())
        assertThat(visible).isEmpty()
        assertThat(flaggedOnly.resolveStartTab(visible)).isNull()
    }

    // A feature its flag switches off is off entirely: no tab, and no link opens it.
    @Test fun `enabled features follow the same flags including tabless ones`() {
        val withDetail = spec.copy(features = spec.features + feature("offers", flagKey = "offers") + feature("auth", isGate = true))
        assertThat(withDetail.enabledFeatureIds(mapOf("catalog" to false), setOf("profile", "offers")))
            .containsExactly("a", "profile", "offers", "auth")
    }
}
