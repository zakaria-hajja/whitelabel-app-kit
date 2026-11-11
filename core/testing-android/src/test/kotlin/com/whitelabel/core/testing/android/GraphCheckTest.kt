package com.whitelabel.core.testing.android

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasMessage
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import org.junit.jupiter.api.Test
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class GraphCheckTest {

    class Repository
    class Options(val name: String)
    class PlainViewModel(val repository: Repository) : ViewModel()
    class SavedViewModel(val repository: Repository, val savedState: SavedStateHandle) : ViewModel()
    class OptionsViewModel(val options: Options, val repository: Repository) : ViewModel()

    @Test fun `every viewmodel in a complete graph resolves`() {
        val koin = koinApplication {
            modules(module { single { Repository() }; viewModelOf(::PlainViewModel); viewModelOf(::SavedViewModel) })
        }.koin
        assertThat(koin.assertEveryViewModelResolves()).containsExactlyInAnyOrder(PlainViewModel::class, SavedViewModel::class)
    }

    @Test fun `a viewmodel with a missing binding fails, naming it`() {
        val koin = koinApplication { modules(module { viewModelOf(::PlainViewModel) }) }.koin
        assertFailure { koin.assertEveryViewModelResolves() }
            .isInstanceOf(AssertionError::class)
            .messageContains("PlainViewModel")
    }

    @Test fun `a viewmodel that takes parameters gets them from the caller`() {
        val koin = koinApplication {
            modules(module { single { Repository() }; viewModel { (options: Options) -> OptionsViewModel(options, get()) } })
        }.koin
        val resolved = koin.assertEveryViewModelResolves { type ->
            if (type == OptionsViewModel::class) parametersOf(Options("test")) else null
        }
        assertThat(resolved).containsExactlyInAnyOrder(OptionsViewModel::class)
    }

    @Test fun `a graph without viewmodels fails, since checking nothing proves nothing`() {
        val koin = koinApplication { modules(module { single { Repository() } }) }.koin
        assertFailure { koin.assertEveryViewModelResolves() }.hasMessage("No ViewModel definitions in the graph")
    }
}
