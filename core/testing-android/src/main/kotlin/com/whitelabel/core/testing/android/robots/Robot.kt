package com.whitelabel.core.testing.android.robots

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.whitelabel.core.testing.android.waitForNoText
import com.whitelabel.core.testing.android.waitForText

/** What every robot can do: find text, type into a labelled field, tap, switch tabs. */
abstract class Robot(val compose: ComposeTestRule) {

    fun sees(text: String) = compose.waitForText(text)

    fun doesNotSee(text: String) = compose.waitForNoText(text)

    fun openTab(label: String) {
        compose.waitUntil(TIMEOUT) { tab(label).isNotEmpty() }
        compose.onAllNodes(hasText(label) and isSelectable()).onFirst().performClick()
    }

    fun hasTab(label: String): Boolean = tab(label).isNotEmpty()

    protected fun type(field: String, text: String) {
        compose.waitForText(field)
        compose.onNodeWithText(field).performTextReplacement(text)
    }

    /** Taps the last clickable node with [text]: a dialog's confirm button sits above the screen's. */
    protected fun tap(text: String) {
        compose.waitUntil(TIMEOUT) { clickable(text).isNotEmpty() }
        compose.onAllNodes(hasText(text) and hasClickAction()).onLast().performClick()
    }

    protected fun scrollTo(text: String) {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
    }

    private fun tab(label: String) = compose.onAllNodes(hasText(label) and isSelectable()).fetchSemanticsNodes()

    private fun clickable(text: String) = compose.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes()

    protected fun count(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes().size

    protected fun texts(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes()

    companion object {
        const val TIMEOUT = 5_000L
    }
}
