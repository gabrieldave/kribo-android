package com.blackeye.kribo.ui.home

import android.app.Activity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.blackeye.kribo.data.export.NoteExportPayload
import com.blackeye.kribo.data.export.copyTextToClipboard
import com.blackeye.kribo.ui.common.SearchText
import com.blackeye.kribo.ui.common.displayNameFor
import com.blackeye.kribo.ui.common.findActivity
import com.blackeye.kribo.ui.share.AiShareSheet
import com.blackeye.kribo.ui.components.AppModal
import com.blackeye.kribo.ui.components.AppScreen
import com.blackeye.kribo.ui.components.BentoNoteCard
import com.blackeye.kribo.ui.billing.ProPaywallSheet
import com.blackeye.kribo.ui.components.CompactNoteRow
import com.blackeye.kribo.ui.components.EmptyStateCard
import com.blackeye.kribo.ui.components.TooltipIconButton
import com.blackeye.kribo.ui.components.TooltipTextButton
import com.blackeye.kribo.ui.components.hideKeyboardOnScroll
import com.blackeye.kribo.ui.components.rememberKeyboardScrollGuard
import com.blackeye.kribo.ui.preferences.HomeLayout
import com.blackeye.kribo.ui.preferences.UiPreferences
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val searchAllScope by viewModel.searchAllScopeActive.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortModeActive.collectAsStateWithLifecycle()
    val folderCounts by viewModel.folderCounts.collectAsStateWithLifecycle()
    val matchingFolders by viewModel.matchingFolders.collectAsStateWithLifecycle()
    val totalNoteCount = remember(folderCounts) { folderCounts.values.sum() }
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
    var showAiShare by remember { mutableStateOf(false) }
    var aiPayloads by remember { mutableStateOf<List<NoteExportPayload>>(emptyList()) }
    var showMovePicker by remember { mutableStateOf(false) }
    var showFolderPickerSheet by remember { mutableStateOf(false) }
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val selectedNotes = remember(notes, selectedIds) { notes.filter { it.note.id in selectedIds } }
    val allSelectedPinned = selectedNotes.isNotEmpty() && selectedNotes.all { it.note.isPinned }

    val folderNames = remember(folders) { folders.associate { it.id to it.name } }
    val folderColors = remember(folders) { folders.associate { it.id to it.color() } }
    val largeFont = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val keyboardGuard = rememberKeyboardScrollGuard()
    val allSelected = notes.isNotEmpty() && selectedIds.size == notes.size
    var searchFocused by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(false) }
    var homeMenuExpanded by remember { mutableStateOf(false) }
    val searchActive = (searchMode || searchFocused || query.isNotBlank()) && !selectionMode

    val exitSearch: () -> Unit = {
        viewModel.clearQuery()
        searchFocused = false
        searchMode = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    BackHandler(enabled = searchActive, onBack = exitSearch)

    val welcomeTitle = stringResource(R.string.welcome_note_title)
    val welcomeSubtitle = stringResource(R.string.welcome_note_subtitle)
    val welcomeTaskOne = stringResource(R.string.welcome_note_task_1)
    val welcomeTaskTwo = stringResource(R.string.welcome_note_task_2)
    val welcomeToggleTitle = stringResource(R.string.welcome_note_toggle_title)
    val welcomeToggleContent = stringResource(R.string.welcome_note_toggle_content)
    val welcomeQuote = stringResource(R.string.welcome_note_quote)
    LaunchedEffect(Unit) {
        viewModel.ensureWelcomeNote(
            WelcomeNoteText(
                title = welcomeTitle,
                subtitle = welcomeSubtitle,
                taskOne = welcomeTaskOne,
                taskTwo = welcomeTaskTwo,
                toggleTitle = welcomeToggleTitle,
                toggleContent = welcomeToggleContent,
                quote = welcomeQuote,
            ),
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val undoScope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.action_undo)
    val deletedMessage = stringResource(R.string.note_deleted_snackbar)
    val copyLabel = stringResource(R.string.app_name)
    val copiedOneMessage = stringResource(R.string.note_copied_snackbar)
    val emptyCopyMessage = stringResource(R.string.note_copy_empty_snackbar)

    // Deletion waits for the 6 s undo window; Room only loses the note if the user lets it go.
    fun scheduleDelete(noteId: Long) {
        undoScope.launch {
            val timeout = launch {
                delay(DELETE_UNDO_WINDOW_MS)
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Indefinite,
            )
            timeout.cancel()
            if (result != SnackbarResult.ActionPerformed) {
                viewModel.deleteNote(noteId)
            }
        }
    }

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
        showHeader = !searchActive,
        actions = {
            if (selectionMode) {
                TooltipTextButton(
                    text = stringResource(
                        if (allSelected) R.string.home_select_none else R.string.home_select_all,
                    ),
                    tooltip = stringResource(
                        if (allSelected) R.string.home_select_none else R.string.home_select_all,
                    ),
                    onClick = {
                        if (allSelected) {
                            viewModel.clearSelection()
                        } else {
                            viewModel.selectAll(notes.map { it.note.id })
                        }
                    },
                    testTag = "home-select-all",
                )
                TooltipIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.home_selection_close),
                    tooltip = stringResource(R.string.home_selection_close),
                    onClick = viewModel::clearSelection,
                    testTag = "home-selection-close",
                )
            } else {
                TooltipIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    tooltip = stringResource(R.string.cd_settings),
                    onClick = onSettings,
                )
                Box {
                    TooltipIconButton(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.cd_more),
                        tooltip = stringResource(R.string.cd_more),
                        onClick = { homeMenuExpanded = true },
                        testTag = "home-more",
                    )
                    DropdownMenu(
                        expanded = homeMenuExpanded,
                        onDismissRequest = { homeMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (homeLayout == HomeLayout.COMPACT) {
                                            R.string.home_view_grid
                                        } else {
                                            R.string.home_view_list
                                        },
                                    ),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (homeLayout == HomeLayout.COMPACT) {
                                        Icons.Rounded.GridView
                                    } else {
                                        Icons.AutoMirrored.Rounded.ViewList
                                    },
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                homeMenuExpanded = false
                                uiPreferences.setHomeLayout(
                                    if (homeLayout == HomeLayout.COMPACT) {
                                        HomeLayout.BENTO
                                    } else {
                                        HomeLayout.COMPACT
                                    },
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_sort_recent)) },
                            leadingIcon = {
                                if (sortMode == NoteSort.RECENT) {
                                    Icon(Icons.Rounded.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                homeMenuExpanded = false
                                viewModel.setSortMode(NoteSort.RECENT)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_sort_title)) },
                            leadingIcon = {
                                if (sortMode == NoteSort.TITLE_ASC) {
                                    Icon(Icons.Rounded.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                homeMenuExpanded = false
                                viewModel.setSortMode(NoteSort.TITLE_ASC)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_sort_oldest)) },
                            leadingIcon = {
                                if (sortMode == NoteSort.OLDEST) {
                                    Icon(Icons.Rounded.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                homeMenuExpanded = false
                                viewModel.setSortMode(NoteSort.OLDEST)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_import_action)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.FileOpen,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                homeMenuExpanded = false
                                importLauncher.launch(arrayOf("*/*"))
                            },
                        )
                    }
                }
            }
        },
        footer = if (selectionMode) {
            {
                BulkSelectionBar(
                    anyPinned = allSelectedPinned,
                    onMove = { showMovePicker = true },
                    onPin = {
                        viewModel.setPinnedFor(selectedIds, !allSelectedPinned)
                    },
                    onCopy = {
                        val orderedIds = selectedNotes.map { it.note.id }.ifEmpty { selectedIds.toList() }
                        val count = orderedIds.size
                        undoScope.launch {
                            val text = viewModel.buildCopyText(orderedIds)
                            snackbarHostState.currentSnackbarData?.dismiss()
                            if (text.isBlank()) {
                                snackbarHostState.showSnackbar(
                                    message = emptyCopyMessage,
                                    duration = SnackbarDuration.Short,
                                )
                            } else {
                                copyTextToClipboard(context, copyLabel, text)
                                viewModel.clearSelection()
                                val message = if (count <= 1) {
                                    copiedOneMessage
                                } else {
                                    context.getString(R.string.notes_copied_snackbar, count)
                                }
                                snackbarHostState.showSnackbar(
                                    message = message,
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    },
                    onShareToAi = {
                        undoScope.launch {
                            val payloads = viewModel.buildAiPayloads(selectedIds)
                            if (payloads.isNotEmpty()) {
                                aiPayloads = payloads
                                showAiShare = true
                            }
                        }
                    },
                    onDelete = { showBulkDelete = true },
                )
            }
        } else if (!searchActive) {
            {
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
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Stable slot: the text field keeps its composition identity when search mode
            // starts, so focus and IME are not stolen mid-typing.
            Box(modifier = Modifier.width(if (searchActive) 48.dp else 0.dp)) {
                if (searchActive) {
                    TooltipIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.home_search_exit),
                        tooltip = stringResource(R.string.home_search_exit),
                        onClick = exitSearch,
                        testTag = "home-search-exit",
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { value ->
                    searchMode = true
                    viewModel.onQueryChange(value)
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focus ->
                        searchFocused = focus.isFocused
                        if (focus.isFocused) {
                            searchMode = true
                            keyboardGuard.onFocusChanged()
                        }
                    },
                placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.action_close),
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { keyboardController?.hide() },
                ),
            )
        }

        if (!searchActive) {
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            FolderChip(
                label = stringResource(R.string.home_folders_chip, totalNoteCount),
                color = MaterialTheme.colorScheme.primary,
                selected = showFolderPickerSheet,
                onClick = { showFolderPickerSheet = true },
            )
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
        }

        Spacer(Modifier.height(8.dp))

        // Search scope chips: when a folder is selected the user can jump between searching
        // every note and only that folder, switching context in one tap.
        if (query.isNotBlank() && activeFolderId != null) {
            val activeName = folders.firstOrNull { it.id == activeFolderId }?.name.orEmpty()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FolderChip(
                    label = stringResource(R.string.home_scope_all),
                    color = MaterialTheme.colorScheme.primary,
                    selected = searchAllScope,
                    onClick = { viewModel.setSearchScopeAll(true) },
                )
                FolderChip(
                    label = stringResource(R.string.home_scope_folder, activeName),
                    color = MaterialTheme.colorScheme.primary,
                    selected = !searchAllScope,
                    onClick = { viewModel.setSearchScopeAll(false) },
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // Folder-name matches for the current query: jump straight into a folder.
        if (query.isNotBlank() && matchingFolders.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                matchingFolders.forEach { folder ->
                    FolderChip(
                        label = folder.name + " · " + (folderCounts[folder.id] ?: 0),
                        color = folder.color(),
                        selected = false,
                        onClick = { viewModel.selectFolder(folder.id) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (query.isNotBlank()) {
            Text(
                text = stringResource(R.string.home_results_count, notes.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
        }

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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hideKeyboardOnScroll(keyboardGuard),
                ) {
                    itemsIndexed(
                        items = notes,
                        key = { _, item -> item.note.id },
                        contentType = { _, _ -> "note" },
                    ) { index, item ->
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .hideKeyboardOnScroll(keyboardGuard),
                    verticalItemSpacing = 10.dp,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items = notes, key = { item -> item.note.id }, contentType = { "note" }) { item ->
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

        SnackbarHost(hostState = snackbarHostState)
    }

    if (showAiShare) {
        AiShareSheet(
            notes = aiPayloads,
            onDismiss = { showAiShare = false },
        )
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

    if (showFolderPickerSheet) {
        FolderPickerSheet(
            folders = folders,
            counts = folderCounts,
            selectedId = activeFolderId,
            onSelect = { id ->
                showFolderPickerSheet = false
                viewModel.selectFolder(id)
            },
            onCreateFolder = { name -> viewModel.createFolder(name) },
            onDismiss = { showFolderPickerSheet = false },
        )
    }

    if (showMovePicker) {
        FolderPickerSheet(
            folders = folders,
            counts = folderCounts,
            selectedId = null,
            onSelect = { id ->
                showMovePicker = false
                viewModel.moveNotesToFolder(selectedIds, id)
            },
            onCreateFolder = { name -> viewModel.createFolder(name) },
            onDismiss = { showMovePicker = false },
        )
    }

    notePendingDelete?.let { note ->
        AppModal(
            onDismissRequest = { notePendingDelete = null },
            title = stringResource(R.string.home_delete_title),
            dismissLabel = stringResource(R.string.action_cancel),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                notePendingDelete = null
                scheduleDelete(note.id)
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
    onCopy: () -> Unit,
    onShareToAi: () -> Unit,
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
            icon = Icons.AutoMirrored.Rounded.DriveFileMove,
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
            icon = Icons.Rounded.ContentCopy,
            label = stringResource(R.string.home_bulk_copy),
            tag = "home-bulk-copy",
            onClick = onCopy,
        )
        BulkActionButton(
            icon = Icons.Rounded.AutoAwesome,
            label = stringResource(R.string.ai_share_title),
            tag = "home-bulk-ai",
            onClick = onShareToAi,
        )
        BulkActionButton(
            icon = Icons.Rounded.Delete,
            label = stringResource(R.string.home_bulk_delete),
            tag = "home-bulk-delete",
            destructive = true,
            onClick = onDelete,
        )
    }
}

@Composable
private fun BulkActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tag: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(tag),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        ),
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

/**
 * Searchable folder selector (#41) shared by Home, the editor folder chip and bulk move.
 * A vertical bottom sheet with instant search, per-folder counts and in-place creation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderPickerSheet(
    folders: List<FolderEntity>,
    counts: Map<Long?, Int>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onCreateFolder: ((String) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var search by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    val visible = remember(folders, search) {
        if (search.isBlank()) folders
        else folders.filter { SearchText.contains(it.name, search) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.home_folder_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                item(key = "all", contentType = "folder") {
                    FolderPickerRow(
                        dotColor = MaterialTheme.colorScheme.primary,
                        label = stringResource(R.string.home_filter_all),
                        count = counts.values.sum(),
                        selected = selectedId == null,
                        onClick = { onSelect(null) },
                    )
                }
                items(items = visible, key = { it.id }, contentType = { "folder" }) { folder ->
                    FolderPickerRow(
                        dotColor = folder.color(),
                        label = folder.name,
                        count = counts[folder.id] ?: 0,
                        selected = selectedId == folder.id,
                        onClick = { onSelect(folder.id) },
                    )
                }
            }
            if (onCreateFolder != null) {
                if (creating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.folder_name_hint)) },
                            singleLine = true,
                            maxLines = 1,
                        )
                        TextButton(
                            onClick = {
                                val clean = newName.trim()
                                if (clean.isNotBlank()) onCreateFolder(clean)
                                newName = ""
                                creating = false
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(
                                stringResource(R.string.action_accept),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    TextButton(
                        onClick = { creating = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(
                            Icons.Rounded.CreateNewFolder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.folder_new),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FolderPickerRow(
    dotColor: Color,
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private const val DELETE_UNDO_WINDOW_MS = 6_000L
