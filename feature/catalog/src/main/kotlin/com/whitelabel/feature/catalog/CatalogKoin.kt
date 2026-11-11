package com.whitelabel.feature.catalog

import com.whitelabel.feature.catalog.data.KtorCatalogDataSource
import com.whitelabel.feature.catalog.domain.CatalogDataSource
import com.whitelabel.feature.catalog.presentation.detail.CatalogDetailViewModel
import com.whitelabel.feature.catalog.presentation.list.CatalogListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The catalog list and item screens: added by `catalog()`. The list gets the app's options as a parameter. */
val catalogModule = module {
    single<CatalogDataSource> { KtorCatalogDataSource(get()) }
    viewModel { (options: CatalogOptions) -> CatalogListViewModel(options, get(), get()) }
    viewModelOf(::CatalogDetailViewModel)
}
