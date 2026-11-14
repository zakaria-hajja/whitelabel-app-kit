package com.whitelabel.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.dimensionResource
import com.whitelabel.core.designsystem.R

/** Material 3 [Shapes] from the pack's [corners], or from the `brand_corner_*` dimen resources when null. */
@Composable
fun brandShapes(corners: Corners? = null): Shapes = shapesOf(
    corners ?: Corners(
        small = dimensionResource(R.dimen.brand_corner_small),
        medium = dimensionResource(R.dimen.brand_corner_medium),
        large = dimensionResource(R.dimen.brand_corner_large),
        extraSmall = dimensionResource(R.dimen.brand_corner_extra_small),
        extraLarge = dimensionResource(R.dimen.brand_corner_extra_large),
    ),
)

/**
 * Material 3 [Shapes] for [corners]. An unset [Corners.extraSmall] or [Corners.extraLarge] keeps
 * Material's own shape (4 and 28 dp). Material's expressive shapes (`largeIncreased`,
 * `extraLargeIncreased`, `extraExtraLarge`) are experimental and keep their defaults.
 */
internal fun shapesOf(corners: Corners): Shapes {
    val material = Shapes()
    return Shapes(
        extraSmall = corners.extraSmall?.let(::RoundedCornerShape) ?: material.extraSmall,
        small = RoundedCornerShape(corners.small),
        medium = RoundedCornerShape(corners.medium),
        large = RoundedCornerShape(corners.large),
        extraLarge = corners.extraLarge?.let(::RoundedCornerShape) ?: material.extraLarge,
    )
}
