package com.whitelabel.app.brand

import android.os.Build
import android.util.Log
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.network.NetworkConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BrandModule {
    @Provides
    @Singleton
    fun brandConfig(): BrandConfig = buildConfigBrand()

    @Provides
    @Singleton
    fun networkConfig(brand: BrandConfig): NetworkConfig = NetworkConfig(
        baseUrl = brand.apiBaseUrl,
        tenant = brand.tenant,
        userAgent = "${brand.applicationId}/${brand.versionName} (Android ${Build.VERSION.RELEASE})",
        logger = if (brand.environment == Environment.PRODUCTION) null else { line -> Log.d("Http", line) },
    )

    @Provides
    @Singleton
    fun httpClientEngine(): HttpClientEngine = OkHttp.create()
}
