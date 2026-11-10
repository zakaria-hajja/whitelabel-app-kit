package com.whitelabel.core.shell

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class TabResolutionTest {

    private val definition = appDefinition {
        add(entry("a", RouteA))
        add(entry("catalog", RouteB, flagKey = "catalog"))
        add(entry("profile", RouteC, flagKey = "profile"))
    }

    private fun routes(tabs: List<Tab>) = tabs.map { it.route }

    @Test fun `unflagged entries are always visible`() {
        assertThat(routes(definition.visibleTabs(emptyMap(), emptySet()))).containsExactly(RouteA)
    }

    @Test fun `remote flag wins over defaults`() {
        assertThat(routes(definition.visibleTabs(mapOf("catalog" to false), setOf("catalog")))).containsExactly(RouteA)
        assertThat(routes(definition.visibleTabs(mapOf("catalog" to true), emptySet()))).containsExactly(RouteA, RouteB)
    }

    @Test fun `missing remote key falls back to defaults then off`() {
        assertThat(routes(definition.visibleTabs(emptyMap(), setOf("profile")))).containsExactly(RouteA, RouteC)
    }

    @Test fun `start tab falls back to first visible when its flag is off`() {
        val withStart = appDefinition {
            add(entry("a", RouteA))
            add(entry("catalog", RouteB, flagKey = "catalog"))
            startTab(RouteB)
        }
        val visible = withStart.visibleTabs(mapOf("catalog" to false), emptySet())

        assertThat(withStart.resolveStartTab(visible)?.route).isEqualTo(RouteA)
    }

    @Test fun `visible start tab is used`() {
        val withStart = appDefinition {
            add(entry("a", RouteA))
            add(entry("b", RouteB))
            startTab(RouteB)
        }

        assertThat(withStart.resolveStartTab(withStart.tabs)?.route).isEqualTo(RouteB)
    }

    @Test fun `no visible tab resolves to null`() {
        val flaggedOnly = appDefinition { add(entry("catalog", RouteB, flagKey = "catalog")) }
        val visible = flaggedOnly.visibleTabs(emptyMap(), emptySet())

        assertThat(visible).isEqualTo(emptyList())
        assertThat(flaggedOnly.resolveStartTab(visible)).isNull()
    }
}
