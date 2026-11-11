package com.whitelabel.core.designsystem.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Android's "Remove animations" sets the animator duration scale to 0. */
internal fun isReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

/** Whether the user asked the system to remove animations. Read once per context. */
@Composable
internal fun systemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        isReducedMotion(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
}
