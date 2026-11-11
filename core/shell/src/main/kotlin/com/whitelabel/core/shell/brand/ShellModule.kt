package com.whitelabel.core.shell.brand

import android.content.Context
import android.os.Build
import android.util.Log
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.analytics.LoggingAnalytics
import com.whitelabel.core.analytics.LoggingCrashReporter
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.shell.AppDefinition
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.network.NetworkConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

/** Base URL for apps without a backend: `.invalid` never resolves (RFC 2606), and nothing calls it. */
internal const val NO_BACKEND_BASE_URL = "https://no-backend.invalid/"

@Module
@InstallIn(SingletonComponent::class)
internal object ShellModule {
    @Provides
    @Singleton
    fun brandConfig(@ApplicationContext context: Context): BrandConfig = brandConfigFromResources(context)

    @Provides
    @Singleton
    fun networkConfig(brand: BrandConfig): NetworkConfig = NetworkConfig(
        baseUrl = brand.apiBaseUrl ?: NO_BACKEND_BASE_URL,
        tenant = brand.tenant,
        userAgent = "${brand.applicationId}/${brand.versionName} (Android ${Build.VERSION.RELEASE})",
        logger = if (brand.environment == Environment.PRODUCTION) null else { line -> Log.d("Http", line) },
    )

    @Provides
    @Singleton
    fun httpClientEngine(): HttpClientEngine = OkHttp.create()

    /** The app's analytics from its definition, else logcat. */
    @Provides
    @Singleton
    fun analytics(definition: AppDefinition): Analytics =
        definition.analytics ?: LoggingAnalytics { line -> Log.d("Analytics", line) }

    /** The app's crash reporter from its definition, else logcat. */
    @Provides
    @Singleton
    fun crashReporter(definition: AppDefinition): CrashReporter =
        definition.crashReporter ?: LoggingCrashReporter { message, throwable -> Log.e("Crash", message, throwable) }
}
