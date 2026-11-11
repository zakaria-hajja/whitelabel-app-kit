package com.whitelabel.core.shell

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class ShellTransitionsTest {

    private val tabs = setOf("com.x.A", "com.x.B")

    @Test fun `tab to tab is a tab switch`() {
        assertThat(transitionKind("com.x.A", "com.x.B", tabs)).isEqualTo(TransitionKind.TabSwitch)
    }

    @Test fun `tab to pushed screen and back are screen transitions`() {
        assertThat(transitionKind("com.x.A", "com.x.Detail", tabs)).isEqualTo(TransitionKind.Screen)
        assertThat(transitionKind("com.x.Detail", "com.x.A", tabs)).isEqualTo(TransitionKind.Screen)
    }

    @Test fun `null routes are screen transitions`() {
        assertThat(transitionKind(null, "com.x.A", tabs)).isEqualTo(TransitionKind.Screen)
        assertThat(transitionKind("com.x.A", null, tabs)).isEqualTo(TransitionKind.Screen)
    }

    @Test fun `routes with arguments still match their tab`() {
        assertThat(transitionKind("com.x.A?filter=all", "com.x.B", tabs)).isEqualTo(TransitionKind.TabSwitch)
    }

    @Test fun `a route that only shares a prefix with a tab is not that tab`() {
        assertThat(transitionKind("com.x.AB", "com.x.B", tabs)).isEqualTo(TransitionKind.Screen)
    }
}
