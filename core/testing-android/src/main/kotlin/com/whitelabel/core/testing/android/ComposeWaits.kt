package com.whitelabel.core.testing.android

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText

/** Waits until a node with [text] is shown; fails after [timeoutMs]. */
fun ComposeTestRule.waitForText(text: String, timeoutMs: Long = 5_000) =
    waitUntil(timeoutMs) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

/** Waits until no node with [text] is shown; fails after [timeoutMs]. */
fun ComposeTestRule.waitForNoText(text: String, timeoutMs: Long = 5_000) =
    waitUntil(timeoutMs) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
