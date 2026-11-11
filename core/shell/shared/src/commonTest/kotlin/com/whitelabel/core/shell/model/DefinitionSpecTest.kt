package com.whitelabel.core.shell.model

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import kotlin.test.Test

/** The rules every app definition must follow, on both platforms (Android's AppDefinitionBuilder.build()). */
class DefinitionSpecTest {

    private fun spec(vararg features: FeatureSpec, start: String? = null) = DefinitionSpec(features.toList(), start)

    @Test fun `a definition with no tab is rejected`() {
        assertFailure { spec(feature("a")).validate() }.messageContains("at least one tab")
    }

    @Test fun `a duplicate id is rejected naming it`() {
        assertFailure { spec(feature("a", tab = "a"), feature("a", tab = "b")).validate() }.messageContains("Feature added more than once: a")
    }

    @Test fun `the start tab must be one of the tabs`() {
        assertFailure { spec(feature("a", tab = "a"), start = "nope").validate() }.messageContains("Start tab nope is not one of the tabs")
    }

    @Test fun `sign in features without a gate fail naming auth`() {
        assertFailure { spec(feature("home", tab = "home", requiresSignIn = true)).validate() }.messageContains("home need sign-in: add auth()")
    }

    @Test fun `sign in features are accepted with a gate`() {
        val valid = spec(feature("auth", isGate = true), feature("home", tab = "home", requiresSignIn = true))
        assertThat(valid.validate().hasGate).isTrue()
    }

    @Test fun `two gates fail`() {
        assertFailure { spec(feature("g1", isGate = true), feature("g2", isGate = true), feature("a", tab = "a")).validate() }
            .messageContains("Only one feature may gate the app")
    }

    @Test fun `a definition without a gate has none`() {
        assertThat(spec(feature("a", tab = "a")).hasGate).isFalse()
    }

    @Test fun `backend features need a backend`() {
        val needing = spec(feature("auth", isGate = true), feature("a", tab = "a"))
        assertThat(needing.backendProblem(hasBackend = false)).isEqualTo(
            "auth call the backend: set api.debug.baseUrl and api.release.baseUrl in app.properties",
        )
        assertThat(needing.backendProblem(hasBackend = true)).isNull()
        assertThat(spec(feature("settings", tab = "settings")).backendProblem(hasBackend = false)).isNull()
    }
}
