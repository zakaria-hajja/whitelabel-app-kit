package com.whitelabel.core.shell

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.designsystem.theme.CubicBezier
import com.whitelabel.core.designsystem.theme.MotionCurve.Tween
import com.whitelabel.core.designsystem.theme.MotionSpec
import org.junit.jupiter.api.Test

/** Entries are (id, route); a TabTransitions instance lives for the whole NavHost, like the real one. */
class ShellTransitionsTest {

    private val tabs = TabTransitions(setOf("com.x.A", "com.x.B"))

    @Test fun `tab to tab is a tab switch`() {
        assertThat(tabs.kind("a", "com.x.A", "b", "com.x.B")).isEqualTo(TransitionKind.TabSwitch)
    }

    @Test fun `push within a tab and back are screen transitions`() {
        assertThat(tabs.kind("a", "com.x.A", "d", "com.x.Detail")).isEqualTo(TransitionKind.Screen)
        assertThat(tabs.kind("d", "com.x.Detail", "a", "com.x.A")).isEqualTo(TransitionKind.Screen)
    }

    @Test fun `tapping another tab from a pushed screen is a tab switch`() {
        tabs.kind("a", "com.x.A", "d", "com.x.Detail") // Detail pushed inside tab A
        assertThat(tabs.kind("d", "com.x.Detail", "b", "com.x.B")).isEqualTo(TransitionKind.TabSwitch)
    }

    @Test fun `switching to a tab that restores its pushed screen is a tab switch`() {
        tabs.kind("b", "com.x.B", "d", "com.x.Detail") // Detail pushed inside tab B, later saved
        assertThat(tabs.kind("a", "com.x.A", "d", "com.x.Detail")).isEqualTo(TransitionKind.TabSwitch)
    }

    @Test fun `enter and exit of the same transition agree`() {
        tabs.kind("a", "com.x.A", "d", "com.x.Detail")
        val first = tabs.kind("d", "com.x.Detail", "b", "com.x.B")
        assertThat(tabs.kind("d", "com.x.Detail", "b", "com.x.B")).isEqualTo(first)
    }

    @Test fun `unknown or null routes are screen transitions`() {
        assertThat(tabs.kind("x", null, "a", "com.x.A")).isEqualTo(TransitionKind.Screen)
        assertThat(tabs.kind("a", "com.x.A", "y", null)).isEqualTo(TransitionKind.Screen)
    }

    @Test fun `routes with arguments still match their tab`() {
        assertThat(tabs.kind("a", "com.x.A?filter=all", "b", "com.x.B/42")).isEqualTo(TransitionKind.TabSwitch)
    }

    @Test fun `a route that only shares a prefix with a tab is not that tab`() {
        assertThat(tabs.kind("ab", "com.x.AB", "b", "com.x.B")).isEqualTo(TransitionKind.Screen)
    }

    private val back = Tween(300, CubicBezier(0.32f, 0.72f, 0f, 1f))
    private val motion = MotionSpec.Gallery.copy(screenReturn = back)

    @Test fun `a push fades in on screenEnter`() {
        assertThat(motion.enterCurve(TransitionKind.Screen, isPop = false)).isEqualTo(motion.screenEnter)
    }

    @Test fun `a pop fades in on screenReturn`() {
        assertThat(motion.enterCurve(TransitionKind.Screen, isPop = true)).isEqualTo(back)
    }

    @Test fun `a tab switch during a pop stays the tab switch`() {
        assertThat(motion.enterCurve(TransitionKind.TabSwitch, isPop = true)).isEqualTo(motion.tabSwitch)
    }
}
