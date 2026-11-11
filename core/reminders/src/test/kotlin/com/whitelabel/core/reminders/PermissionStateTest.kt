package com.whitelabel.core.reminders

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class PermissionStateTest {

    @Test fun `enabled notifications are granted`() {
        assertThat(permissionState(enabled = true, sdk = 33, askedBefore = false, showRationale = false))
            .isEqualTo(NotificationPermission.Granted)
    }

    @Test fun `never asked on Android 13 can be asked`() {
        assertThat(permissionState(enabled = false, sdk = 33, askedBefore = false, showRationale = false))
            .isEqualTo(NotificationPermission.NotAskedYet)
    }

    @Test fun `asked once with a rationale can be asked again`() {
        assertThat(permissionState(enabled = false, sdk = 33, askedBefore = true, showRationale = true))
            .isEqualTo(NotificationPermission.Denied)
    }

    @Test fun `asked with no rationale is blocked in settings`() {
        assertThat(permissionState(enabled = false, sdk = 34, askedBefore = true, showRationale = false))
            .isEqualTo(NotificationPermission.BlockedInSettings)
    }

    @Test fun `below Android 13 disabled notifications only change in settings`() {
        assertThat(permissionState(enabled = false, sdk = 32, askedBefore = false, showRationale = false))
            .isEqualTo(NotificationPermission.BlockedInSettings)
    }
}
