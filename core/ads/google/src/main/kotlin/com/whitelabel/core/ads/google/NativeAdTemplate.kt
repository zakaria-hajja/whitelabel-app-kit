package com.whitelabel.core.ads.google

import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.google.android.gms.ads.nativead.NativeAd
import com.whitelabel.core.ads.R
import com.whitelabel.core.ads.google.compose.NativeAdAdvertiserView
import com.whitelabel.core.ads.google.compose.NativeAdBodyView
import com.whitelabel.core.ads.google.compose.NativeAdCallToActionView
import com.whitelabel.core.ads.google.compose.NativeAdHeadlineView
import com.whitelabel.core.ads.google.compose.NativeAdIconView
import com.whitelabel.core.ads.google.compose.NativeAdMediaView
import com.whitelabel.core.ads.google.compose.NativeAdView
import com.whitelabel.core.designsystem.theme.AppTheme
import kotlin.math.ceil
import kotlin.math.max

/**
 * Sizes of the native list-item template (the Designer's option A, "media left"), the same numbers
 * as iOS's `NativeAdTemplate` in pt. [Height] is the card at font scale ≤ 1; [height] grows it with
 * the app's actual line heights at its font scale, computed once so the card never changes size on screen.
 */
internal object NativeAdTemplate {
    /** The media view, square; the policy minimum is 120. */
    val MediaSize = 120.dp

    /** The "Ad" chip's minimum size; the policy minimum is 15 px. */
    val BadgeMinSize = 16.dp

    /** The top-right square kept free for the SDK's AdChoices icon. */
    val AdChoicesInset = 24.dp

    /** The card's height at font scale ≤ 1. */
    val Height: Dp = height(1f)

    /** The card's height at [fontScale] with the spec's line heights ([Lines.spec]). */
    fun height(fontScale: Float): Dp = height(Lines.spec(fontScale))

    /**
     * The card's height for [lines]: padding 16 + header max(24, label, advertiser) + gap 8 + main row
     * max(media, headline 2 lines + 4 + body 2 lines + 4 + bottom row max(40, CTA line + 20)) + padding 16,
     * rounded up. Taller type roles grow the card, so the CTA row is never squeezed out.
     */
    fun height(lines: Lines): Dp {
        val header = maxOf(24f, lines.label, lines.body)
        val column = 2 * lines.headline + 4f + 2 * lines.body + 4f + max(40f, lines.cta + 20f)
        val main = max(MediaSize.value, column)
        return ceil(16f + header + 8f + main + 16f).dp
    }

    /**
     * Line heights in dp of the template's text roles: [label] the chip (`labelSmall`), [headline]
     * (`titleSmall`), [body] the body and advertiser (`bodySmall`), [cta] (`labelLarge`).
     */
    data class Lines(val label: Float, val headline: Float, val body: Float, val cta: Float) {
        companion object {
            /** The spec's 16 / 20 / 16 / 20 (Material 3's defaults) at [fontScale]. */
            fun spec(fontScale: Float): Lines {
                val s = max(fontScale, 0f)
                return Lines(label = 16f * s, headline = 20f * s, body = 16f * s, cta = 20f * s)
            }

            /**
             * The app's actual line heights. A style without one falls back to the spec's 16 / 20 sp, while
             * `Text` then uses the font's natural line height, which some brand fonts make taller: give
             * such a type role a `lineHeight` (Material 3's styles always have one) or the CTA may clip.
             */
            fun of(density: Density, label: TextStyle, headline: TextStyle, body: TextStyle, cta: TextStyle): Lines {
                val spec = spec(1f)
                fun TextStyle.lineDp(fallbackSp: Float): Float = with(density) {
                    when {
                        lineHeight.isSp -> lineHeight.toDp().value
                        lineHeight.isEm && fontSize.isSp -> (fontSize.value * lineHeight.value).sp.toDp().value
                        else -> fallbackSp.sp.toDp().value
                    }
                }
                return Lines(label.lineDp(spec.label), headline.lineDp(spec.headline), body.lineDp(spec.body), cta.lineDp(spec.cta))
            }
        }
    }

    /** The "Ad" chip's horizontal padding (spec: 6, between the kit's xs and sm). */
    val BadgePadding = 6.dp

    /** The advertiser's icon, square. */
    val IconSize = 40.dp

    /** The CTA's minimum height. */
    val CtaMinHeight = 40.dp
}

/**
 * The fixed list-item template for [ad]: a bordered `surface` card with the "Ad" chip top-left, the
 * advertiser beside it, the media (fit, never cropped) on the left, then the headline, body, icon
 * and an outlined CTA. Themed only by tokens. Only the registered asset views open the ad: the
 * container has no click handler, and the CTA is a plain box (a Compose button would swallow the click).
 * Text is never rewritten, only truncated. The top-right [NativeAdTemplate.AdChoicesInset] square is
 * left to the SDK's AdChoices icon, and the card is not clipped so the icon is never cut.
 */
@Composable
internal fun NativeAdTemplate(ad: NativeAd, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val spacing = AppTheme.spacing
    val cardShape = MaterialTheme.shapes.large
    val smallShape = MaterialTheme.shapes.small
    val density = LocalDensity.current
    val height = remember(density, typography) {
        NativeAdTemplate.height(
            NativeAdTemplate.Lines.of(density, typography.labelSmall, typography.titleSmall, typography.bodySmall, typography.labelLarge),
        )
    }
    NativeAdView(
        nativeAd = ad,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(colors.surface, cardShape)
            .border(1.dp, colors.outlineVariant, cardShape),
    ) {
        // Screen readers read "Ad, <headline>" first (design spec §5), then the rest in layout order.
        Column(Modifier.fillMaxSize().padding(spacing.md).semantics { isTraversalGroup = true }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp)
                    .padding(end = NativeAdTemplate.AdChoicesInset),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .semantics { traversalIndex = -2f }
                        .defaultMinSize(NativeAdTemplate.BadgeMinSize, NativeAdTemplate.BadgeMinSize)
                        .background(colors.onSurface, smallShape)
                        .padding(horizontal = NativeAdTemplate.BadgePadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.ads_label), style = typography.labelSmall, color = colors.surface, maxLines = 1)
                }
                ad.advertiser?.takeIf { it.isNotEmpty() }?.let { advertiser ->
                    NativeAdAdvertiserView(Modifier.weight(1f, fill = false)) {
                        Text(
                            advertiser,
                            style = typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(spacing.sm))
            Row(Modifier.fillMaxWidth().weight(1f)) {
                NativeAdMediaView(
                    modifier = Modifier.size(NativeAdTemplate.MediaSize).background(colors.surface),
                    scaleType = ImageView.ScaleType.CENTER_INSIDE,
                )
                Spacer(Modifier.width(spacing.md))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    ad.headline?.let { headline ->
                        NativeAdHeadlineView(Modifier.semantics { traversalIndex = -1f }) {
                            Text(headline, style = typography.titleSmall, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    ad.body?.takeIf { it.isNotEmpty() }?.let { body ->
                        Spacer(Modifier.height(spacing.xs))
                        NativeAdBodyView {
                            Text(body, style = typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.weight(1f).heightIn(min = spacing.xs))
                    val icon = ad.icon?.drawable
                    val cta = ad.callToAction?.takeIf { it.isNotEmpty() }
                    if (icon != null || cta != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            if (icon != null) {
                                // A drawable without an intrinsic size (a color) would make an empty bitmap throw.
                                val bitmap = remember(icon) {
                                    icon.toBitmap(icon.intrinsicWidth.coerceAtLeast(1), icon.intrinsicHeight.coerceAtLeast(1)).asImageBitmap()
                                }
                                NativeAdIconView(Modifier.size(NativeAdTemplate.IconSize)) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().clip(smallShape),
                                    )
                                }
                            }
                            if (cta != null) {
                                NativeAdCallToActionView(Modifier.weight(1f, fill = false)) {
                                    Box(
                                        modifier = Modifier
                                            .heightIn(min = NativeAdTemplate.CtaMinHeight)
                                            .border(1.dp, colors.outline, smallShape)
                                            .padding(horizontal = spacing.md),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(cta, style = typography.labelLarge, color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
