package com.whitelabel.feature.catalog.presentation

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.AnalyticsEvents
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import com.whitelabel.core.network.Page
import com.whitelabel.feature.catalog.domain.CatalogDataSource
import com.whitelabel.feature.catalog.domain.CatalogItem
import com.whitelabel.feature.catalog.domain.CatalogQuery
import com.whitelabel.feature.catalog.presentation.detail.CatalogDetailAction
import com.whitelabel.feature.catalog.presentation.detail.CatalogDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private val ITEM = CatalogItem("i7", "Item 7", "About item 7", "Music", null)

private class FakeItems : CatalogDataSource {
    var answer: Result<CatalogItem, DataError> = Result.Success(ITEM)
    val requested = mutableListOf<String>()
    override suspend fun page(query: CatalogQuery, page: Int, pageSize: Int): Result<Page<CatalogItem>, DataError> = error("unused")
    override suspend fun item(id: String): Result<CatalogItem, DataError> { requested += id; return answer }
}

private class DetailAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    override fun track(event: AnalyticsEvent) { events += event }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogDetailViewModelTest {

    private val items = FakeItems()
    private val analytics = DetailAnalytics()

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = CatalogDetailViewModel(items, analytics, SavedStateHandle(mapOf("id" to "i7")))

    @Test fun `loads the item named by the route`() = runTest {
        val state = viewModel().state.value
        assertThat(items.requested).containsExactly("i7")
        assertThat(state.item).isEqualTo(ITEM)
    }

    @Test fun `not found shows the missing item message`() = runTest {
        items.answer = Result.Error(DataError.Api(ApiErrorCode.NOT_FOUND, 404))
        val state = viewModel().state.value
        assertThat(state.notFound).isTrue()
        assertThat(state.error).isNull()
    }

    @Test fun `view item event fires once`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(CatalogDetailAction.OnRetry)
        assertThat(analytics.events).containsExactly(AnalyticsEvent(AnalyticsEvents.VIEW_ITEM, mapOf("item_id" to "i7")))
    }
}
