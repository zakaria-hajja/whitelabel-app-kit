package com.whitelabel.feature.home

import com.whitelabel.feature.home.presentation.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The home tab: added by `home()`. */
val homeModule = module {
    viewModelOf(::HomeViewModel)
}
