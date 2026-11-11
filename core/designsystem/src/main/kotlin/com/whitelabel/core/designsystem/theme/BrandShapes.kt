package com.whitelabel.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.dimensionResource
import com.whitelabel.core.designsystem.R

/** Material 3 [Shapes] from the pack's [corners], or from the `brand_corner_*` dimen resources when null. */
@Composable
fun brandShapes(corners: Corners? = null): Shapes = if (corners != null) {
    Shapes(
        small = RoundedCornerShape(corners.small),
        medium = RoundedCornerShape(corners.medium),
        large = RoundedCornerShape(corners.large),
    )
} else {
    Shapes(
        small = RoundedCornerShape(dimensionResource(R.dimen.brand_corner_small)),
        medium = RoundedCornerShape(dimensionResource(R.dimen.brand_corner_medium)),
        large = RoundedCornerShape(dimensionResource(R.dimen.brand_corner_large)),
    )
}
