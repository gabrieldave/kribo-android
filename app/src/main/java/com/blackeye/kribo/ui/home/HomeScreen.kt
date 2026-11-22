package com.blackeye.kribo.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackeye.kribo.R
import com.blackeye.kribo.data.Note
import com.blackeye.kribo.ui.components.ActionCard
import com.blackeye.kribo.ui.components.AppModal
import com.blackeye.kribo.ui.components.AppScreen
import com.blackeye.kribo.ui.components.EmptyStateCard
import com.blackeye.kribo.ui.components.NoteCard
import com.blackeye.kribo.ui.components.SectionTitle

@Composable
fun HomeScreen(
    viewModel: NotesViewModel,
    onQuickNote: () -> Unit,
    onScan: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var notePendingDelete by remember { mutableStateOf<Note?>(null) }

    AppScreen(
        title = stringResource(R.string.app_name),
        actions = {
            IconButton(
                onClick = onSettings,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                )
            }
        },
    ) {
        Text(
            text = stringResource(R.string.brand_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard(
                title = stringResource(R.string.home_quick_note_title),
                subtitle = stringResource(R.string.home_quick_note_subtitle),
                icon = Icons.Rounded.Edit,
                onClick = onQuickNote,
            )
            ActionCard(
                title = stringResource(R.string.home_scan_title),
                subtitle = stringResource(R.string.home_scan_subtitle),
                icon = Icons.Rounded.DocumentScanner,
                onClick = onScan,
            )
            ActionCard(
                title = stringResource(R.string.home_search_title),
                subtitle = stringResource(R.string.home_search_subtitle),
                icon = Icons.Rounded.Search,
                onClick = onSearch,
            )
        }

        Spacer(Modifier.height(28.dp))

        SectionTitle(stringResource(R.string.home_recent_title))
        Spacer(Modifier.height(12.dp))

        if (notes.isEmpty()) {
            EmptyStateCard(
                icon = Icons.AutoMirrored.Rounded.Notes,
                text = stringResource(R.string.home_recent_empty),
            )
        } else {
            notes.take(6).forEach { note ->
                NoteCard(
                    note = note,
                    onClick = { onOpenNote(note.id) },
                    onDelete = { notePendingDelete = note },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    notePendingDelete?.let { note ->
        AppModal(
            onDismissRequest = { notePendingDelete = null },
            title = stringResource(R.string.home_delete_title),
            dismissLabel = stringResource(R.string.action_cancel),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                viewModel.deleteNote(note.id)
                notePendingDelete = null
            },
        ) {
            Text(
                text = stringResource(R.string.home_delete_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
