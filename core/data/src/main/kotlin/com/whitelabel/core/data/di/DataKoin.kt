package com.whitelabel.core.data.di

import com.whitelabel.core.data.config.AppConfigCache
import com.whitelabel.core.data.config.DefaultAppConfigRepository
import com.whitelabel.core.data.language.AppCompatLanguageRepository
import com.whitelabel.core.data.preferences.DefaultUserPreferencesRepository
import com.whitelabel.core.data.session.DefaultSessionRepository
import com.whitelabel.core.data.session.LocalSession
import com.whitelabel.core.data.session.StoreSessionTokens
import com.whitelabel.core.data.user.DefaultUserRepository
import com.whitelabel.core.data.user.UserCache
import com.whitelabel.core.datastore.KeystoreTokenCipher
import com.whitelabel.core.datastore.StorePaths
import com.whitelabel.core.datastore.TokenCipher
import com.whitelabel.core.datastore.TokenStore
import com.whitelabel.core.datastore.jsonStoreFile
import com.whitelabel.core.domain.AppConfigRepository
import com.whitelabel.core.domain.LanguageRepository
import com.whitelabel.core.domain.SessionRepository
import com.whitelabel.core.domain.UserPreferencesRepository
import com.whitelabel.core.domain.UserRepository
import com.whitelabel.core.network.SessionTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

/** The scope that lives as long as the app's graph, for work that must outlive a screen. Cancelled when Koin stops. */
val ApplicationScope: Qualifier = named("applicationScope")

/**
 * The repositories and their stores. Needs a platform module for [StorePaths], [TokenCipher] and
 * [LanguageRepository] ([androidDataModule] on Android), plus `HttpClient` and `BrandConfig`.
 */
val dataModule = module {
    single<CoroutineScope>(ApplicationScope) { CoroutineScope(SupervisorJob() + Dispatchers.Default) } onClose { it?.cancel() }
    single { TokenStore(get<StorePaths>().file("session"), get(), get(ApplicationScope)) }
    single { UserCache(get<StorePaths>().file("user"), get(ApplicationScope)) }
    single { AppConfigCache(get<StorePaths>().file("app_config"), get(ApplicationScope)) }
    single<UserPreferencesRepository> { DefaultUserPreferencesRepository(get<StorePaths>().file("preferences"), get(ApplicationScope)) }
    single { LocalSession(get(), get()) }
    single<SessionTokenStore> { StoreSessionTokens(get()) }
    single<SessionRepository> { DefaultSessionRepository(get(), get(), get(ApplicationScope)) }
    single<UserRepository> { DefaultUserRepository(get(), get()) }
    single<AppConfigRepository> { DefaultAppConfigRepository(get(), get(), get(), get(ApplicationScope)) }
}

/** Android's side of [dataModule]: files under `filesDir/datastore`, the Keystore cipher, the app language. */
val androidDataModule = module {
    single { StorePaths { name -> androidContext().jsonStoreFile(name) } }
    single<TokenCipher> { KeystoreTokenCipher() }
    single<LanguageRepository> { AppCompatLanguageRepository(androidContext()) }
}
