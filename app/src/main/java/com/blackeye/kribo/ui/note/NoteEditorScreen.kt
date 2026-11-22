package com.blackeye.kribo.ui.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blackeye.kribo.R
import com.blackeye.kribo.data.NoteRepository
import com.blackeye.kribo.ui.components.AppScreen

@Composable
fun NoteEditorScreen(
    noteId: Long?,
    repository: NoteRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val editorViewModel: NoteEditorViewModel = viewModel(
        key = "note-editor-${noteId ?: "new"}",
        factory = viewModelFactory {
            initializer { NoteEditorViewModel(repository, noteId) }
        },
    )

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(editorViewModel.isLoading) {
        if (!editorViewModel.isLoading && noteId == null) {
            focusRequester.requestFocus()
        }
    }

    AppScreen(
        title = stringResource(
            if (noteId == null) R.string.note_title_new else R.string.note_title_edit,
        ),
        onBack = onBack,
        scrollable = false,
        footer = {
            Button(
                onClick = { editorViewModel.save(onSaved) },
                enabled = editorViewModel.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
    ) {
        if (editorViewModel.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            OutlinedTextField(
                value = editorViewModel.content,
                onValueChange = editorViewModel::onContentChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(stringResource(R.string.note_placeholder))
                },
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
