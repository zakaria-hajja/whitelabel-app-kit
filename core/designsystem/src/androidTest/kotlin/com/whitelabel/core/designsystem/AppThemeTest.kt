package com.whitelabel.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.designsystem.theme.brandColorScheme
import com.whitelabel.core.designsystem.theme.contrastPairs
import com.whitelabel.core.designsystem.theme.contrastRatio
import org.junit.Rule
import org.junit.Test

class AppThemeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun darkTheme_usesBrandDarkPrimary() {
        var actual = Color.Unspecified
        var expected = Color.Unspecified
        rule.setContent {
            AppTheme(darkTheme = true) {
                actual = MaterialTheme.colorScheme.primary
                expected = colorResource(R.color.brand_dark_primary)
            }
        }
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun lightTheme_usesBrandLightPrimary() {
        var actual = Color.Unspecified
        var expected = Color.Unspecified
        rule.setContent {
            AppTheme(darkTheme = false) {
                actual = MaterialTheme.colorScheme.primary
                expected = colorResource(R.color.brand_light_primary)
            }
        }
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun defaultBrandColors_meetContrastAA() {
        val failures = mutableListOf<String>()
        rule.setContent {
            listOf(false, true).forEach { dark ->
                brandColorScheme(dark).contrastPairs().forEach {
                    val ratio = contrastRatio(it.foreground, it.background)
                    if (ratio < 4.5) failures += "${if (dark) "dark" else "light"} ${it.name} = $ratio"
                }
            }
        }
        rule.waitForIdle()
        assertThat(failures.joinToString()).isEqualTo("")
    }

    /**
     * With only the foundation's default resources, every role equals Material's own baseline, so an
     * app that sets none of the brand colors looks exactly like Material. Also fails if a Material
     * upgrade moves its baseline: then update the defaults in brand.xml.
     */
    @Test
    fun defaultBrandColors_equalMaterialBaseline() {
        val mismatches = mutableListOf<String>()
        rule.setContent {
            listOf(false, true).forEach { dark ->
                val brand = brandColorScheme(dark)
                val baseline = if (dark) darkColorScheme() else lightColorScheme()
                allRoles.forEach { (name, role) ->
                    if (role(brand) != role(baseline)) {
                        mismatches += "${if (dark) "dark" else "light"} $name: ${role(brand)} != ${role(baseline)}"
                    }
                }
            }
        }
        rule.waitForIdle()
        assertThat(mismatches.joinToString()).isEqualTo("")
    }

    /** Every color role of [ColorScheme], which has no equals of its own. */
    private val allRoles: List<Pair<String, (ColorScheme) -> Color>> = listOf(
        "primary" to { it.primary },
        "onPrimary" to { it.onPrimary },
        "primaryContainer" to { it.primaryContainer },
        "onPrimaryContainer" to { it.onPrimaryContainer },
        "inversePrimary" to { it.inversePrimary },
        "secondary" to { it.secondary },
        "onSecondary" to { it.onSecondary },
        "secondaryContainer" to { it.secondaryContainer },
        "onSecondaryContainer" to { it.onSecondaryContainer },
        "tertiary" to { it.tertiary },
        "onTertiary" to { it.onTertiary },
        "tertiaryContainer" to { it.tertiaryContainer },
        "onTertiaryContainer" to { it.onTertiaryContainer },
        "background" to { it.background },
        "onBackground" to { it.onBackground },
        "surface" to { it.surface },
        "onSurface" to { it.onSurface },
        "surfaceVariant" to { it.surfaceVariant },
        "onSurfaceVariant" to { it.onSurfaceVariant },
        "surfaceTint" to { it.surfaceTint },
        "inverseSurface" to { it.inverseSurface },
        "inverseOnSurface" to { it.inverseOnSurface },
        "error" to { it.error },
        "onError" to { it.onError },
        "errorContainer" to { it.errorContainer },
        "onErrorContainer" to { it.onErrorContainer },
        "outline" to { it.outline },
        "outlineVariant" to { it.outlineVariant },
        "scrim" to { it.scrim },
        "surfaceBright" to { it.surfaceBright },
        "surfaceDim" to { it.surfaceDim },
        "surfaceContainer" to { it.surfaceContainer },
        "surfaceContainerHigh" to { it.surfaceContainerHigh },
        "surfaceContainerHighest" to { it.surfaceContainerHighest },
        "surfaceContainerLow" to { it.surfaceContainerLow },
        "surfaceContainerLowest" to { it.surfaceContainerLowest },
        "primaryFixed" to { it.primaryFixed },
        "primaryFixedDim" to { it.primaryFixedDim },
        "onPrimaryFixed" to { it.onPrimaryFixed },
        "onPrimaryFixedVariant" to { it.onPrimaryFixedVariant },
        "secondaryFixed" to { it.secondaryFixed },
        "secondaryFixedDim" to { it.secondaryFixedDim },
        "onSecondaryFixed" to { it.onSecondaryFixed },
        "onSecondaryFixedVariant" to { it.onSecondaryFixedVariant },
        "tertiaryFixed" to { it.tertiaryFixed },
        "tertiaryFixedDim" to { it.tertiaryFixedDim },
        "onTertiaryFixed" to { it.onTertiaryFixed },
        "onTertiaryFixedVariant" to { it.onTertiaryFixedVariant },
    )
}
