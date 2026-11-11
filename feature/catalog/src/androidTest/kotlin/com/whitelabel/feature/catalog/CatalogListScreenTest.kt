package com.whitelabel.feature.catalog

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import assertk.assertThat
import assertk.assertions.contains
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.feature.catalog.domain.CatalogItem
import com.whitelabel.feature.catalog.presentation.list.CatalogListAction
import com.whitelabel.feature.catalog.presentation.list.CatalogListScreen
import com.whitelabel.feature.catalog.presentation.list.CatalogListState
import org.junit.Rule
import org.junit.Test

class CatalogListScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<CatalogListAction>()

    private fun show(state: CatalogListState, options: CatalogOptions = CatalogOptions(categories = listOf("Music", "Books"))) {
        rule.setContent {
            AppTheme { CatalogListScreen(state = state, options = options, onAction = { actions += it }, onItemClick = {}) }
        }
    }

    @Test
    fun typingQuery_sendsTheQuery() {
        show(CatalogListState(items = listOf(CatalogItem("i1", "Item 1", null, "Music", null)), page = 1))
        rule.onNodeWithText("Item 1").assertExists()
        rule.onNodeWithText("Search").performTextInput("jazz")
        rule.runOnIdle { assertThat(actions).contains(CatalogListAction.OnQueryChange("jazz")) }
    }

    @Test
    fun emptyResult_showsEmptyState() {
        show(CatalogListState(page = 1, endReached = true))
        rule.onNodeWithText("No results").assertExists()
    }

    @Test
    fun footerError_retryLoadsMore() {
        show(CatalogListState(items = List(3) { CatalogItem("i$it", "Item $it", null, null, null) }, page = 1, moreError = com.whitelabel.core.ui.UiText.Dynamic("Offline")))
        rule.onNodeWithText("Retry").performScrollTo().performClick()
        rule.runOnIdle { assertThat(actions).contains(CatalogListAction.OnRetry) }
    }

    @Test
    fun categoryChip_selectsTheCategory() {
        show(CatalogListState(page = 1, endReached = true))
        rule.onNodeWithText("Music").performClick()
        rule.runOnIdle { assertThat(actions).contains(CatalogListAction.OnCategorySelect("Music")) }
    }
}
