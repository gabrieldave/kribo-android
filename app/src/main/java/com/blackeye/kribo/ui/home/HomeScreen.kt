package com.blackeye.kribo.ui.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackeye.kribo.R
import com.blackeye.kribo.billing.BillingUiState
import com.blackeye.kribo.data.FolderEntity
import com.blackeye.kribo.data.NoteEntity
import com.blackeye.kribo.ui.common.displayNameFor
import com.blackeye.kribo.ui.common.findActivity
import com.blackeye.kribo.ui.components.AppModal
import com.blackeye.kribo.ui.components.AppScreen
import com.blackeye.kribo.ui.components.EmptyStateCard
import com.blackeye.kribo.ui.components.NoteCard
import com.blackeye.kribo.ui.components.StatusPill
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

private data class FolderDraft(val id: Long?, val name: String)

@Composable
fun HomeScreen(
    viewModel: LibraryViewModel,
    billingState: BillingUiState,
    onOpenNote: (Long) -> Unit,
    onSettings: () -> Unit,
    onPurchase: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val activeFolderId by viewModel.activeFolderId.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val paywallVisible by viewModel.paywallVisible.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val freeScansLeft by viewModel.freeScansLeft.collectAsStateWithLifecycle()

    var notePendingDelete by remember { mutableStateOf<NoteEntity?>(null) }
    var showFolderManager by remember { mutableStateOf(false) }
    var folderDraft by remember { mutableStateOf<FolderDraft?>(null) }
    var draftName by rememberSaveable { mutableStateOf("") }

    val folderNames = remember(folders) { folders.associate { it.id to it.name } }
    val largeFont = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f
    val actionHeight = if (largeFont) 80.dp else 56.dp

    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val scan = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { GmsDocumentScanningResult.fromActivityResultIntent(it) }
        } else {
            null
        }
        val uris = scan?.pages.orEmpty().map { it.imageUri }
        if (uris.isEmpty()) {
            viewModel.cancelScan()
        } else {
            viewModel.processScan(uris, context.contentResolver)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val name = context.displayNameFor(uri)
            val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            if (stream != null) viewModel.importFile(name, stream)
        }
    }

    val launchScanner: () -> Unit = {
        if (viewModel.canStartScan()) {
            val host = activity
            if (host == null) {
                viewModel.onScanLaunchFailed()
            } else {
                val options = GmsDocumentScannerOptions.Builder()
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setGalleryImportAllowed(true)
                    .build()
                GmsDocumentScanning.getClient(options)
                    .getStartScanIntent(host)
                    .addOnSuccessListener { sender ->
                        scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                    .addOnFailureListener { viewModel.onScanLaunchFailed() }
            }
        }
    }

    LaunchedEffect(scanState.phase, scanState.noteId) {
        if (scanState.phase == ScanPhase.COMPLETE) {
            val noteId = scanState.noteId
            if (noteId != null) {
                viewModel.acknowledgeScan()
                onOpenNote(noteId)
            }
        }
    }

    AppScreen(
        title = stringResource(R.string.app_name),
        scrollable = false,
        actions = {
            IconButton(
                onClick = {
                    importLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileOpen,
                    contentDescription = stringResource(R.string.cd_import),
                )
            }
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
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearQuery) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                }
            },
            singleLine = true,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FolderChip(
                label = stringResource(R.string.home_filter_all),
                color = MaterialTheme.colorScheme.primary,
                selected = activeFolderId == null,
                onClick = viewModel::clearFolderSelection,
            )
            folders.forEach { folder ->
                FolderChip(
                    label = folder.name,
                    color = folder.color(),
                    selected = activeFolderId == folder.id,
                    onClick = { viewModel.selectFolder(folder.id) },
                )
            }
            IconButton(
                onClick = {
                    draftName = ""
                    folderDraft = FolderDraft(id = null, name = "")
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CreateNewFolder,
                    contentDescription = stringResource(R.string.cd_new_folder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { showFolderManager = true },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.cd_manage_folders),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    viewModel.createNote { noteId -> onOpenNote(noteId) }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = actionHeight),
            ) {
                HomeActionContent(
                    icon = Icons.Rounded.Edit,
                    label = stringResource(R.string.home_action_write),
                    stacked = largeFont,
                )
            }
            FilledTonalButton(
                onClick = { launchScanner() },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = actionHeight),
            ) {
                HomeActionContent(
                    icon = Icons.Rounded.DocumentScanner,
                    label = stringResource(R.string.home_action_scan),
                    stacked = largeFont,
                )
            }
        }

        if (!billingState.owned) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_free_scans, freeScansLeft),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(18.dp))

        if (notes.isEmpty()) {
            EmptyStateCard(
                icon = Icons.AutoMirrored.Rounded.Notes,
                text = if (query.isBlank()) {
                    stringResource(R.string.home_recent_empty)
                } else {
                    stringResource(R.string.search_no_results, query)
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items = notes, key = { it.note.id }) { item ->
                    NoteCard(
                        note = item.note,
                        snippet = item.snippet,
                        folderName = item.note.folderId?.let(folderNames::get),
                        matchedOcr = item.matchedOcr,
                        onClick = { onOpenNote(item.note.id) },
                        onDelete = { notePendingDelete = item.note },
                        onTogglePin = { viewModel.togglePinned(item.note) },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
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

    if (showFolderManager) {
        AppModal(
            onDismissRequest = { showFolderManager = false },
            title = stringResource(R.string.folders_title),
            dismissLabel = stringResource(R.string.action_close),
        ) {
            folders.forEach { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(folder.color()),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = {
                            draftName = folder.name
                            folderDraft = FolderDraft(id = folder.id, name = folder.name)
                            showFolderManager = false
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.folder_rename),
                        )
                    }
                    if (folder.id != 1L) {
                        IconButton(
                            onClick = { viewModel.deleteFolder(folder.id) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.folder_delete),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    draftName = ""
                    folderDraft = FolderDraft(id = null, name = "")
                    showFolderManager = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CreateNewFolder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.folder_new))
            }
        }
    }

    folderDraft?.let { draft ->
        AppModal(
            onDismissRequest = { folderDraft = null },
            title = stringResource(
                if (draft.id == null) R.string.folder_new else R.string.folder_rename_title,
            ),
            dismissLabel = stringResource(R.string.action_cancel),
            confirmLabel = stringResource(R.string.action_accept),
            onConfirm = {
                if (draft.id == null) {
                    viewModel.createFolder(draftName)
                } else {
                    viewModel.renameFolder(draft.id, draftName)
                }
                folderDraft = null
            },
        ) {
            OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.folder_name_hint)) },
                singleLine = true,
            )
        }
    }

    if (scanState.phase == ScanPhase.SCANNING) {
        AppModal(
            onDismissRequest = {},
            title = stringResource(R.string.scan_progress_title),
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    R.string.scan_progress_page,
                    scanState.page.coerceAtLeast(1),
                    scanState.totalPages,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (scanState.phase == ScanPhase.ERROR) {
        AppModal(
            onDismissRequest = viewModel::acknowledgeScan,
            title = stringResource(R.string.scan_error_title),
            dismissLabel = stringResource(R.string.action_close),
        ) {
            Text(
                text = stringResource(R.string.scan_error_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (paywallVisible) {
        val price = viewModel.proPrice()
        AppModal(
            onDismissRequest = viewModel::dismissPaywall,
            title = stringResource(R.string.paywall_title),
            dismissLabel = stringResource(R.string.action_cancel),
            confirmLabel = price?.let { stringResource(R.string.settings_pro_price, it) },
            onConfirm = if (price != null) {
                {
                    viewModel.dismissPaywall()
                    onPurchase()
                }
            } else {
                null
            },
        ) {
            Text(
                text = stringResource(R.string.paywall_body, viewModel.freeScansLeft.value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (price == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_pro_unavailable),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    when (importState.phase) {
        TransferPhase.RUNNING -> AppModal(
            onDismissRequest = {},
            title = stringResource(R.string.import_running_title),
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.import_running_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TransferPhase.DONE -> AppModal(
            onDismissRequest = viewModel::acknowledgeImport,
            title = stringResource(R.string.import_done_title),
            dismissLabel = stringResource(R.string.action_close),
        ) {
            Text(
                text = stringResource(
                    R.string.import_summary,
                    importState.notes,
                    importState.attachments,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TransferPhase.ERROR -> AppModal(
            onDismissRequest = viewModel::acknowledgeImport,
            title = stringResource(R.string.import_error_title),
            dismissLabel = stringResource(R.string.action_close),
        ) {
            Text(
                text = stringResource(R.string.import_error_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TransferPhase.IDLE -> Unit
    }
}

@Composable
private fun HomeActionContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    stacked: Boolean,
) {
    if (stacked) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(text = label, maxLines = 1)
        }
    } else {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, maxLines = 1)
    }
}

@Composable
private fun FolderChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick),
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
            )
        }
    }
}

private fun FolderEntity.color(): Color = runCatching {
    Color(android.graphics.Color.parseColor(colorHex))
}.getOrDefault(Color(0xFF00D8FF))
