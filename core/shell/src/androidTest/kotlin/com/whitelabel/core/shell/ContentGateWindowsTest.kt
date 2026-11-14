package com.whitelabel.core.shell

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.Dialog
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.shell.model.ContentGate
import com.whitelabel.core.shell.model.ContentGateState
import com.whitelabel.core.shell.model.shellScreen
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The app's windows across a real background and return (Home, then `am start`). Composition
 * dialogs are windows of their own: when the app returns, the window manager shows them and they
 * draw **before** the activity's onStart, so before the gate decides. Nothing may draw there.
 */
class ContentGateWindowsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** Locks on return when [lockOnReturn]; counts the process lifecycle calls. */
    private class ReturnGate : ContentGate {
        override val state = MutableStateFlow(ContentGateState.OPEN)
        override val protectsAppSwitcher = MutableStateFlow(false)
        @Volatile var lockOnReturn = false
        @Volatile var backgrounds = 0
        @Volatile var foregrounds = 0
        override fun onAppForeground() {
            if (backgrounds > 0 && lockOnReturn) state.value = ContentGateState.LOCKED
            foregrounds++
        }
        override fun onAppBackground() {
            backgrounds++
        }
    }

    private val gate = ReturnGate()
    private val lifecycle = ContentGateLifecycle(gate)

    /** Draws of the dialog's window while it is visible, recorded once [recording] is on. */
    private val dialogDraws = mutableListOf<Long>()
    @Volatile private var recording = false
    private var dialogRoot: View? = null

    @Before fun register() {
        registerContentGateLifecycle(lifecycle)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @After fun unregister() {
        unregisterContentGateLifecycle(lifecycle)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Composable
    private fun DialogProbe() {
        val root = LocalView.current.rootView
        DisposableEffect(root) {
            dialogRoot = root
            val listener = ViewTreeObserver.OnDrawListener {
                if (recording && root.visibility == View.VISIBLE) dialogDraws += SystemClock.uptimeMillis()
            }
            root.viewTreeObserver.addOnDrawListener(listener)
            onDispose { if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnDrawListener(listener) }
        }
    }

    private fun show() = rule.setContent {
        val gateState by gate.state.collectAsState()
        AppTheme {
            ShellRoot(shellScreen(true, gateState), lockScreen = { Text("Lock screen") }, onContentShown = lifecycle::onContentShown) {
                var open by remember { mutableStateOf(false) }
                Text("Open dialog", Modifier.clickable { open = true })
                if (open) Dialog(onDismissRequest = { open = false }) {
                    DialogProbe()
                    var taps by remember { mutableIntStateOf(0) }
                    Column {
                        Text("Secret dialog")
                        Text("Dialog taps $taps", Modifier.clickable { taps++ })
                    }
                }
            }
        }
    }

    private fun shell(command: String) {
        val pfd: ParcelFileDescriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "timed out waiting for $what" }
            SystemClock.sleep(50)
        }
    }

    /** Home until the process is in the background (ON_STOP is debounced), then back to the activity. */
    private fun backgroundAndReturn() {
        val activity = rule.activity
        val backgroundsBefore = gate.backgrounds
        shell("input keyevent KEYCODE_HOME")
        waitUntil("the process to stop") { gate.backgrounds > backgroundsBefore }
        rule.runOnIdle { recording = true }
        val foregroundsBefore = gate.foregrounds
        shell("am start -W -f 0x10020000 -n ${activity.packageName}/${activity.javaClass.name}")
        waitUntil("the process to start") { gate.foregrounds > foregroundsBefore }
        rule.waitForIdle()
    }

    @Test
    fun relockAfterBackground_noDialogFrameDrawsOnReturn() {
        gate.lockOnReturn = true
        show()
        rule.onNodeWithText("Open dialog").performClick()
        rule.onNodeWithText("Secret dialog").assertExists()

        backgroundAndReturn()

        rule.onNodeWithText("Lock screen").assertExists()
        rule.onNodeWithText("Secret dialog", useUnmergedTree = true).assertDoesNotExist()
        rule.runOnIdle { assertThat(dialogDraws).isEmpty() }
    }

    @Test
    fun returnWithoutRelock_keepsDialogOpenWithItsState() {
        show()
        rule.onNodeWithText("Open dialog").performClick()
        rule.onNodeWithText("Dialog taps 0").performClick()
        rule.onNodeWithText("Dialog taps 1").assertExists()

        backgroundAndReturn()

        rule.onNodeWithText("Dialog taps 1").assertExists()
        rule.onNodeWithText("Lock screen", useUnmergedTree = true).assertDoesNotExist()
        rule.runOnIdle {
            assertThat(dialogRoot?.visibility).isEqualTo(View.VISIBLE)
            assertThat(dialogRoot?.isShown == true).isTrue()
            assertThat(dialogDraws).isNotEmpty()
        }
    }

    /** A window outside the composition (a View dialog, like the consent form) outlives the lock. */
    @Test
    fun relock_keepsAViewDialogCoveredUntilTheContentShowsAgain() {
        gate.lockOnReturn = true
        show()
        lateinit var viewDialog: android.app.Dialog
        rule.runOnUiThread {
            viewDialog = android.app.Dialog(rule.activity).apply {
                setContentView(android.widget.TextView(context).apply { text = "View dialog" })
                show()
            }
        }
        rule.waitForIdle()
        val root = viewDialog.window!!.decorView

        backgroundAndReturn()

        rule.onNodeWithText("Lock screen").assertExists()
        rule.runOnIdle { assertThat(root.visibility).isEqualTo(View.INVISIBLE) }
        gate.state.value = ContentGateState.OPEN
        rule.waitForIdle()
        rule.runOnIdle {
            assertThat(root.visibility).isEqualTo(View.VISIBLE)
            viewDialog.dismiss()
        }
    }
}
