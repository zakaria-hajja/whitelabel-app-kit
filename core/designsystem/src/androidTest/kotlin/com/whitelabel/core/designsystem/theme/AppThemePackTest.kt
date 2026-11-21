package com.whitelabel.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.designsystem.component.AppCard
import org.junit.Rule
import org.junit.Test

class AppThemePackTest {

    @get:Rule val compose = createComposeRule()

    private lateinit var shapes: Shapes
    private lateinit var typography: Typography
    private lateinit var style: StylePack

    private fun capture(pack: StylePack?) {
        compose.setContent {
            val body = @androidx.compose.runtime.Composable {
                shapes = MaterialTheme.shapes
                typography = MaterialTheme.typography
                style = AppTheme.style
            }
            if (pack == null) AppTheme(darkTheme = false) { body() } else AppTheme(darkTheme = false, stylePack = pack) { body() }
        }
        compose.waitForIdle()
    }

    @Test fun materialKeepsTodaysTheme() {
        capture(pack = null) // an app that never picks a pack
        assertThat(shapes.small).isEqualTo(RoundedCornerShape(8.dp))
        assertThat(style).isEqualTo(StylePack.Material)
        assertThat(typography.displayLarge.fontWeight).isEqualTo(Typography().displayLarge.fontWeight)
    }

    @Test fun galleryAppliesCornersAndType() {
        capture(StylePack.Gallery)
        assertThat(shapes.small).isEqualTo(RoundedCornerShape(4.dp))
        assertThat(typography.displayLarge.fontWeight).isEqualTo(FontWeight.Normal)
    }

    /** The text style a raw Material TextButton (dialog, picker, snackbar action) gives its label. */
    private fun textButtonLabelWeight(pack: StylePack): FontWeight? {
        var weight: FontWeight? = null
        compose.setContent {
            AppTheme(darkTheme = false, stylePack = pack) {
                TextButton(onClick = {}) { weight = LocalTextStyle.current.fontWeight; Text("OK") }
            }
        }
        compose.waitForIdle()
        return weight
    }

    @Test fun labelWeightReachesRawMaterialTextButtons() {
        val pack = StylePack.Gallery.copy(type = StylePack.Gallery.type.copy(labelWeight = FontWeight.SemiBold))
        assertThat(textButtonLabelWeight(pack)).isEqualTo(FontWeight.SemiBold)
    }

    @Test fun unsetLabelWeightKeepsMaterialsButtonLabel() {
        assertThat(textButtonLabelWeight(StylePack.Gallery)).isEqualTo(Typography().labelLarge.fontWeight)
    }

    @Test fun galleryCardIsOutlined() {
        compose.setContent { AppTheme(darkTheme = false, stylePack = StylePack.Gallery) { AppCard { Text("x") } } }
        compose.onNodeWithTag("AppCard:Hairline").assertExists()
    }

    @Test fun materialCardIsShadowed() {
        compose.setContent { AppTheme(darkTheme = false) { AppCard { Text("x") } } }
        compose.onNodeWithTag("AppCard:Shadow").assertExists()
    }
}
