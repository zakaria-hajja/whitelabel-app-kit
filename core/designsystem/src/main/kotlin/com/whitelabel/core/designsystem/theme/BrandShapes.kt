package com.whitelabel.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.dimensionResource
import com.whitelabel.core.designsystem.R

/** Material 3 [Shapes] built from the `brand_corner_*` dimen resources. */
@Composable
fun brandShapes(): Shapes = Shapes(
    small = RoundedCornerShape(dimensionResource(R.dimen.brand_corner_small)),
    medium = RoundedCornerShape(dimensionResource(R.dimen.brand_corner_medium)),
    large = RoundedCornerShape(dimensionResource(R.dimen.brand_corner_large)),
)
