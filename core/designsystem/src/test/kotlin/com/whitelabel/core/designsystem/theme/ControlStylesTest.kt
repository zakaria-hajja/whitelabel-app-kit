package com.whitelabel.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

/**
 * The opt-in control looks (buttons, selection controls, settings value rows). Every default keeps
 * Material's own value (null = "don't pass anything"), so an app that doesn't opt in renders as before.
 */
class ControlStylesTest {

    private val shapes = shapesOf(Corners(small = 4.dp, medium = 8.dp, large = 14.dp))
    private val scheme = lightColorScheme(
        primary = Color(0xFF3D6838),
        outline = Color(0xFF687061),
        onSurfaceVariant = Color(0xFF444B3F),
    )

    @Test fun `no pack opts in, so Munch and every existing app keep their controls`() {
        for (pack in listOf(StylePack.Material, StylePack.Gallery)) {
            assertThat(pack.buttons).isEqualTo(ButtonStyle.Pill)
            assertThat(pack.selectionControls).isEqualTo(SelectionControlStyle.Material)
            assertThat(pack.bottomBarItems).isEqualTo(BottomBarItemStyle.Material)
        }
    }

    @Test fun `pill buttons keep every Material default`() {
        val pill = ButtonStyle.Pill
        assertThat(pill.shape(shapes)).isNull()
        assertThat(pill.labelWeight).isNull()
        assertThat(pill.outlinedBorderColor(scheme)).isNull()
        assertThat(pill.outlinedLabelColor(scheme)).isNull()
    }

    @Test fun `squared buttons take the small corner and a 600 label`() {
        val squared = ButtonStyle.Squared
        assertThat(squared.shape(shapes)).isEqualTo(RoundedCornerShape(4.dp))
        assertThat(squared.labelWeight).isEqualTo(FontWeight.SemiBold)
    }

    @Test fun `squared outlined button has an outline border and a primary label`() {
        assertThat(ButtonStyle.Squared.outlinedBorderColor(scheme)).isEqualTo(Color(0xFF687061))
        assertThat(ButtonStyle.Squared.outlinedLabelColor(scheme)).isEqualTo(Color(0xFF3D6838))
    }

    @Test fun `material selection controls keep Material's colors`() {
        assertThat(SelectionControlStyle.Material.sheetHandleColor(scheme)).isNull()
        assertThat(SelectionControlStyle.Material.radioUnselectedColor(scheme)).isNull()
    }

    @Test fun `outline selection controls draw the sheet handle and unselected radio in outline`() {
        assertThat(SelectionControlStyle.Outline.sheetHandleColor(scheme)).isEqualTo(Color(0xFF687061))
        assertThat(SelectionControlStyle.Outline.radioUnselectedColor(scheme)).isEqualTo(Color(0xFF687061))
    }

    @Test fun `only disclosure rows show a chevron, and only when they open something`() {
        assertThat(ValueRowLayout.Disclosure.showsChevron(clickable = true)).isEqualTo(true)
        assertThat(ValueRowLayout.Disclosure.showsChevron(clickable = false)).isEqualTo(false)
        assertThat(ValueRowLayout.Inline.showsChevron(clickable = true)).isEqualTo(false)
        assertThat(ValueRowLayout.Stacked.showsChevron(clickable = true)).isEqualTo(false)
    }
}
