package com.whitelabel.apps.showcase.e2e.robots

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.whitelabel.core.testing.android.waitForText

class CatalogRobot(compose: ComposeTestRule) : Robot(compose) {

    fun search(text: String) {
        type("Search", text)
    }

    fun filter(category: String) {
        compose.onNode(hasText(category) and isSelectable()).performClick()
    }

    fun sortBy(option: String) {
        tap("Sort")
        tap(option)
    }

    /** [titles] are shown, top to bottom in this order. */
    fun showsInOrder(vararg titles: String) {
        titles.forEach { compose.waitForText(it) }
        val tops = titles.map { compose.onAllNodesWithText(it).onFirst().fetchSemanticsNode().boundsInRoot.top }
        check(tops == tops.sorted()) { "Expected ${titles.toList()} top to bottom, tops were $tops" }
    }

    fun open(title: String) {
        compose.waitForText(title)
        compose.onAllNodesWithText(title).onFirst().performClick()
    }

    fun showsDetail(description: String) = sees(description)

}
