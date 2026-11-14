package com.whitelabel.core.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.whitelabel.core.shell.model.ShellScreen

/** On the root of the gated shell only: an app without a content gate never composes it. */
internal const val CONTENT_GATE_ROOT_TAG = "ShellContentGateRoot"
internal const val LOCK_SCREEN_TAG = "ShellLockScreen"
internal const val SHELL_STATE_KEY = "shell"

/**
 * The root of an app with a content gate: nothing while [screen] is SPLASH (the system splash
 * covers it), [lockScreen] **instead of** [content] while LOCK, and [content] while CONTENT.
 *
 * The lock replaces the content in composition, so nothing of it (screens, dialogs, banners,
 * deep-link handling) exists while locked. The content's `rememberSaveable` state is kept in a
 * [rememberSaveableStateHolder] across a relock and process death.
 *
 * [onContentShown] runs on **every** entry into CONTENT; the caller dedupes.
 */
@Composable
internal fun ShellRoot(
    screen: ShellScreen,
    lockScreen: @Composable () -> Unit,
    onContentShown: () -> Unit,
    content: @Composable () -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val currentOnContentShown by rememberUpdatedState(onContentShown)
    Box(Modifier.fillMaxSize().testTag(CONTENT_GATE_ROOT_TAG)) {
        when (screen) {
            ShellScreen.SPLASH -> Unit
            ShellScreen.LOCK -> Box(Modifier.fillMaxSize().testTag(LOCK_SCREEN_TAG)) { lockScreen() }
            ShellScreen.CONTENT -> {
                holder.SaveableStateProvider(SHELL_STATE_KEY) { content() }
                LaunchedEffect(Unit) { currentOnContentShown() }
            }
        }
    }
}
