package com.whitelabel.core.shell

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class AppDefinitionModulesTest {

    private val appOwn = module {}
    private val first = module {}
    private val second = module {}

    @Test fun `the app's own modules follow the entries' modules, in definition order`() {
        val definition = appDefinition {
            modules(appOwn)
            add(entry("a", RouteA, modules = listOf(first)))
            add(entry("b", RouteB, modules = listOf(second)))
        }
        assertThat(definition.koinModules).containsExactly(first, second, appOwn)
    }

    @Test fun `an app binding replaces a feature's binding of the same type`() {
        val definition = appDefinition {
            modules(module { single { "the app's" } })
            add(entry("a", RouteA, modules = listOf(module { single { "the feature's" } })))
        }
        val koin = koinApplication { modules(definition.koinModules) }.koin
        assertThat(koin.get<String>()).isEqualTo("the app's")
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
