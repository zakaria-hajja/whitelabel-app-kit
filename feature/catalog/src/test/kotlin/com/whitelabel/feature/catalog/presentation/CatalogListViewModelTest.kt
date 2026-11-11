package com.whitelabel.feature.catalog.presentation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.AnalyticsEvents
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import com.whitelabel.core.network.Page
import com.whitelabel.feature.catalog.CatalogOptions
import com.whitelabel.feature.catalog.CatalogSort
import com.whitelabel.feature.catalog.domain.CatalogDataSource
import com.whitelabel.feature.catalog.domain.CatalogItem
import com.whitelabel.feature.catalog.domain.CatalogQuery
import com.whitelabel.feature.catalog.presentation.list.CatalogListAction
import com.whitelabel.feature.catalog.presentation.list.CatalogListViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

private fun item(n: Int) = CatalogItem("i$n", "Item $n", null, null, null)

private fun page(n: Int, size: Int, totalPages: Int, count: Int = size) =
    Page(items = List(count) { item((n - 1) * size + it) }, page = n, pageSize = size, totalItems = totalPages * size, totalPages = totalPages)

private class FakeCatalog : CatalogDataSource {
    val calls = mutableListOf<Pair<CatalogQuery, Int>>()
    var answer: suspend (CatalogQuery, Int, Int) -> Result<Page<CatalogItem>, DataError> = { _, p, size -> Result.Success(page(p, size, totalPages = 3)) }
    override suspend fun page(query: CatalogQuery, page: Int, pageSize: Int): Result<Page<CatalogItem>, DataError> {
        calls += query to page
        return answer(query, page, pageSize)
    }
    override suspend fun item(id: String) = Result.Success(item(0))
}

private class FakeAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    override fun track(event: AnalyticsEvent) { events += event }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogListViewModelTest {

    private val catalog = FakeCatalog()
    private val analytics = FakeAnalytics()

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(options: CatalogOptions = CatalogOptions(pageSize = 5)): CatalogListViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return CatalogListViewModel(options, catalog, analytics)
    }

    @Test fun `the first page loads at once with the default sort`() = runTest {
        val viewModel = viewModel(CatalogOptions(pageSize = 5, defaultSort = CatalogSort.TITLE_DESC))
        runCurrent()
        assertThat(catalog.calls.single().first.sort).isEqualTo(CatalogSort.TITLE_DESC)
        assertThat(viewModel.state.value.items.size).isEqualTo(5)
    }

    @Test fun `typing is debounced and older query results never replace newer ones`() = runTest {
        val slow = CompletableDeferred<Unit>()
        catalog.answer = { query, p, size ->
            if (query.text == "ja") slow.await()
            Result.Success(Page(listOf(CatalogItem(query.text, query.text, null, null, null)), p, size, 1, 1))
        }
        val viewModel = viewModel()
        runCurrent()
        viewModel.onAction(CatalogListAction.OnQueryChange("ja"))
        advanceTimeBy(301) // "ja" goes out and hangs
        viewModel.onAction(CatalogListAction.OnQueryChange("jazz"))
        advanceTimeBy(301)
        runCurrent()
        // The newer query shows while the older one still hangs.
        assertThat(viewModel.state.value.items.map { it.title }).containsExactly("jazz")
        slow.complete(Unit)
        advanceUntilIdle()
        assertThat(viewModel.state.value.items.map { it.title }).containsExactly("jazz")
        assertThat(catalog.calls.map { it.first.text }).containsExactly("", "ja", "jazz")
    }

    @Test fun `near end loads the next page once`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(CatalogListAction.OnNearEnd)
        viewModel.onAction(CatalogListAction.OnNearEnd)
        advanceUntilIdle()
        assertThat(catalog.calls.map { it.second }).containsExactly(1, 2)
        assertThat(viewModel.state.value.items.size).isEqualTo(10)
    }

    @Test fun `short page ends the list`() = runTest {
        catalog.answer = { _, p, size -> Result.Success(page(p, size, totalPages = 9, count = 2)) }
        val viewModel = viewModel()
        advanceUntilIdle()
        assertThat(viewModel.state.value.endReached).isTrue()
        viewModel.onAction(CatalogListAction.OnNearEnd)
        advanceUntilIdle()
        assertThat(catalog.calls.size).isEqualTo(1)
    }

    @Test fun `more error keeps items and retries on demand`() = runTest {
        catalog.answer = { _, p, size -> if (p == 2) Result.Error(DataError.Network.NO_INTERNET) else Result.Success(page(p, size, totalPages = 3)) }
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(CatalogListAction.OnNearEnd)
        advanceUntilIdle()
        assertThat(viewModel.state.value.items.size).isEqualTo(5)
        assertThat(viewModel.state.value.moreError).isNotNull()
        viewModel.onAction(CatalogListAction.OnNearEnd) // no automatic retry while the error shows
        advanceUntilIdle()
        assertThat(catalog.calls.size).isEqualTo(2)
        catalog.answer = { _, p, size -> Result.Success(page(p, size, totalPages = 3)) }
        viewModel.onAction(CatalogListAction.OnRetry)
        advanceUntilIdle()
        assertThat(viewModel.state.value.moreError).isNull()
        assertThat(viewModel.state.value.items.size).isEqualTo(10)
    }

    @Test fun `feature disabled shows unavailable without retry`() = runTest {
        catalog.answer = { _, _, _ -> Result.Error(DataError.Api(ApiErrorCode.FEATURE_DISABLED, 404)) }
        val viewModel = viewModel()
        advanceUntilIdle()
        assertThat(viewModel.state.value.unavailable).isTrue()
        assertThat(viewModel.state.value.firstPageError).isNull()
    }

    @Test fun `search event carries length not text`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        viewModel.onAction(CatalogListAction.OnQueryChange("j"))
        viewModel.onAction(CatalogListAction.OnQueryChange("jazz"))
        advanceTimeBy(301)
        advanceUntilIdle()
        assertThat(analytics.events).containsExactly(AnalyticsEvent(AnalyticsEvents.SEARCH, mapOf("length" to "4")))
    }

    @Test fun `a category or sort change reloads at once`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAction(CatalogListAction.OnCategorySelect("Music"))
        runCurrent()
        assertThat(catalog.calls.last().first.category).isEqualTo("Music")
        assertThat(viewModel.state.value.isLoadingFirst || viewModel.state.value.items.isNotEmpty()).isTrue()
        viewModel.onAction(CatalogListAction.OnSortSelect(CatalogSort.OLDEST))
        runCurrent()
        assertThat(catalog.calls.last().first.sort).isEqualTo(CatalogSort.OLDEST)
        assertThat(viewModel.state.value.endReached).isFalse()
    }
}
