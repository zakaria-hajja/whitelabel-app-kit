package com.whitelabel.core.shell

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.jupiter.api.Test
import org.koin.dsl.module

class AppDefinitionModulesTest {

    private val appOwn = module {}
    private val first = module {}
    private val second = module {}

    @Test fun `entry modules follow the app's own modules, in definition order`() {
        val definition = appDefinition {
            modules(appOwn)
            add(entry("a", RouteA, modules = listOf(first)))
            add(entry("b", RouteB, modules = listOf(second)))
        }
        assertThat(definition.koinModules).containsExactly(appOwn, first, second)
    }

    @Test fun `the same module from two entries appears once`() {
        val definition = appDefinition {
            add(entry("a", RouteA, modules = listOf(first)))
            add(entry("b", RouteB, modules = listOf(first, second)))
        }
        assertThat(definition.koinModules).containsExactly(first, second)
    }

    @Test fun `a definition without modules has none`() {
        assertThat(appDefinition { add(entry("a", RouteA)) }.koinModules).isEmpty()
    }
}
