package com.whitelabel.feature.catalog

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.common.Result
import com.whitelabel.core.network.Page
import com.whitelabel.core.testing.ContractBackend
import com.whitelabel.core.testing.OpenApiSpec
import com.whitelabel.core.testing.exampleValue
import com.whitelabel.feature.catalog.data.KtorCatalogDataSource
import com.whitelabel.feature.catalog.domain.CatalogItem
import com.whitelabel.feature.catalog.domain.CatalogQuery
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ContractTest {
    private val backend = ContractBackend()
    private val source = KtorCatalogDataSource(backend.client())

    @Test fun `get api v1 catalog matches the contract`() = runTest {
        val result = source.page(CatalogQuery(text = "item", category = "Music", sort = CatalogSort.TITLE_ASC), page = 1, pageSize = 20)
        val page = (result as Result.Success<Page<CatalogItem>>).data
        assertThat(page.items.first().title).isEqualTo(OpenApiSpec.exampleValue("/api/v1/catalog", "get", 200, "data.0.title"))
        assertThat(page.totalPages.toString()).isEqualTo(OpenApiSpec.exampleValue("/api/v1/catalog", "get", 200, "meta.pagination.totalPages"))
    }

    @Test fun `get api v1 catalog id matches the contract`() = runTest {
        val item = (source.item("any-id") as Result.Success<CatalogItem>).data
        assertThat(item.title).isEqualTo(OpenApiSpec.exampleValue("/api/v1/catalog/{id}", "get", 200, "data.title"))
    }

    @Test fun `the smallest valid catalog responses still decode`() = runTest {
        val minimal = KtorCatalogDataSource(ContractBackend(minimal = true).client())
        val item = (minimal.item("any-id") as Result.Success<CatalogItem>).data
        assertThat(item.description).isEqualTo(null)
        assertThat(item.category).isEqualTo(null)
        val page = minimal.page(CatalogQuery(sort = CatalogSort.NEWEST), page = 1, pageSize = 20)
        assertThat(page is Result.Success).isEqualTo(true)
    }
}
