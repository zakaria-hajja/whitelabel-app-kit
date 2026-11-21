package com.whitelabel.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em

// The text styles of shared chrome. A pack's TextSpec replaces the Material role; unset, the role
// stays exactly as before, so a pack that doesn't opt in renders the same.

/** The Caption section header's text style. */
internal fun sectionCaptionStyle(spec: TextSpec?, typography: Typography): TextStyle =
    spec?.resolve(typography) ?: typography.labelSmall.copy(letterSpacing = 0.12.em)

/** The flat top bar's title style. */
internal fun flatTopBarTitleStyle(spec: TextSpec?, typography: Typography): TextStyle =
    spec?.resolve(typography) ?: typography.headlineSmall

/** [this] spec in its brand font, built on that font's base role so the theme's text defaults carry over. */
private fun TextSpec.resolve(typography: Typography): TextStyle {
    val base = when (font) {
        BrandFont.Display -> typography.displayLarge
        BrandFont.Body -> typography.bodyLarge
    }
    return base.copy(fontSize = size, lineHeight = lineHeight, fontWeight = weight, letterSpacing = letterSpacing)
}
