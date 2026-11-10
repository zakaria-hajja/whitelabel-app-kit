package com.whitelabel.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
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
}
