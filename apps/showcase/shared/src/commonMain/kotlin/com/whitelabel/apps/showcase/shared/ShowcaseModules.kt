package com.whitelabel.apps.showcase.shared

import com.whitelabel.core.data.di.dataModule
import com.whitelabel.core.network.di.networkModule
import com.whitelabel.feature.authentication.authenticationModule
import com.whitelabel.feature.catalog.catalogModule
import com.whitelabel.feature.home.homeModule
import com.whitelabel.feature.profile.profileModule
import com.whitelabel.feature.settings.settingsModule
import org.koin.core.module.Module

/**
 * Every shared Koin module Showcase uses, plus the iOS app's [platform] module (store paths, the
 * token cipher, the language, the HTTP engine, `NetworkConfig`, `BrandConfig`, `Analytics`). The
 * iOS app hands this list to `startKoin`.
 */
fun showcaseSharedModules(platform: Module): List<Module> = listOf(
    networkModule,
    dataModule,
    authenticationModule,
    settingsModule,
    profileModule,
    homeModule,
    catalogModule,
    platform,
)
