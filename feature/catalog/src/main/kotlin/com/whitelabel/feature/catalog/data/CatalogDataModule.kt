package com.whitelabel.feature.catalog.data

import com.whitelabel.feature.catalog.domain.CatalogDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CatalogDataModule {
    @Binds abstract fun catalog(impl: KtorCatalogDataSource): CatalogDataSource
}
