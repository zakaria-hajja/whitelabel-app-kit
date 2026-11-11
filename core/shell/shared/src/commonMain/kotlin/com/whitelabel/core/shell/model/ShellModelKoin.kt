package com.whitelabel.core.shell.model

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The shell's ViewModel on both platforms, for the app's [definition] (Android: `toSpec()`; iOS: the Swift definition's spec). */
fun shellModelModule(definition: DefinitionSpec) = module {
    viewModel { ShellViewModel(definition, get(), get(), get(), get(), get(), get()) }
}
