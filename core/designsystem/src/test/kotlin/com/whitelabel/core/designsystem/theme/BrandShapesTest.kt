package com.whitelabel.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class BrandShapesTest {

    private val material = Shapes()

    @Test fun `small, medium and large come from the corners`() {
        val shapes = shapesOf(Corners(small = 4.dp, medium = 8.dp, large = 14.dp))
        assertThat(shapes.small).isEqualTo(RoundedCornerShape(4.dp))
        assertThat(shapes.medium).isEqualTo(RoundedCornerShape(8.dp))
        assertThat(shapes.large).isEqualTo(RoundedCornerShape(14.dp))
    }

    @Test fun `unset extra small and extra large keep Material's shapes`() {
        val shapes = shapesOf(Corners(small = 4.dp, medium = 8.dp, large = 14.dp))
        assertThat(shapes.extraSmall).isEqualTo(material.extraSmall)
        assertThat(shapes.extraLarge).isEqualTo(material.extraLarge)
    }

    @Test fun `extra large brands dialogs and sheets when set`() {
        val shapes = shapesOf(Corners(small = 4.dp, medium = 8.dp, large = 14.dp, extraLarge = 14.dp))
        assertThat(shapes.extraLarge).isEqualTo(RoundedCornerShape(14.dp))
    }

    @Test fun `extra small brands snackbars, menus and tooltips when set`() {
        val shapes = shapesOf(Corners(small = 4.dp, medium = 8.dp, large = 14.dp, extraSmall = 2.dp))
        assertThat(shapes.extraSmall).isEqualTo(RoundedCornerShape(2.dp))
    }

    @Test fun `packs leave extra small and extra large unset`() {
        assertThat(StylePack.Gallery.corners).isEqualTo(Corners(small = 4.dp, medium = 8.dp, large = 14.dp, extraSmall = null, extraLarge = null))
    }
}
