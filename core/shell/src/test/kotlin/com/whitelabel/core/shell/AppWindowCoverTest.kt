package com.whitelabel.core.shell

import android.view.View
import android.view.WindowManager.LayoutParams
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class AppWindowCoverTest {

    @Test fun `dialogs, bottom sheets and popups are covered`() {
        assertThat(coversWindow(LayoutParams.TYPE_APPLICATION, View.VISIBLE)).isTrue()
        assertThat(coversWindow(LayoutParams.TYPE_APPLICATION_PANEL, View.VISIBLE)).isTrue()
        assertThat(coversWindow(LayoutParams.TYPE_APPLICATION_SUB_PANEL, View.VISIBLE)).isTrue()
        assertThat(coversWindow(LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG, View.VISIBLE)).isTrue()
    }

    @Test fun `the activity's own window is left to the platform, which hides it while stopped`() {
        assertThat(coversWindow(LayoutParams.TYPE_BASE_APPLICATION, View.VISIBLE)).isFalse()
        assertThat(coversWindow(LayoutParams.TYPE_APPLICATION_STARTING, View.VISIBLE)).isFalse()
    }

    @Test fun `windows meant to show outside the app are never covered`() {
        assertThat(coversWindow(LayoutParams.TYPE_APPLICATION_OVERLAY, View.VISIBLE)).isFalse()
        assertThat(coversWindow(LayoutParams.TYPE_TOAST, View.VISIBLE)).isFalse()
    }

    @Test fun `a window the app already hid is not taken over, so the reveal can't show it`() {
        assertThat(coversWindow(LayoutParams.TYPE_APPLICATION, View.INVISIBLE)).isFalse()
        assertThat(coversWindow(LayoutParams.TYPE_APPLICATION, View.GONE)).isFalse()
    }
}
