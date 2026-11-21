package com.whitelabel.core.designsystem.theme

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle

/**
 * Material 3 [Typography] using the optional `font/brand_display` and `font/brand_body` fonts, with
 * the style pack's [tweaks] (heading and button-label weights, titles in the body font). A client that ships neither
 * font gets the system font. The lookup is by name, once per context.
 */
@Composable
fun brandTypography(tweaks: TypeTweaks = StylePack.Material.type): Typography {
    val context = LocalContext.current
    return remember(context, tweaks) {
        typographyOf(context.brandFontFamily("brand_display"), context.brandFontFamily("brand_body"), tweaks)
    }
}

/**
 * Material's type scale in the [display] and [body] fonts with [tweaks] applied. `labelLarge` is the
 * role Material's own buttons use (dialog, picker and snackbar actions), so [TypeTweaks.labelWeight]
 * reaches every one of them.
 */
internal fun typographyOf(display: FontFamily, body: FontFamily, tweaks: TypeTweaks): Typography {
    val title = if (tweaks.titlesUseBodyFont) body else display
    val base = Typography()
    fun TextStyle.withFont(family: FontFamily, weight: FontWeight?) = copy(fontFamily = family, fontWeight = weight ?: fontWeight)
    return Typography(
        displayLarge = base.displayLarge.withFont(display, tweaks.displayWeight),
        displayMedium = base.displayMedium.withFont(display, tweaks.displayWeight),
        displaySmall = base.displaySmall.withFont(display, tweaks.displayWeight),
        headlineLarge = base.headlineLarge.withFont(display, tweaks.headlineWeight),
        headlineMedium = base.headlineMedium.withFont(display, tweaks.headlineWeight),
        headlineSmall = base.headlineSmall.withFont(display, tweaks.headlineWeight),
        titleLarge = base.titleLarge.copy(fontFamily = title),
        titleMedium = base.titleMedium.copy(fontFamily = title),
        titleSmall = base.titleSmall.copy(fontFamily = title),
        bodyLarge = base.bodyLarge.copy(fontFamily = body),
        bodyMedium = base.bodyMedium.copy(fontFamily = body),
        bodySmall = base.bodySmall.copy(fontFamily = body),
        labelLarge = base.labelLarge.withFont(body, tweaks.labelWeight),
        labelMedium = base.labelMedium.copy(fontFamily = body),
        labelSmall = base.labelSmall.copy(fontFamily = body),
    )
}

// Fonts are optional and client-provided, so they cannot be referenced as R.font.
@SuppressLint("DiscouragedApi")
private fun Context.brandFontFamily(name: String): FontFamily {
    val id = resources.getIdentifier(name, "font", packageName)
    return if (id != 0) FontFamily(Font(id)) else FontFamily.Default
}
