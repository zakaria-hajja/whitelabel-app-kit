package com.whitelabel.core.data.di

import com.whitelabel.core.data.language.AppCompatLanguageRepository
import com.whitelabel.core.datastore.KeystoreTokenCipher
import com.whitelabel.core.datastore.StorePaths
import com.whitelabel.core.datastore.TokenCipher
import com.whitelabel.core.datastore.jsonStorePath
import com.whitelabel.core.domain.LanguageRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Android's side of [dataModule]: files under `filesDir/datastore`, the Keystore cipher, the app language. */
val androidDataModule = module {
    single { StorePaths { name -> androidContext().jsonStorePath(name) } }
    single<TokenCipher> { KeystoreTokenCipher() }
    single<LanguageRepository> { AppCompatLanguageRepository(androidContext()) }
}
