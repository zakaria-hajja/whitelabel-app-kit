package com.whitelabel.core.designsystem.theme

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

/**
 * Material 3 [Typography] using the optional `font/brand_display` and `font/brand_body` fonts.
 * A client that ships neither gets the system font. The lookup is by name, once per context.
 */
@Composable
fun brandTypography(): Typography {
    val context = LocalContext.current
    return remember(context) {
        val display = context.brandFontFamily("brand_display")
        val body = context.brandFontFamily("brand_body")
        val base = Typography()
        Typography(
            displayLarge = base.displayLarge.copy(fontFamily = display),
            displayMedium = base.displayMedium.copy(fontFamily = display),
            displaySmall = base.displaySmall.copy(fontFamily = display),
            headlineLarge = base.headlineLarge.copy(fontFamily = display),
            headlineMedium = base.headlineMedium.copy(fontFamily = display),
            headlineSmall = base.headlineSmall.copy(fontFamily = display),
            titleLarge = base.titleLarge.copy(fontFamily = display),
            titleMedium = base.titleMedium.copy(fontFamily = display),
            titleSmall = base.titleSmall.copy(fontFamily = display),
            bodyLarge = base.bodyLarge.copy(fontFamily = body),
            bodyMedium = base.bodyMedium.copy(fontFamily = body),
            bodySmall = base.bodySmall.copy(fontFamily = body),
            labelLarge = base.labelLarge.copy(fontFamily = body),
            labelMedium = base.labelMedium.copy(fontFamily = body),
            labelSmall = base.labelSmall.copy(fontFamily = body),
        )
    }
}

// Fonts are optional and client-provided, so they cannot be referenced as R.font.
@SuppressLint("DiscouragedApi")
private fun Context.brandFontFamily(name: String): FontFamily {
    val id = resources.getIdentifier(name, "font", packageName)
    return if (id != 0) FontFamily(Font(id)) else FontFamily.Default
}
