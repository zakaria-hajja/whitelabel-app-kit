package com.whitelabel.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight

// What each opt-in control style resolves to. Null means "keep Material's default": the components
// pass nothing, so a pack that doesn't opt in renders exactly as before.

/** The button shape, or null for Material's pill. */
internal fun ButtonStyle.shape(shapes: Shapes): Shape? = when (this) {
    ButtonStyle.Pill -> null
    ButtonStyle.Squared -> shapes.small
}

/** The button label weight, or null for Material's. */
internal val ButtonStyle.labelWeight: FontWeight?
    get() = when (this) {
        ButtonStyle.Pill -> null
        ButtonStyle.Squared -> FontWeight.SemiBold
    }

/** The outlined button's 1 dp border color, or null for Material's. */
internal fun ButtonStyle.outlinedBorderColor(scheme: ColorScheme): Color? = when (this) {
    ButtonStyle.Pill -> null
    ButtonStyle.Squared -> scheme.outline
}

/** The outlined button's label color, or null for Material's. */
internal fun ButtonStyle.outlinedLabelColor(scheme: ColorScheme): Color? = when (this) {
    ButtonStyle.Pill -> null
    ButtonStyle.Squared -> scheme.primary
}

/** The sheet drag handle color, or null for Material's. */
internal fun SelectionControlStyle.sheetHandleColor(scheme: ColorScheme): Color? = when (this) {
    SelectionControlStyle.Material -> null
    SelectionControlStyle.Outline -> scheme.outline
}

/** An unselected radio button's color, or null for Material's. */
internal fun SelectionControlStyle.radioUnselectedColor(scheme: ColorScheme): Color? = when (this) {
    SelectionControlStyle.Material -> null
    SelectionControlStyle.Outline -> scheme.outline
}
