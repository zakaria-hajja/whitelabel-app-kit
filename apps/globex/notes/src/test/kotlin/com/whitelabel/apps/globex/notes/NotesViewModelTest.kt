package com.whitelabel.apps.globex.notes

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    private val store = FakeNotesStore()
    private lateinit var viewModel: NotesViewModel

    @BeforeEach fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = NotesViewModel(store)
    }

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test fun `adding a note stores the trimmed text and clears the draft`() = runTest {
        viewModel.state.test {
            skipItems(1)
            viewModel.onAction(NotesAction.OnDraftChange("  Buy milk  "))
            assertThat(awaitItem().draft).isEqualTo("  Buy milk  ")
            viewModel.onAction(NotesAction.OnAddClick)
            val state = expectMostRecentItem()
            assertThat(state.draft).isEqualTo("")
            assertThat(state.notes.map { it.text }).containsExactly("Buy milk")
        }
        assertThat(store.added).containsExactly("Buy milk")
    }

    @Test fun `blank or whitespace draft is not added`() = runTest {
        viewModel.state.test {
            skipItems(1)
            viewModel.onAction(NotesAction.OnAddClick)
            viewModel.onAction(NotesAction.OnDraftChange(" \n\t "))
            viewModel.onAction(NotesAction.OnAddClick)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(store.added).isEmpty()
    }

    @Test fun `delete removes only that note`() = runTest {
        viewModel.state.test {
            skipItems(1)
            viewModel.onAction(NotesAction.OnDraftChange("first"))
            viewModel.onAction(NotesAction.OnAddClick)
            viewModel.onAction(NotesAction.OnDraftChange("second"))
            viewModel.onAction(NotesAction.OnAddClick)
            val firstId = expectMostRecentItem().notes.single { it.text == "first" }.id
            viewModel.onAction(NotesAction.OnDeleteClick(firstId))
            assertThat(expectMostRecentItem().notes.map { it.text }).containsExactly("second")
        }
    }
}
