package com.whitelabel.core.designsystem.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.isNull
import com.whitelabel.core.designsystem.theme.MotionCurve.Spring
import com.whitelabel.core.designsystem.theme.MotionCurve.Tween
import org.junit.jupiter.api.Test

class StylePackTest {

    private val standard = CubicBezier(0.2f, 0f, 0f, 1f)

    private fun MotionSpec.curves() = listOf(screenEnter, screenExit, sharedElement, tabSwitch, emphasis, snackbar)

    @Test fun `material reproduces today`() {
        val m = StylePack.Material
        assertThat(m.corners).isNull()
        assertThat(m.surfaces).isEqualTo(SurfaceStyle.Shadow)
        assertThat(m.sizing).isEqualTo(Sizing(controlHeight = 48.dp, listRowMinHeight = 56.dp, screenGutter = 16.dp))
        assertThat(m.haptics).isEmpty()
        assertThat(m.type).isEqualTo(TypeTweaks(displayWeight = null, headlineWeight = null, titlesUseBodyFont = false))
        // Navigation Compose's default: tween(700) with FastOutSlowIn.
        val navDefault = Tween(700, CubicBezier(0.4f, 0f, 0.2f, 1f))
        assertThat(m.motion.screenEnter).isEqualTo(navDefault)
        assertThat(m.motion.screenExit).isEqualTo(navDefault)
        assertThat(m.motion.tabSwitch).isEqualTo(navDefault)
        assertThat(m.motion.tabSwitchScale).isEqualTo(1f)
    }

    @Test fun `material keeps today's chrome`() {
        val m = StylePack.Material
        assertThat(m.topBar).isEqualTo(TopBarStyle.Raised)
        assertThat(m.sectionHeader).isEqualTo(SectionHeaderStyle.Accent)
        assertThat(m.valueRows).isEqualTo(ValueRowLayout.Inline)
    }

    @Test fun `bottom bar stays on surface container unless an app opts in`() {
        assertThat(StylePack.Material.bottomBarOnSurface).isFalse()
        assertThat(StylePack.Gallery.bottomBarOnSurface).isFalse()
        assertThat(StylePack.Gallery.copy(bottomBarOnSurface = true).bottomBarOnSurface).isTrue()
    }

    @Test fun `gallery chrome follows the design`() {
        val g = StylePack.Gallery
        assertThat(g.topBar).isEqualTo(TopBarStyle.Flat)
        assertThat(g.sectionHeader).isEqualTo(SectionHeaderStyle.Caption)
        assertThat(g.valueRows).isEqualTo(ValueRowLayout.Stacked)
    }

    @Test fun `gallery matches the design`() {
        val g = StylePack.Gallery
        assertThat(g.corners).isEqualTo(Corners(small = 4.dp, medium = 8.dp, large = 14.dp))
        assertThat(g.surfaces).isEqualTo(SurfaceStyle.Hairline)
        assertThat(g.sizing).isEqualTo(Sizing(controlHeight = 52.dp, listRowMinHeight = 72.dp, screenGutter = 20.dp))
        assertThat(g.type).isEqualTo(TypeTweaks(FontWeight.Normal, FontWeight.Normal, titlesUseBodyFont = true))
        val emphasized = Tween(420, CubicBezier(0.2f, 0f, 0f, 1f))
        assertThat(g.motion.sharedElement).isEqualTo(emphasized)
        assertThat(g.motion.screenEnter).isEqualTo(emphasized)
        assertThat(g.motion.screenExit).isEqualTo(Tween(320, CubicBezier(0.3f, 0f, 0.8f, 0.15f)))
        assertThat(g.motion.tabSwitch).isEqualTo(Tween(210, standard))
        assertThat(g.motion.tabSwitchScale).isEqualTo(0.96f)
        assertThat(g.motion.emphasis).isEqualTo(Spring(dampingRatio = 0.55f, stiffness = 380f))
        assertThat(g.motion.snackbar).isEqualTo(Tween(200, standard))
        assertThat(g.haptics).isEqualTo(
            mapOf(
                HapticEvent.Confirm to HapticPattern.Tick,
                HapticEvent.Success to HapticPattern.Confirm,
                HapticEvent.Toggle to HapticPattern.Toggle,
            ),
        )
    }

    @Test fun `reduced collapses every curve to a 150 ms linear crossfade`() {
        val crossfade = Tween(150, CubicBezier(0f, 0f, 1f, 1f))
        for (pack in listOf(StylePack.Material, StylePack.Gallery)) {
            val reduced = pack.reduced()
            reduced.motion.curves().forEach { assertThat(it).isEqualTo(crossfade) }
            assertThat(reduced.motion.tabSwitchScale).isEqualTo(1f)
            assertThat(reduced.copy(motion = pack.motion)).isEqualTo(pack)
        }
    }

    @Test fun `copy allows app variation`() {
        val custom = StylePack.Gallery.copy(sizing = StylePack.Gallery.sizing.copy(controlHeight = 56.dp))
        assertThat(custom.sizing.controlHeight).isEqualTo(56.dp)
    }
}
