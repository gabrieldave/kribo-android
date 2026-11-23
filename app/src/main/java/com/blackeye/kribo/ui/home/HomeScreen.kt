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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
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
import com.blackeye.kribo.ui.components.BentoNoteCard
import com.blackeye.kribo.ui.billing.ProPaywallSheet
import com.blackeye.kribo.ui.components.CompactNoteRow
import com.blackeye.kribo.ui.components.EmptyStateCard
import com.blackeye.kribo.ui.preferences.HomeLayout
import com.blackeye.kribo.ui.preferences.UiPreferences
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

private data class FolderDraft(val id: Long?, val name: String)

@Composable
fun HomeScreen(
    viewModel: LibraryViewModel,
    uiPreferences: UiPreferences,
    billingState: BillingUiState,
    onOpenNote: (Long, String) -> Unit,
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
    val paywallReason by viewModel.paywallReason.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val freeScansLeft by viewModel.freeScansLeft.collectAsStateWithLifecycle()
    val homeLayout by uiPreferences.homeLayout.collectAsStateWithLifecycle()

    var notePendingDelete by remember { mutableStateOf<NoteEntity?>(null) }
    var showFolderManager by remember { mutableStateOf(false) }
    var folderDraft by remember { mutableStateOf<FolderDraft?>(null) }
    var draftName by rememberSaveable { mutableStateOf("") }
    var captureText by rememberSaveable { mutableStateOf("") }
    var showBulkDelete by remember { mutableStateOf(false) }
    var showMovePicker by remember { mutableStateOf(false) }
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val selectedNotes = remember(notes, selectedIds) { notes.filter { it.note.id in selectedIds } }
    val allSelectedPinned = selectedNotes.isNotEmpty() && selectedNotes.all { it.note.isPinned }

    val folderNames = remember(folders) { folders.associate { it.id to it.name } }
    val folderColors = remember(folders) { folders.associate { it.id to it.color() } }
    val largeFont = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f

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
                onOpenNote(noteId, "")
            }
        }
    }

    AppScreen(
        title = if (selectionMode) {
            stringResource(R.string.home_selection_count, selectedIds.size)
        } else {
            stringResource(R.string.app_name)
        },
        scrollable = false,
        actions = {
            if (selectionMode) {
                IconButton(
                    onClick = { viewModel.selectAll(notes.map { it.note.id }) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("home-select-all"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SelectAll,
                        contentDescription = stringResource(R.string.home_select_all),
                    )
                }
                IconButton(
                    onClick = viewModel::clearSelection,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("home-selection-close"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.home_selection_close),
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        uiPreferences.setHomeLayout(
                            if (homeLayout == HomeLayout.COMPACT) HomeLayout.BENTO else HomeLayout.COMPACT,
                        )
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = if (homeLayout == HomeLayout.COMPACT) {
                            Icons.Rounded.GridView
                        } else {
                            Icons.Rounded.ViewList
                        },
                        contentDescription = stringResource(
                            if (homeLayout == HomeLayout.COMPACT) R.string.cd_toggle_bento
                            else R.string.cd_toggle_list,
                        ),
                    )
                }
                IconButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
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
            }
        },
        footer = {
            if (selectionMode) {
                BulkSelectionBar(
                    anyPinned = allSelectedPinned,
                    onMove = { showMovePicker = true },
                    onPin = {
                        viewModel.setPinnedFor(selectedIds, !allSelectedPinned)
                    },
                    onDelete = { showBulkDelete = true },
                )
            } else {
                QuickCaptureBar(
                    text = captureText,
                    onTextChange = { value ->
                        captureText = value.replace(Regex("\\n{3,}"), "\n\n")
                    },
                    onSend = {
                        if (captureText.isNotBlank()) {
                            viewModel.quickCapture(captureText) { captureText = "" }
                        }
                    },
                    onOpenEditor = {
                        viewModel.createNote { noteId -> onOpenNote(noteId, "") }
                    },
                    onScan = { launchScanner() },
                    freeScansLeft = if (viewModel.isPro()) null else freeScansLeft,
                    largeFont = largeFont,
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

        Spacer(Modifier.height(10.dp))

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

        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (notes.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Rounded.Notes,
                    text = if (query.isBlank()) {
                        stringResource(R.string.home_recent_empty)
                    } else {
                        stringResource(R.string.search_no_results, query)
                    },
                )
            } else if (homeLayout == HomeLayout.COMPACT) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(items = notes, key = { _, item -> item.note.id }) { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                            )
                        }
                        CompactNoteRow(
                            item = item,
                            query = query,
                            folderName = item.note.folderId?.let(folderNames::get),
                            folderColor = item.note.folderId?.let(folderColors::get)
                                ?: MaterialTheme.colorScheme.outline,
                            selectionMode = selectionMode,
                            selected = item.note.id in selectedIds,
                            onClick = {
                                if (selectionMode) {
                                    viewModel.toggleSelection(item.note.id)
                                } else {
                                    onOpenNote(item.note.id, query)
                                }
                            },
                            onLongClick = { viewModel.startSelection(item.note.id) },
                            onDelete = { notePendingDelete = item.note },
                            onTogglePin = { viewModel.togglePinned(item.note) },
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth(),
                    verticalItemSpacing = 10.dp,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items = notes, key = { item -> item.note.id }) { item ->
                        BentoNoteCard(
                            item = item,
                            query = query,
                            folderName = item.note.folderId?.let(folderNames::get),
                            folderColor = item.note.folderId?.let(folderColors::get)
                                ?: MaterialTheme.colorScheme.outline,
                            selectionMode = selectionMode,
                            selected = item.note.id in selectedIds,
                            onClick = {
                                if (selectionMode) {
                                    viewModel.toggleSelection(item.note.id)
                                } else {
                                    onOpenNote(item.note.id, query)
                                }
                            },
                            onLongClick = { viewModel.startSelection(item.note.id) },
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }

    if (showBulkDelete) {
        AppModal(
            onDismissRequest = { showBulkDelete = false },
            title = stringResource(R.string.home_bulk_delete_title),
            dismissLabel = stringResource(R.string.action_cancel),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                showBulkDelete = false
                viewModel.deleteNotes(selectedIds)
            },
        ) {
            Text(
                text = stringResource(R.string.home_bulk_delete_body, selectedIds.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showMovePicker) {
        AppModal(
            onDismissRequest = { showMovePicker = false },
            title = stringResource(R.string.home_move_title),
            dismissLabel = stringResource(R.string.action_cancel),
        ) {
            folders.forEach { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable {
                            viewModel.moveNotesToFolder(selectedIds, folder.id)
                            showMovePicker = false
                        },
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
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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

    paywallReason?.let { reason ->
        ProPaywallSheet(
            reason = reason,
            price = billingState.formattedPrice,
            onDismiss = viewModel::dismissPaywall,
            onPurchase = {
                viewModel.dismissPaywall()
                onPurchase()
            },
            onRestore = { callback -> viewModel.restorePurchases(callback) },
        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BulkSelectionBar(
    anyPinned: Boolean,
    onMove: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home-bulk-bar"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BulkActionButton(
            icon = Icons.Rounded.Folder,
            label = stringResource(R.string.home_bulk_move),
            tag = "home-bulk-move",
            onClick = onMove,
        )
        BulkActionButton(
            icon = Icons.Rounded.PushPin,
            label = stringResource(
                if (anyPinned) R.string.home_bulk_unpin else R.string.home_bulk_pin,
            ),
            tag = "home-bulk-pin",
            onClick = onPin,
        )
        BulkActionButton(
            icon = Icons.Rounded.Delete,
            label = stringResource(R.string.home_bulk_delete),
            tag = "home-bulk-delete",
            onClick = onDelete,
        )
    }
}

@Composable
private fun BulkActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(tag),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun QuickCaptureBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onOpenEditor: () -> Unit,
    onScan: () -> Unit,
    freeScansLeft: Int?,
    largeFont: Boolean,
) {
    val wordCount = remember(text) {
        if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }
    Column(modifier = Modifier.fillMaxWidth().testTag("home-quick-capture")) {
        if (freeScansLeft != null) {
            Text(
                text = stringResource(R.string.home_free_scans, freeScansLeft),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (text.length > 120) {
            Text(
                text = stringResource(R.string.home_word_count, wordCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .testTag("home-word-count"),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                ),
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 54.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                            .testTag("home-quick-capture-field"),
                        singleLine = false,
                        minLines = 1,
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box {
                                if (text.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.home_quick_capture_placeholder),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    IconButton(
                        onClick = onOpenEditor,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home-quick-editor"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInFull,
                            contentDescription = stringResource(R.string.cd_open_editor),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = onSend,
                        enabled = text.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home-quick-send"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.cd_save_quick),
                            tint = if (text.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            FilledIconButton(
                onClick = onScan,
                modifier = Modifier
                    .size(if (largeFont) 62.dp else 56.dp)
                    .testTag("home-quick-scan"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DocumentScanner,
                    contentDescription = stringResource(R.string.cd_scan_ocr),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
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
