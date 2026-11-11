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
 * Every shared Koin module Showcase uses, plus a [platform] module (store paths, the token cipher,
 * the language, the HTTP engine, `NetworkConfig`, `BrandConfig`, `Analytics`).
 *
 * Kotlin code starts Koin with it (as ShowcaseGraphIosTest does). Swift can't build a Koin module or
 * get a ViewModel through Koin's reified functions: the iOS starter (step 5) adds the Swift-facing
 * entry point (taking the platform pieces as plain parameters) and typed ViewModel accessors on top.
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
