package com.whitelabel.core.designsystem.motion

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.designsystem.theme.CubicBezier
import com.whitelabel.core.designsystem.theme.MotionCurve.Tween
import com.whitelabel.core.designsystem.theme.MotionSpec
import org.junit.jupiter.api.Test

class SharedCurveTest {

    private val back = Tween(300, CubicBezier(0.32f, 0.72f, 0f, 1f))
    private val motion = MotionSpec.Gallery.copy(sharedElementReturn = back)

    @Test fun `a push uses sharedElement`() {
        assertThat(motion.sharedCurve(isPop = false)).isEqualTo(MotionSpec.Gallery.sharedElement)
    }

    @Test fun `a pop uses sharedElementReturn`() {
        assertThat(motion.sharedCurve(isPop = true)).isEqualTo(back)
    }
}
