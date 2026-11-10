package com.whitelabel.apps.globex.notes

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.whitelabel.core.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test

class NotesScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = rule.activity.getString(id)

    @Test
    fun addNote_showsItInList_andDeleteRemovesIt() {
        val viewModel = NotesViewModel(FakeNotesStore())
        rule.setContent { AppTheme { NotesRoot(viewModel = viewModel) } }

        rule.onNodeWithText(string(R.string.notes_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.notes_draft_label)).performTextInput("Call the bank")
        rule.onNodeWithText(string(R.string.notes_add)).performClick()
        rule.onNodeWithText("Call the bank").assertIsDisplayed()

        rule.onNodeWithContentDescription(string(R.string.notes_delete)).performClick()
        rule.onNodeWithText("Call the bank").assertDoesNotExist()
    }
}
