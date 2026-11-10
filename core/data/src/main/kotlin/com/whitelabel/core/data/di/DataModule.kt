package com.whitelabel.core.data.di

import android.content.Context
import com.whitelabel.core.data.config.AppConfigCache
import com.whitelabel.core.data.config.DefaultAppConfigRepository
import com.whitelabel.core.data.preferences.DefaultUserPreferencesRepository
import com.whitelabel.core.data.session.DefaultSessionRepository
import com.whitelabel.core.data.session.StoreSessionTokens
import com.whitelabel.core.data.user.DefaultUserRepository
import com.whitelabel.core.data.user.UserCache
import com.whitelabel.core.datastore.KeystoreTokenCipher
import com.whitelabel.core.datastore.TokenStore
import com.whitelabel.core.datastore.jsonStoreFile
import com.whitelabel.core.domain.AppConfigRepository
import com.whitelabel.core.domain.SessionRepository
import com.whitelabel.core.domain.UserPreferencesRepository
import com.whitelabel.core.domain.UserRepository
import com.whitelabel.core.network.SessionTokenStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds @Singleton abstract fun sessionRepository(impl: DefaultSessionRepository): SessionRepository

    @Binds @Singleton abstract fun userRepository(impl: DefaultUserRepository): UserRepository

    @Binds @Singleton abstract fun sessionTokens(impl: StoreSessionTokens): SessionTokenStore

    @Binds @Singleton abstract fun appConfigRepository(impl: DefaultAppConfigRepository): AppConfigRepository

    companion object {
        @Provides @Singleton @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides @Singleton
        fun tokenStore(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): TokenStore =
            TokenStore(context.jsonStoreFile("session"), KeystoreTokenCipher(), scope)

        @Provides @Singleton
        fun userCache(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): UserCache =
            UserCache(context.jsonStoreFile("user"), scope)

        @Provides @Singleton
        fun appConfigCache(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): AppConfigCache =
            AppConfigCache(context.jsonStoreFile("app_config"), scope)

        @Provides @Singleton
        fun preferences(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): UserPreferencesRepository =
            DefaultUserPreferencesRepository(context.jsonStoreFile("preferences"), scope)
    }
}
