package com.whitelabel.core.data.config

import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import com.whitelabel.core.common.asEmptyResult
import com.whitelabel.core.common.onSuccess
import com.whitelabel.core.data.di.ApplicationScope
import com.whitelabel.core.domain.AppConfig
import com.whitelabel.core.domain.AppConfigRepository
import com.whitelabel.core.network.getResult
import io.ktor.client.HttpClient
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class DefaultAppConfigRepository @Inject constructor(
    private val client: HttpClient,
    brand: BrandConfig,
    private val cache: AppConfigCache,
    @ApplicationScope scope: CoroutineScope,
) : AppConfigRepository {

    private val defaults = brand.runtimeDefaults

    private val resolved = cache.config.map { cached -> cached?.toAppConfig(defaults) ?: defaults.toAppConfig() }

    override val config: StateFlow<AppConfig> = resolved.stateIn(scope, SharingStarted.Eagerly, defaults.toAppConfig())

    /** Reads storage directly: [config] may not have emitted the cache, or a just-written fetch, yet. */
    override suspend fun current(): AppConfig = resolved.first()

    override suspend fun refresh(): EmptyResult<DataError> =
        client.getResult<AppConfigDto>("app/config").onSuccess { cache.write(it) }.asEmptyResult()
}
