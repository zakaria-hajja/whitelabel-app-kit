package com.whitelabel.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonColors
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import com.whitelabel.core.designsystem.component.AppPrimaryButton
import com.whitelabel.core.designsystem.component.AppSecondaryButton
import com.whitelabel.core.designsystem.component.AppSheetDragHandle
import com.whitelabel.core.designsystem.component.appRadioButtonColors
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.designsystem.theme.ButtonStyle
import com.whitelabel.core.designsystem.theme.SelectionControlStyle
import com.whitelabel.core.designsystem.theme.StylePack
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test

/** The opt-in control looks as drawn: Gallery alone (Munch) keeps Material's; the opt-ins change them. */
class ControlStylesRenderTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val squared = StylePack.Gallery.copy(buttons = ButtonStyle.Squared)
    private val outline = StylePack.Gallery.copy(selectionControls = SelectionControlStyle.Outline)
    private lateinit var scheme: ColorScheme

    private fun show(pack: StylePack, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            AppTheme(darkTheme = false, stylePack = pack) {
                scheme = MaterialTheme.colorScheme
                Box(Modifier.padding(16.dp)) { content() }
            }
        }
        rule.waitForIdle()
    }

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first()
    }

    /** A pixel 4 px in from the button's top-left corner: inside a 4 dp corner, outside a pill's. */
    private fun cornerPixel(tag: String): Int {
        val bitmap = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        return bitmap.getPixel(4, 4)
    }

    private fun close(a: Int, b: Color): Boolean {
        val c = b.toArgb()
        fun ch(x: Int, s: Int) = (x shr s) and 0xFF
        return listOf(16, 8, 0).all { abs(ch(a, it) - ch(c, it)) <= 8 }
    }

    @Test
    fun squaredPrimaryButton_fillsItsCorner() {
        show(squared) { AppPrimaryButton("Save", onClick = {}, modifier = Modifier.testTag("b")) }
        assertThat(close(cornerPixel("b"), scheme.primary)).isEqualTo(true)
    }

    @Test
    fun galleryPrimaryButton_staysAPill() {
        show(StylePack.Gallery) { AppPrimaryButton("Save", onClick = {}, modifier = Modifier.testTag("b")) }
        assertThat(close(cornerPixel("b"), scheme.primary)).isEqualTo(false)
    }

    @Test
    fun squaredButtonLabels_are600() {
        show(squared) { AppPrimaryButton("Save", onClick = {}) }
        assertThat(rule.onNodeWithText("Save").layout().layoutInput.style.fontWeight).isEqualTo(FontWeight.SemiBold)
    }

    @Test
    fun galleryButtonLabels_keepMaterialsWeight() {
        show(StylePack.Gallery) { AppPrimaryButton("Save", onClick = {}) }
        assertThat(rule.onNodeWithText("Save").layout().layoutInput.style.fontWeight).isNotEqualTo(FontWeight.SemiBold)
    }

    @Test
    fun squaredOutlinedButton_hasOutlineBorderAndPrimaryLabel() {
        show(squared) { AppSecondaryButton("Edit", onClick = {}, modifier = Modifier.testTag("b")) }
        val bitmap = rule.onNodeWithTag("b").captureToImage().asAndroidBitmap()
        assertThat(close(bitmap.getPixel(1, bitmap.height / 2), scheme.outline)).isEqualTo(true)
        assertThat(rule.onNodeWithText("Edit").layout().layoutInput.style.color).isEqualTo(scheme.primary)
    }

    @Test
    fun galleryOutlinedButton_keepsMaterialsBorder() {
        show(StylePack.Gallery) { AppSecondaryButton("Edit", onClick = {}, modifier = Modifier.testTag("b")) }
        val bitmap = rule.onNodeWithTag("b").captureToImage().asAndroidBitmap()
        assertThat(close(bitmap.getPixel(1, bitmap.height / 2), scheme.outline)).isEqualTo(false)
    }

    @Test
    fun outlineSheetHandle_isOutline() {
        show(outline) { AppSheetDragHandle(Modifier.testTag("h")) }
        val bitmap = rule.onNodeWithTag("h").captureToImage().asAndroidBitmap()
        assertThat(close(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2), scheme.outline)).isEqualTo(true)
    }

    @Test
    fun gallerySheetHandle_keepsOnSurfaceVariant() {
        show(StylePack.Gallery) { AppSheetDragHandle(Modifier.testTag("h")) }
        val bitmap = rule.onNodeWithTag("h").captureToImage().asAndroidBitmap()
        assertThat(close(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2), scheme.onSurfaceVariant)).isEqualTo(true)
    }

    private fun radioColors(pack: StylePack): RadioButtonColors {
        lateinit var colors: RadioButtonColors
        rule.setContent { AppTheme(darkTheme = false, stylePack = pack) { scheme = MaterialTheme.colorScheme; colors = appRadioButtonColors() } }
        rule.waitForIdle()
        return colors
    }

    @Test
    fun outlineRadio_isOutlineWhenUnselected() {
        val colors = radioColors(outline)
        assertThat(colors.unselectedColor).isEqualTo(scheme.outline)
        assertThat(colors.selectedColor).isEqualTo(scheme.primary)
    }

    @Test
    fun galleryRadio_keepsMaterialsColors() {
        assertThat(radioColors(StylePack.Gallery).unselectedColor).isEqualTo(scheme.onSurfaceVariant)
    }
}
