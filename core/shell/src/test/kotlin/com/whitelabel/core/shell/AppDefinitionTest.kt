package com.whitelabel.core.shell

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import com.whitelabel.core.designsystem.theme.StylePack
import org.junit.jupiter.api.Test

class AppDefinitionTest {

    @Test fun `definition keeps declaration order of tabs`() {
        val definition = appDefinition {
            add(entry("b", RouteB))
            add(entry("settings-like", route = null))
            add(entry("a", RouteA))
        }

        assertThat(definition.tabs.map { it.route }).containsExactly(RouteB, RouteA)
        assertThat(definition.entries.map { it.id }).containsExactly("b", "settings-like", "a")
    }

    @Test fun `no tab is rejected`() {
        assertFailure { appDefinition { add(entry("a", route = null)) } }
            .isInstanceOf(IllegalArgumentException::class)
            .messageContains("at least one tab")
    }

    @Test fun `duplicate entry id is rejected`() {
        assertFailure {
            appDefinition {
                add(entry("notes", RouteA))
                add(entry("notes", RouteB))
            }
        }.isInstanceOf(IllegalArgumentException::class).messageContains("notes")
    }

    @Test fun `start tab must be one of the tabs`() {
        assertFailure {
            appDefinition {
                add(entry("a", RouteA))
                startTab(RouteC)
            }
        }.isInstanceOf(IllegalArgumentException::class).messageContains("RouteC")
    }

    @Test fun `start tab defaults to null meaning first`() {
        val definition = appDefinition { add(entry("a", RouteA)) }

        assertThat(definition.startTabRoute).isNull()
    }

    @Test fun `explicit start tab is kept`() {
        val definition = appDefinition {
            add(entry("a", RouteA))
            add(entry("b", RouteB))
            startTab(RouteB)
        }

        assertThat(definition.startTabRoute).isEqualTo(RouteB)
    }

    @Test fun `feature needing sign in without auth fails naming auth()`() {
        assertFailure {
            appDefinition {
                add(entry("profile", RouteA, requiresSignIn = true))
                add(entry("home", RouteB, requiresSignIn = true))
            }
        }.isInstanceOf(IllegalArgumentException::class).messageContains("profile, home need sign-in: add auth()")
    }

    @Test fun `feature needing sign in is accepted with a gate`() {
        val definition = appDefinition {
            add(entry("auth", gate = Gate(RouteC)))
            add(entry("profile", RouteA, requiresSignIn = true))
        }
        assertThat(definition.gateEntry?.id).isEqualTo("auth")
    }

    @Test fun `two gates fail`() {
        assertFailure {
            appDefinition {
                add(entry("auth", gate = Gate(RouteC)))
                add(entry("sso", gate = Gate(RouteB)))
                add(entry("a", RouteA))
            }
        }.isInstanceOf(IllegalArgumentException::class).messageContains("auth, sso")
    }

    @Test fun `backendless app without backend features is fine`() {
        val definition = appDefinition { add(entry("a", RouteA)) }
        assertThat(definition.backendProblem(hasBackend = false)).isNull()
    }

    @Test fun `backend features need a backend`() {
        val definition = appDefinition {
            add(entry("auth", gate = Gate(RouteC)))
            add(entry("a", RouteA, requiresSignIn = true))
            add(entry("b", RouteB, requiresBackend = true))
        }
        assertThat(definition.backendProblem(hasBackend = false)).isEqualTo(
            "auth, a, b call the backend: set api.debug.baseUrl and api.release.baseUrl in app.properties",
        )
        assertThat(definition.backendProblem(hasBackend = true)).isNull()
    }

    @Test fun `style defaults to material`() {
        assertThat(appDefinition { add(entry("a", RouteA)) }.stylePack).isEqualTo(StylePack.Material)
    }

    @Test fun `last style call wins`() {
        val definition = appDefinition {
            add(entry("a", RouteA))
            style(StylePack.Gallery)
            style(StylePack.Material)
        }
        assertThat(definition.stylePack).isEqualTo(StylePack.Material)
    }

    @Test fun `tabs with the same simple route name get different default ids`() {
        val definition = appDefinition {
            add(entry("first", FirstFeature.Root))
            add(entry("second", SecondFeature.Root, flagKey = "second"))
        }
        assertThat(definition.tabs.map { it.routeId }.toSet().size).isEqualTo(2)
        assertThat(definition.visibleTabs(mapOf("second" to false), emptySet()).map { it.route }).containsExactly(FirstFeature.Root)
    }

    @Test fun `contentGate sets the lock screen`() {
        val definition = appDefinition {
            add(entry("a", RouteA))
            contentGate { androidx.compose.material3.Text("Lock") }
        }
        assertThat(definition.lockScreen != null).isEqualTo(true)
    }

    @Test fun `without contentGate there is no lock screen`() {
        assertThat(appDefinition { add(entry("a", RouteA)) }.lockScreen).isNull()
    }
}
