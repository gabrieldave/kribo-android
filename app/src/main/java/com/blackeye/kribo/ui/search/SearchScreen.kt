package com.blackeye.kribo.ui.search

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackeye.kribo.R
import com.blackeye.kribo.ui.components.AppScreen
import com.blackeye.kribo.ui.components.EmptyStateCard
import com.blackeye.kribo.ui.components.NoteCard
import com.blackeye.kribo.ui.home.NotesViewModel

@Composable
fun SearchScreen(
    viewModel: NotesViewModel,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val trimmedQuery = query.trim()

    val results = remember(notes, trimmedQuery) {
        if (trimmedQuery.isEmpty()) {
            notes
        } else {
            notes.filter { it.content.contains(trimmedQuery, ignoreCase = true) }
        }
    }

    AppScreen(
        title = stringResource(R.string.search_title),
        onBack = onBack,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                }
            },
            singleLine = true,
        )

        Spacer(Modifier.height(16.dp))

        when {
            notes.isEmpty() -> EmptyStateCard(
                icon = Icons.AutoMirrored.Rounded.Notes,
                text = stringResource(R.string.search_no_notes),
            )

            results.isEmpty() -> EmptyStateCard(
                icon = Icons.Rounded.Search,
                text = stringResource(R.string.search_no_results, trimmedQuery),
            )

            else -> results.forEach { note ->
                NoteCard(
                    note = note,
                    onClick = { onOpenNote(note.id) },
                    onDelete = null,
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
