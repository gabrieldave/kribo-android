package com.blackeye.kribo.ui.note

import android.content.ClipData
import com.blackeye.kribo.data.blocks.AccentTone
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDropDownCircle
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FormatClear
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LowPriority
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle as TextSpanStyle
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blackeye.kribo.AppContainer
import com.blackeye.kribo.billing.ProLimits
import com.blackeye.kribo.ui.billing.PaywallReason
import com.blackeye.kribo.ui.billing.ProPaywallSheet
import com.blackeye.kribo.ui.common.findActivity
import com.blackeye.kribo.R
import com.blackeye.kribo.data.AttachmentEntity
import com.blackeye.kribo.data.FolderEntity
import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.data.blocks.NoteBlock
import com.blackeye.kribo.data.blocks.LinkBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.ActiveStyles
import com.blackeye.kribo.data.blocks.InlineSpan
import com.blackeye.kribo.data.blocks.SpanType
import com.blackeye.kribo.data.blocks.UrlSafety
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import com.blackeye.kribo.data.blocks.ToggleBlock
import com.blackeye.kribo.domain.editor.BlockReorder
import com.blackeye.kribo.ui.common.SearchText
import com.blackeye.kribo.ui.components.AppModal
import com.blackeye.kribo.ui.components.AppScreen
import com.blackeye.kribo.ui.components.StatusPill
import com.blackeye.kribo.ui.components.TooltipAction
import com.blackeye.kribo.ui.components.hideKeyboardOnScroll
import com.blackeye.kribo.ui.components.isImeVisible
import com.blackeye.kribo.ui.components.rememberKeyboardScrollGuard
import com.blackeye.kribo.data.export.NoteExportPayload
import com.blackeye.kribo.ui.share.AiShareSheet
import com.blackeye.kribo.ui.theme.InlineColors
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NoteEditorScreen(
    noteId: Long,
    initialQuery: String = "",
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val viewModel: NoteEditorViewModel = viewModel(
        key = "note-editor-$noteId",
        factory = viewModelFactory {
            initializer {
                NoteEditorViewModel(
                    repository = container.noteRepository,
                    attachmentStore = container.attachmentStore,
                    noteId = noteId,
                    initialSearchQuery = initialQuery,
                )
            }
        },
    )

    val blocks = viewModel.blocks
    val attachmentMap = remember(viewModel.attachments) {
        viewModel.attachments.associateBy { it.id }
    }
    val isPro by container.entitlementRepository.isPro.collectAsStateWithLifecycle()
    val billingState by container.billingManager.state.collectAsStateWithLifecycle()
    val titleBlock = blocks.filterIsInstance<TitleBlock>()
        .firstOrNull { it.text.isNotBlank() }
        ?.text
    val screenTitle = viewModel.noteTitle.ifBlank { titleBlock ?: "" }.take(40)
        .ifBlank { stringResource(R.string.note_title_edit) }
    // The legacy H1 that mirrors the title is hidden so it is not shown twice (#38).
    // Blank-title notes also hide a blank leading H1 seeded by an older version.
    val hiddenDuplicateHead = blocks.firstOrNull().let {
        it is TitleBlock &&
            (it.text.isBlank() && viewModel.noteTitle.isBlank() ||
                viewModel.noteTitle.isNotBlank() && it.text.trim() == viewModel.noteTitle.trim())
    }
    val bodyBlocks = if (hiddenDuplicateHead) blocks.drop(1) else blocks
    val reorderMinIndex = if (hiddenDuplicateHead) 1 else 0
    val listState = rememberLazyListState()
    // One guard shared by the list and by focus/creation events: the IME only leaves on a
    // deliberate >48.dp finger drag, never because Compose kept a focused field in view.
    val keyboardGuard = rememberKeyboardScrollGuard()
    val appTheme by container.uiPreferences.theme.collectAsStateWithLifecycle()
    val darkTheme = !appTheme.isLight
    val noteFontFamily = when (viewModel.noteFont) {
        com.blackeye.kribo.data.NoteFont.SANS -> FontFamily.Default
        com.blackeye.kribo.data.NoteFont.SERIF -> FontFamily.Serif
        com.blackeye.kribo.data.NoteFont.MONO -> FontFamily.Monospace
    }

    var showExport by remember { mutableStateOf(false) }
    var showAiShare by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf(false) }
    var showAttachChooser by remember { mutableStateOf(false) }
    var viewerPath by remember { mutableStateOf<String?>(null) }
    var viewerLabel by remember { mutableStateOf("") }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var formatMode by remember { mutableStateOf(false) }
    var reorderMode by remember { mutableStateOf(false) }
    var reorderQuery by remember { mutableStateOf("") }
    var moveToBlockId by remember { mutableStateOf<String?>(null) }
    var flashBlockId by remember { mutableStateOf<String?>(null) }
    val reorderListState = rememberLazyListState()
    var paywallReason by remember { mutableStateOf<PaywallReason?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val deletedMessage = stringResource(R.string.note_deleted_snackbar)
    val undoLabel = stringResource(R.string.action_undo)
    val manualPhotoCount = blocks.count { it is AttachmentBlock && it.manual }
    val requestImage: (Uri) -> Unit = { uri ->
        if (ProLimits.canAttachManualPhoto(manualPhotoCount, isPro)) {
            viewModel.addImage(uri, context.contentResolver)
        } else {
            paywallReason = PaywallReason.PHOTO_LIMIT
        }
    }

    val activeBlock = blocks.firstOrNull { it.id == viewModel.focusedBlockId }
    val folderLabel = viewModel.folders.firstOrNull { it.id == viewModel.folderId }?.name
        ?: stringResource(R.string.folder_general)

    val highlight = SpanStyle(
        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) requestImage(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = cameraUri
        cameraUri = null
        if (success && uri != null) requestImage(uri)
    }

    val launchCamera: () -> Unit = {
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(directory, "capture-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    val exit: () -> Unit = { viewModel.flushAndExit(onBack) }
    val leaveReorder: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus()
        reorderMode = false
        reorderQuery = ""
        moveToBlockId = null
        flashBlockId = null
    }
    BackHandler {
        if (reorderMode) leaveReorder() else exit()
    }

    LaunchedEffect(flashBlockId) {
        if (flashBlockId != null) {
            delay(1400)
            flashBlockId = null
        }
    }

    /** Scrolls the reorder list to a block and flashes it so the new position is obvious. */
    val revealBlock: (String) -> Unit = { blockId ->
        flashBlockId = blockId
        val entries = if (reorderQuery.isBlank()) {
            viewModel.blocks.withIndex().toList()
        } else {
            BlockReorder.filterBlocksForSearch(viewModel.blocks, reorderQuery)
        }
        val displayIndex = entries
            .filterNot { hiddenDuplicateHead && it.index == 0 }
            .indexOfFirst { it.value.id == blockId }
        if (displayIndex >= 0) {
            scope.launch {
                delay(60)
                reorderListState.animateScrollToItem(displayIndex)
            }
        }
    }

    val deleteWithUndo: () -> Unit = {
        showDeleteConfirm = false
        scope.launch {
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
                viewModel.deleteNote(onBack)
            }
        }
    }

    // New blocks take the keyboard, get a soft haptic tick and stay above the IME.
    // This is a programmatic scroll: it is flagged as user input by Compose's
    // bring-into-view machinery, but the guard ignores it (no finger down).
    LaunchedEffect(viewModel.focusRequestId) {
        val requestId = viewModel.focusRequestId ?: return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        // The folder chip and the title field are the first items, so blocks shift by two.
        val index = blocks.indexOfFirst { it.id == requestId }
        val hidden = if (hiddenDuplicateHead) 1 else 0
        if (index >= hidden) listState.animateScrollToItem(index - hidden + 2)
    }

    fun exportAndShare(pdf: Boolean) {
        showExport = false
        scope.launch {
            val file = runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    // The note title (sanitized) names the exported file, not an internal id.
                    val baseName = sanitizedExportName(titleBlock ?: viewModel.noteTitle, noteId)
                    if (pdf) {
                        container.exportService.exportNotePdf(noteId, File(dir, "$baseName.pdf"))
                    } else {
                        container.exportService.exportNoteMarkdown(noteId, File(dir, "$baseName.md"))
                    }
                }
            }.getOrNull()
            if (file == null) {
                exportError = true
            } else {
                runCatching {
                    shareFile(
                        context = context,
                        file = file,
                        mimeType = if (pdf) "application/pdf" else "text/markdown",
                    )
                }.onFailure { exportError = true }
            }
        }
    }

    val scrollTarget = remember(blocks, viewModel.highlightQuery) {
        val query = viewModel.highlightQuery
        if (query.isBlank()) {
            -1
        } else {
            blocks.indexOfFirst { SearchText.contains(it.plainText(), query) }
        }
    }
    LaunchedEffect(scrollTarget, viewModel.isLoading) {
        if (!viewModel.isLoading && scrollTarget >= 0) {
            // The folder chip is the first list item, so block indices shift by one.
            val hidden = if (hiddenDuplicateHead) 1 else 0
            if (scrollTarget >= hidden) listState.animateScrollToItem(scrollTarget - hidden + 2)
        }
    }

    AppScreen(
        title = screenTitle,
        onBack = exit,
        scrollable = false,
        actions = {
            IconButton(
                onClick = exit,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("editor-home"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = stringResource(R.string.cd_home),
                )
            }
            IconButton(
                onClick = viewModel::undo,
                enabled = viewModel.canUndo,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("editor-undo"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = stringResource(R.string.cd_undo),
                    tint = if (viewModel.canUndo) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    },
                )
            }
            IconButton(
                onClick = viewModel::redo,
                enabled = viewModel.canRedo,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("editor-redo"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Redo,
                    contentDescription = stringResource(R.string.cd_redo),
                    tint = if (viewModel.canRedo) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    },
                )
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("editor-more"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.cd_more),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (viewModel.isPinned) R.string.cd_unpin else R.string.cd_pin,
                                ),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = null,
                                tint = if (viewModel.isPinned) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            viewModel.togglePinned()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_title)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Share, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            showExport = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ai_share_title)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            showAiShare = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reorder_title)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.SwapVert, contentDescription = null)
                        },
                        enabled = blocks.size >= 2,
                        onClick = {
                            menuExpanded = false
                            keyboardController?.hide()
                            viewModel.prepareForReorder()
                            reorderQuery = ""
                            reorderMode = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            showDeleteConfirm = true
                        },
                    )
                }
            }
        },
        footer = if (reorderMode) {
            null
        } else {
            {
                if (formatMode) {
                    EditorFormatBar(
                        onBack = { formatMode = false },
                        onToggleFormatMode = { },
                        activeStyles = viewModel.activeTypingStyles,
                        noteFont = viewModel.noteFont,
                        onToggleStyle = { type, tone ->
                            viewModel.focusedBlockId?.let { viewModel.toggleFormatting(it, type, tone) }
                        },
                        onClearFormatting = {
                            viewModel.focusedBlockId?.let { viewModel.clearFormatting(it) }
                        },
                        onSetFont = { font -> viewModel.updateNoteFont(font) },
                        darkTheme = darkTheme,
                    )
                } else {
                    EditorToolbar(
                        onAdd = { block ->
                            keyboardGuard.onBlockCreated()
                            viewModel.addBlock(block)
                        },
                        onAttachImage = {
                            // Capture the caret before the chooser steals focus.
                            viewModel.beginAttachImage()
                            showAttachChooser = true
                        },
                        activeBlock = activeBlock,
                        activeStyles = viewModel.activeTypingStyles,
                        formatMode = formatMode,
                        onToggleFormatMode = { formatMode = !formatMode },
                    )
                }
            }
        },
    ) {
        if (reorderMode) {
            ReorderPane(
                blocks = blocks,
                noteTitle = viewModel.noteTitle.ifBlank { titleBlock.orEmpty() },
                hiddenFirst = hiddenDuplicateHead,
                query = reorderQuery,
                onQueryChange = { reorderQuery = it },
                listState = reorderListState,
                flashBlockId = flashBlockId,
                onDone = leaveReorder,
                onDragStart = viewModel::beginBlockDrag,
                onDragEnd = viewModel::endBlockDrag,
                onPreviewMove = viewModel::previewMoveBlock,
                onJumpTop = { blockId ->
                    viewModel.moveBlockToTop(blockId, reorderMinIndex)
                    revealBlock(blockId)
                },
                onJumpBottom = { blockId ->
                    viewModel.moveBlockToBottom(blockId)
                    revealBlock(blockId)
                },
                onMoveBy = { blockId, delta ->
                    viewModel.moveBlockBy(blockId, delta, reorderMinIndex)
                    revealBlock(blockId)
                },
                onMoveTo = { blockId -> moveToBlockId = blockId },
            )
        } else if (viewModel.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            if (viewModel.highlightQuery.isNotBlank()) {
                SearchHighlightChip(
                    query = viewModel.highlightQuery,
                    onClear = viewModel::clearHighlight,
                )
                Spacer(Modifier.height(6.dp))
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .hideKeyboardOnScroll(keyboardGuard),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp),
            ) {
                item(key = "editor-folder-chip") {
                    Column {
                        FolderChipButton(
                            label = folderLabel,
                            onClick = { showFolderPicker = true },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                item(key = "editor-title-field") {
                    NoteTitleField(
                        value = viewModel.noteTitle,
                        fontFamily = noteFontFamily,
                        onValueChange = viewModel::updateNoteTitle,
                        onSubmit = {
                            keyboardGuard.onBlockCreated()
                            viewModel.focusFirstParagraph()
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (bodyBlocks.isEmpty() && blocks.isEmpty()) {
                    item(key = "editor-empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.editor_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(items = bodyBlocks, key = { it.id }) { block ->
                    BlockRow(
                        block = block,
                        focused = viewModel.focusedBlockId == block.id,
                        focusRequest = viewModel.focusRequestId == block.id,
                        flash = viewModel.createdBlockId == block.id,
                        attachment = (block as? AttachmentBlock)?.attachmentId?.let(attachmentMap::get),
                        highlightQuery = viewModel.highlightQuery,
                        highlightStyle = highlight,
                        onFocus = {
                            keyboardGuard.onFocusChanged()
                            viewModel.onFocus(block.id)
                        },
                        onFocusRequestHandled = { viewModel.consumeFocusRequest(block.id) },
                        onSelection = { startSel, endSel, field ->
                            viewModel.onSelection(block.id, startSel, endSel, field)
                        },
                        onEnterBelow = {
                            keyboardGuard.onBlockCreated()
                            viewModel.insertParagraphBelow(block.id)
                        },
                        onTitlePaste = { id, pasted -> viewModel.applyTitlePaste(id, pasted) },
                        onDoubleEnter = { id ->
                            keyboardGuard.onBlockCreated()
                            viewModel.handleDoubleEnter(id)
                        },
                        onTextChange = { value -> viewModel.updateBlockText(block.id, value) },
                        onToggleTitleChange = { value -> viewModel.updateToggleTitle(block.id, value) },
                        fontFamily = noteFontFamily,
                        darkTheme = darkTheme,
                        onUpdate = { transform ->
                            viewModel.updateBlock(block.id, transform)
                        },
                        onDelete = { viewModel.removeBlock(block.id) },
                        onOpenImage = { path, label ->
                            keyboardController?.hide()
                            viewerPath = path
                            viewerLabel = label
                        },
                        onSaveAttachmentText = { attachmentBlock, text ->
                            val attachmentId = attachmentBlock.attachmentId
                            if (attachmentId != null) {
                                viewModel.updateAttachmentText(attachmentBlock.id, attachmentId, text)
                            }
                        },
                        onEnterNextTask = {
                            keyboardGuard.onBlockCreated()
                            viewModel.exitTaskBelow(block.id)
                        },
                        onSaveAndHome = if (block is AttachmentBlock && block.pageNumber > 0) {
                            exit
                        } else {
                            null
                        },
                    )
                }
                val lastBodyBlock = bodyBlocks.lastOrNull()
                if (!(lastBodyBlock is ParagraphBlock && lastBodyBlock.text.isBlank())) {
                    item(key = "editor-new-paragraph") {
                        NewParagraphRow(
                            onClick = {
                                keyboardGuard.onBlockCreated()
                                viewModel.appendParagraph()
                            },
                        )
                    }
                }
                item(key = "editor-bottom-space") {
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = {
                                    keyboardGuard.onBlockCreated()
                                    viewModel.appendParagraph()
                                },
                            ),
                    )
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }

    if (showAttachChooser) {
        AppModal(
            onDismissRequest = { showAttachChooser = false },
            title = stringResource(R.string.attach_image_title),
            dismissLabel = stringResource(R.string.action_cancel),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        showAttachChooser = false
                        galleryLauncher.launch("image/*")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.attach_from_gallery))
                }
                OutlinedButton(
                    onClick = {
                        showAttachChooser = false
                        launchCamera()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.attach_from_camera))
                }
            }
        }
    }

    viewerPath?.let { path ->
        ZoomableImageViewer(
            path = path,
            title = viewerLabel,
            onDismiss = { viewerPath = null },
        )
    }

    if (showDeleteConfirm) {
        AppModal(
            onDismissRequest = { showDeleteConfirm = false },
            title = stringResource(R.string.home_delete_title),
            dismissLabel = stringResource(R.string.action_cancel),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = deleteWithUndo,
        ) {
            Text(
                text = stringResource(R.string.home_delete_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showAiShare) {
        val aiTitle = viewModel.noteTitle
            .ifBlank { BlockCodec.derivedTitle(blocks) }
            .ifBlank { stringResource(R.string.note_title_edit) }
        AiShareSheet(
            notes = listOf(
                NoteExportPayload(
                    title = aiTitle,
                    folder = folderLabel,
                    blocks = blocks,
                    ocrTexts = viewModel.attachments
                        .filter { it.extractedText.isNotBlank() }
                        .associate { it.id to it.extractedText },
                ),
            ),
            onDismiss = { showAiShare = false },
        )
    }

    val movingBlock = moveToBlockId?.let { id -> blocks.firstOrNull { it.id == id } }
    if (movingBlock != null) {
        MoveBlockSheet(
            moving = movingBlock,
            blocks = blocks,
            hiddenFirst = hiddenDuplicateHead,
            onDismiss = { moveToBlockId = null },
            onConfirm = { targetId, placeBefore ->
                viewModel.moveBlockRelativeTo(movingBlock.id, targetId, placeBefore)
                moveToBlockId = null
                reorderQuery = ""
                focusManager.clearFocus()
                keyboardController?.hide()
                revealBlock(movingBlock.id)
            },
        )
    }

    if (showFolderPicker) {
        // Same searchable sheet as Home: instant search with per-folder counts.
        com.blackeye.kribo.ui.home.FolderPickerSheet(
            folders = viewModel.folders,
            counts = emptyMap(),
            selectedId = viewModel.folderId,
            onSelect = { id ->
                viewModel.moveToFolder(id)
                showFolderPicker = false
            },
            onCreateFolder = null,
            onDismiss = { showFolderPicker = false },
        )
    }

    if (showExport) {
        AppModal(
            onDismissRequest = { showExport = false },
            title = stringResource(R.string.export_title),
            dismissLabel = stringResource(R.string.action_close),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { exportAndShare(pdf = false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.export_markdown))
                }
                OutlinedButton(
                    onClick = { exportAndShare(pdf = true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.export_pdf))
                }
            }
        }
    }

    if (exportError) {
        AppModal(
            onDismissRequest = { exportError = false },
            title = stringResource(R.string.export_error_title),
            dismissLabel = stringResource(R.string.action_close),
        ) {
            Text(
                text = stringResource(R.string.export_error_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    paywallReason?.let { reason ->
        ProPaywallSheet(
            reason = reason,
            price = billingState.formattedPrice,
            onDismiss = { paywallReason = null },
            onPurchase = {
                paywallReason = null
                context.findActivity()?.let { container.entitlementRepository.launchPurchase(it) }
            },
            onRestore = { callback -> container.entitlementRepository.restore(callback) },
        )
    }

    if (viewModel.imageError) {
        AppModal(
            onDismissRequest = viewModel::clearImageError,
            title = stringResource(R.string.image_add_error_title),
            dismissLabel = stringResource(R.string.action_close),
        ) {
            Text(
                text = stringResource(R.string.image_add_error_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FolderChipButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("editor-folder-chip"),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.editor_move_note_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SearchHighlightChip(query: String, onClear: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.search_highlight_chip, query),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.search_highlight_clear),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun EditorToolbar(
    onAdd: (NoteBlock) -> Unit,
    onAttachImage: () -> Unit,
    activeBlock: NoteBlock?,
    activeStyles: ActiveStyles = ActiveStyles(),
    formatMode: Boolean = false,
    onToggleFormatMode: () -> Unit = {},
) {
    val activeTitleLevel = (activeBlock as? TitleBlock)?.level
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = isImeVisible()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .testTag("editor-toolbar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            // The eight block actions stay fully visible on phones >= 340dp. Scroll only on
            // very narrow lanes or large-font accessibility modes, with a right-edge fade.
            val scrollable = maxWidth < TOOLBAR_SCROLL_MIN_LANE_WIDTH ||
                LocalDensity.current.fontScale >= TOOLBAR_LARGE_FONT_SCALE
            if (scrollable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolbarActions(
                        activeTitleLevel = activeTitleLevel,
                        activeBlock = activeBlock,
                        onAdd = onAdd,
                        onAttachImage = onAttachImage,
                        scrollable = true,
                        activeStyles = activeStyles,
                        formatMode = formatMode,
                        onToggleFormatMode = onToggleFormatMode,
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(TOOLBAR_EDGE_FADE_WIDTH)
                        .height(TOOLBAR_CELL_HEIGHT)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                            ),
                        ),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        0.dp,
                        Alignment.CenterHorizontally,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolbarActions(
                        activeTitleLevel = activeTitleLevel,
                        activeBlock = activeBlock,
                        onAdd = onAdd,
                        onAttachImage = onAttachImage,
                        scrollable = false,
                        activeStyles = activeStyles,
                        formatMode = formatMode,
                        onToggleFormatMode = onToggleFormatMode,
                    )
                }
            }
        }

        // The IME button lives outside the scroll area: it is always anchored on the right.
        if (imeVisible) {
            VerticalDivider(
                modifier = Modifier.height(26.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            )
            TooltipAction(
                tooltip = stringResource(R.string.cd_hide_keyboard),
                onClick = { keyboard?.hide() },
                modifier = Modifier
                    .width(TOOLBAR_IME_BUTTON_WIDTH)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("editor-hide-keyboard"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardHide,
                    contentDescription = stringResource(R.string.cd_hide_keyboard),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * The seven block actions. When the lane is wide enough each one takes an equal share
 * (`weight`); otherwise they keep a fixed, still tappable cell inside a scroll row.
 */
@Composable
private fun RowScope.ToolbarActions(
    activeTitleLevel: Int?,
    activeBlock: NoteBlock?,
    onAdd: (NoteBlock) -> Unit,
    onAttachImage: () -> Unit,
    scrollable: Boolean,
    activeStyles: ActiveStyles = ActiveStyles(),
    formatMode: Boolean = false,
    onToggleFormatMode: () -> Unit = {},
) {
    // A fresh modifier per action: reusing one `weight` instance across children would make a
    // single cell swallow the whole row.
    fun cell(tag: String? = null): Modifier {
        val base = if (scrollable) {
            Modifier
                .width(TOOLBAR_SCROLL_CELL_WIDTH)
                .height(TOOLBAR_CELL_HEIGHT)
        } else {
            Modifier
                .weight(1f)
                .height(TOOLBAR_CELL_HEIGHT)
        }
        return if (tag == null) base else base.then(Modifier.testTag(tag))
    }
    // Format mode toggle first: Dot shows when styles are active at the caret.
    ToolbarTextButton(
        label = "Aa",
        descriptionRes = R.string.cd_format_mode,
        tooltip = stringResource(R.string.cd_format_mode),
        active = formatMode || !activeStyles.isEmpty,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        ),
        modifier = cell("toolbar-format"),
    ) { onToggleFormatMode() }
    ToolbarTextButton(
        label = stringResource(R.string.editor_h1),
        descriptionRes = R.string.cd_block_title,
        tooltip = stringResource(R.string.block_tooltip_h1),
        active = activeTitleLevel == 1,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        ),
        modifier = cell("toolbar-h1"),
    ) { onAdd(TitleBlock(level = 1)) }
    ToolbarTextButton(
        label = stringResource(R.string.editor_h2),
        descriptionRes = R.string.cd_block_subtitle,
        tooltip = stringResource(R.string.block_tooltip_h2),
        active = activeTitleLevel == 2,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        modifier = cell("toolbar-h2"),
    ) { onAdd(TitleBlock(level = 2)) }
    ToolbarButton(
        icon = Icons.AutoMirrored.Rounded.Subject,
        descriptionRes = R.string.cd_block_paragraph,
        tooltipRes = R.string.block_tooltip_paragraph,
        active = activeBlock is ParagraphBlock,
        modifier = cell(),
    ) { onAdd(ParagraphBlock()) }
    ToolbarButton(
        icon = Icons.Rounded.CheckBox,
        descriptionRes = R.string.cd_block_task,
        tooltipRes = R.string.block_tooltip_task,
        active = activeBlock is TaskBlock,
        modifier = cell(),
    ) { onAdd(TaskBlock()) }
    ToolbarButton(
        icon = Icons.Rounded.ArrowDropDownCircle,
        descriptionRes = R.string.cd_block_toggle,
        tooltipRes = R.string.block_tooltip_toggle,
        active = activeBlock is ToggleBlock,
        modifier = cell("toolbar-toggle"),
    ) { onAdd(ToggleBlock.newSection()) }
    ToolbarButton(
        icon = Icons.Rounded.FormatQuote,
        descriptionRes = R.string.cd_block_quote,
        tooltipRes = R.string.block_tooltip_quote,
        active = activeBlock is QuoteBlock,
        modifier = cell(),
    ) { onAdd(QuoteBlock()) }
    ToolbarButton(
        icon = Icons.Rounded.Link,
        descriptionRes = R.string.cd_insert_link,
        tooltipRes = R.string.block_tooltip_link,
        active = activeBlock is LinkBlock,
        modifier = cell("toolbar-link"),
    ) { onAdd(LinkBlock()) }
    ToolbarButton(
        icon = Icons.Rounded.AddPhotoAlternate,
        descriptionRes = R.string.cd_attach_image,
        tooltipRes = R.string.block_tooltip_image,
        active = activeBlock is AttachmentBlock,
        modifier = cell("toolbar-image"),
    ) { onAttachImage() }
}

@Composable
internal fun EditorFormatBar(
    onBack: () -> Unit,
    onToggleFormatMode: () -> Unit,
    activeStyles: ActiveStyles,
    noteFont: com.blackeye.kribo.data.NoteFont,
    onToggleStyle: (SpanType, AccentTone?) -> Unit,
    onClearFormatting: () -> Unit,
    onSetFont: (com.blackeye.kribo.data.NoteFont) -> Unit,
    darkTheme: Boolean = true,
) {
    var openPalette by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = isImeVisible()
    Column(modifier = Modifier.testTag("editor-format-bar")) {
        // Palettes pop above the bar (same footprint, no vertical jump below).
        if (openPalette == "font") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.blackeye.kribo.data.NoteFont.entries.forEach { font ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (noteFont == font) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { onSetFont(font) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when (font) {
                                    com.blackeye.kribo.data.NoteFont.SANS -> stringResource(R.string.font_sans)
                                    com.blackeye.kribo.data.NoteFont.SERIF -> stringResource(R.string.font_serif)
                                    com.blackeye.kribo.data.NoteFont.MONO -> stringResource(R.string.font_mono)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (noteFont == font) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        } else if (openPalette != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToneCircle(
                    color = InlineColors.automatic,
                    selected = when (openPalette) {
                        "text" -> activeStyles.textTone == null
                        "hl" -> activeStyles.highlightTone == null
                        else -> false
                    },
                    description = stringResource(
                        if (openPalette == "text") R.string.format_automatic else R.string.format_none,
                    ),
                    onClick = {
                        onToggleStyle(
                            if (openPalette == "text") SpanType.TEXT_COLOR else SpanType.HIGHLIGHT,
                            null,
                        )
                    },
                    slash = true,
                )
                AccentTone.entries.forEach { tone ->
                    ToneCircle(
                        color = if (openPalette == "text") {
                            InlineColors.textColor(tone, darkTheme)
                        } else {
                            InlineColors.highlightColor(tone, darkTheme)
                        },
                        selected = when (openPalette) {
                            "text" -> activeStyles.textTone == tone
                            "hl" -> activeStyles.highlightTone == tone
                            else -> false
                        },
                        description = tone.name.lowercase().replaceFirstChar(Char::uppercase),
                        onClick = {
                            onToggleStyle(
                                if (openPalette == "text") SpanType.TEXT_COLOR else SpanType.HIGHLIGHT,
                                tone,
                            )
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .testTag("editor-format-bar-row"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val scrollable = maxWidth < TOOLBAR_SCROLL_MIN_LANE_WIDTH ||
                    LocalDensity.current.fontScale >= TOOLBAR_LARGE_FONT_SCALE
                Row(
                    modifier = if (scrollable) {
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    } else {
                        Modifier.fillMaxWidth()
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    fun cell(tag: String? = null): Modifier {
                        val base = if (scrollable) {
                            Modifier
                                .width(TOOLBAR_SCROLL_CELL_WIDTH)
                                .height(TOOLBAR_CELL_HEIGHT)
                        } else {
                            Modifier
                                .weight(1f)
                                .height(TOOLBAR_CELL_HEIGHT)
                        }
                        return if (tag == null) base else base.then(Modifier.testTag(tag))
                    }
                    FormatNavButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        description = stringResource(R.string.cd_back_to_blocks),
                        modifier = cell("format-back"),
                    ) { onBack() }
                    FormatGlyphButton(
                        label = "B",
                        active = activeStyles.bold,
                        description = stringResource(R.string.cd_bold),
                        modifier = cell("format-bold"),
                    ) { onToggleStyle(SpanType.BOLD, null) }
                    FormatGlyphButton(
                        label = "I",
                        active = activeStyles.italic,
                        description = stringResource(R.string.cd_italic),
                        modifier = cell("format-italic"),
                    ) { onToggleStyle(SpanType.ITALIC, null) }
                    FormatGlyphButton(
                        label = "U",
                        active = activeStyles.underline,
                        description = stringResource(R.string.cd_underline),
                        modifier = cell("format-underline"),
                    ) { onToggleStyle(SpanType.UNDERLINE, null) }
                    FormatGlyphButton(
                        label = "S",
                        active = activeStyles.strikethrough,
                        description = stringResource(R.string.cd_strikethrough),
                        modifier = cell("format-strike"),
                    ) { onToggleStyle(SpanType.STRIKETHROUGH, null) }
                    FormatColorButton(
                        label = "A",
                        activeColor = activeStyles.textTone?.let { InlineColors.textColor(it, darkTheme) }
                            ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        active = activeStyles.textTone != null || openPalette == "text",
                        description = stringResource(R.string.cd_text_color),
                        modifier = cell("format-color"),
                    ) { openPalette = if (openPalette == "text") null else "text" }
                    FormatColorButton(
                        label = "A",
                        activeColor = activeStyles.highlightTone?.let { InlineColors.highlightColor(it, darkTheme) }
                            ?: MaterialTheme.colorScheme.surfaceVariant,
                        active = activeStyles.highlightTone != null || openPalette == "hl",
                        description = stringResource(R.string.cd_highlight),
                        modifier = cell("format-highlight"),
                        pill = true,
                    ) { openPalette = if (openPalette == "hl") null else "hl" }
                    FormatGlyphButton(
                        label = when (noteFont) {
                            com.blackeye.kribo.data.NoteFont.SANS -> "Ag"
                            com.blackeye.kribo.data.NoteFont.SERIF -> "Ag"
                            com.blackeye.kribo.data.NoteFont.MONO -> "Tt"
                        },
                        active = noteFont != com.blackeye.kribo.data.NoteFont.SANS,
                        description = stringResource(R.string.cd_format_mode),
                        modifier = cell("format-font"),
                        serif = noteFont == com.blackeye.kribo.data.NoteFont.SERIF,
                        mono = noteFont == com.blackeye.kribo.data.NoteFont.MONO,
                    ) { openPalette = if (openPalette == "font") null else "font" }
                    FormatNavButton(
                        icon = Icons.Rounded.FormatClear,
                        description = stringResource(R.string.cd_clear_formatting),
                        modifier = cell("format-clear"),
                    ) { onClearFormatting() }
                }
            }
            if (imeVisible) {
                VerticalDivider(
                    modifier = Modifier.height(26.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                )
                TooltipAction(
                    tooltip = stringResource(R.string.cd_hide_keyboard),
                    onClick = { keyboard?.hide() },
                    modifier = Modifier
                        .width(TOOLBAR_IME_BUTTON_WIDTH)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .testTag("format-hide-keyboard"),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardHide,
                            contentDescription = stringResource(R.string.cd_hide_keyboard),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneCircle(
    color: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    slash: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (slash) 0.35f else 1f))
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
                },
            )
            .clickable(onClick = onClick)
            .testTag("tone-$description"),
        contentAlignment = Alignment.Center,
    ) {
        if (slash) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(2.dp)
                    .graphicsLayer { rotationZ = -45f }
                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun FormatGlyphButton(
    label: String,
    active: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    serif: Boolean = false,
    mono: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold,
        color = contentColor,
        fontStyle = if (label == "I") androidx.compose.ui.text.font.FontStyle.Italic else null,
        textDecoration = when (label) {
            "U" -> androidx.compose.ui.text.style.TextDecoration.Underline
            "S" -> androidx.compose.ui.text.style.TextDecoration.LineThrough
            else -> null
        },
        fontFamily = when {
            serif -> FontFamily.Serif
            mono -> FontFamily.Monospace
            else -> null
        },
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        TooltipAction(
            tooltip = description,
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = style,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FormatColorButton(
    label: String,
    activeColor: Color,
    active: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    pill: Boolean = false,
    onClick: () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        TooltipAction(
            tooltip = description,
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (pill) {
                    // Highlighter: the "A" sits inside a tinted pill (not a bar).
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(activeColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(activeColor),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        TooltipAction(
            tooltip = description,
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolbarTextButton(
    label: String,
    descriptionRes: Int,
    tooltip: String,
    active: Boolean,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val contentColor = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    // TooltipBox applies its modifier to an inner anchor node, so the cell (weight/width) lives
    // on this wrapper Box: Row parent data is only read from direct children.
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        TooltipAction(
            tooltip = tooltip,
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else Color.Transparent,
                ),
        ) {
            // The tag lives on the inner cell so callers can add their own semantic tag
            // (toolbar-h1/h2, toolbar-toggle…) without overwriting this one.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("editor-toolbar-button"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = style,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    descriptionRes: Int,
    tooltipRes: Int,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val contentColor = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        TooltipAction(
            tooltip = stringResource(tooltipRes),
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("editor-toolbar-button"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(descriptionRes),
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun BlockRow(
    block: NoteBlock,
    focused: Boolean,
    focusRequest: Boolean,
    flash: Boolean,
    attachment: AttachmentEntity?,
    highlightQuery: String,
    highlightStyle: SpanStyle,
    onFocus: () -> Unit,
    onFocusRequestHandled: () -> Unit,
    onSelection: (Int, Int, com.blackeye.kribo.ui.note.NoteEditorViewModel.BlockField) -> Unit,
    onEnterBelow: () -> Unit,
    onDoubleEnter: (String) -> Unit,
    onTitlePaste: (String, String) -> Unit,
    onTextChange: (String) -> Unit,
    onToggleTitleChange: (String) -> Unit,
    fontFamily: FontFamily?,
    darkTheme: Boolean,
    onUpdate: ((NoteBlock) -> NoteBlock) -> Unit,
    onDelete: () -> Unit,
    onOpenImage: (String, String) -> Unit,
    onSaveAttachmentText: (AttachmentBlock, String) -> Unit,
    onEnterNextTask: () -> Unit,
    onSaveAndHome: (() -> Unit)?,
) {
    // Soft confirmation pulse: the new block briefly tints with the accent colour.
    val flashAlpha = remember(block.id) { Animatable(0f) }
    LaunchedEffect(flash) {
        if (flash) {
            flashAlpha.snapTo(1f)
            flashAlpha.animateTo(0f, tween(420))
        }
    }
    // Enter on a collapsible section title opens it and hands focus to the inner field.
    var toggleContentFocus by remember(block.id) { mutableStateOf(false) }
    val onSelectionTitle: (Int, Int) -> Unit = { start, end ->
        onSelection(start, end, com.blackeye.kribo.ui.note.NoteEditorViewModel.BlockField.TOGGLE_TITLE)
    }
    val onSelectionContent: (Int, Int) -> Unit = { start, end ->
        onSelection(start, end, com.blackeye.kribo.ui.note.NoteEditorViewModel.BlockField.TOGGLE_CONTENT)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f * flashAlpha.value),
                RoundedCornerShape(10.dp),
            ),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.weight(1f)) {
                when (block) {
                    is TitleBlock -> {
                        BlockTextField(
                            value = block.text,
                            onValueChange = { value ->
                                if (value.contains('\n')) {
                                    onTitlePaste(block.id, value)
                                } else {
                                    onTextChange(value)
                                }
                            },
                            textStyle = if (block.level <= 1) {
                                MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                )
                            } else {
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            placeholder = stringResource(
                                if (block.level <= 1) R.string.block_heading_placeholder
                                else R.string.block_subtitle_placeholder,
                            ),
                            onFocus = onFocus,
                            requestFocus = focusRequest,
                            onFocusRequestHandled = onFocusRequestHandled,
                            onSelectionChange = { start, end ->
                                onSelection(start, end, com.blackeye.kribo.ui.note.NoteEditorViewModel.BlockField.MAIN)
                            },
                            onSubmit = onEnterBelow,
                            spans = block.spans,
                            fontFamily = fontFamily,
                            darkTheme = darkTheme,
                            // Allow pasted \n through so it can be split, while the
                            // tactile Enter key still descends via ImeAction.Next.
                            singleLine = false,
                            highlightQuery = highlightQuery,
                            highlightStyle = highlightStyle,
                            modifier = Modifier.padding(top = if (block.level <= 1) 14.dp else 10.dp),
                        )
                    }

                    is ParagraphBlock -> Column(modifier = Modifier.fillMaxWidth()) {
                        BlockTextField(
                        value = block.text,
                        onValueChange = onTextChange,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = stringResource(R.string.block_paragraph_placeholder),
                        onFocus = onFocus,
                        requestFocus = focusRequest,
                        onFocusRequestHandled = onFocusRequestHandled,
                        onSelectionChange = { start, end ->
                                onSelection(start, end, com.blackeye.kribo.ui.note.NoteEditorViewModel.BlockField.MAIN)
                            },
                        spans = block.spans,
                        fontFamily = fontFamily,
                        darkTheme = darkTheme,
                        highlightQuery = highlightQuery,
                        highlightStyle = highlightStyle,
                        modifier = Modifier.padding(vertical = 6.dp),
                        )
                        UrlChipsRow(text = block.text)
                    }

                    is TaskBlock -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = block.checked,
                            onCheckedChange = { checked ->
                                onUpdate { current -> (current as TaskBlock).copy(checked = checked) }
                            },
                        )
                        BlockTextField(
                            value = block.text,
                            onValueChange = onTextChange,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            placeholder = stringResource(R.string.block_task_placeholder),
                            onFocus = onFocus,
                            requestFocus = focusRequest,
                            onFocusRequestHandled = onFocusRequestHandled,
                            onSelectionChange = { start, end ->
                                onSelection(start, end, com.blackeye.kribo.ui.note.NoteEditorViewModel.BlockField.MAIN)
                            },
                            onSubmit = onEnterNextTask,
                            spans = block.spans,
                            fontFamily = fontFamily,
                            darkTheme = darkTheme,
                            highlightQuery = highlightQuery,
                            highlightStyle = highlightStyle,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 6.dp),
                        )
                    }

                    is ToggleBlock -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 6.dp),
                    ) {
                        // Universal affordance: a triangle that rotates 90° when the section opens.
                        val chevronRotation by animateFloatAsState(
                            targetValue = if (block.expanded) 90f else 0f,
                            animationSpec = tween(durationMillis = 180),
                            label = "toggle-chevron",
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    onUpdate { current ->
                                        (current as ToggleBlock).copy(expanded = !block.expanded)
                                    }
                                },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowRight,
                                    contentDescription = stringResource(
                                        if (block.expanded) R.string.cd_collapse
                                        else R.string.cd_expand,
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer { rotationZ = chevronRotation },
                                )
                            }
                            BlockTextField(
                                value = block.title,
                                onValueChange = onToggleTitleChange,
                                textStyle = MaterialTheme.typography.titleMedium,
                                placeholder = stringResource(R.string.block_toggle_placeholder),
                                onFocus = onFocus,
                                requestFocus = focusRequest,
                                onFocusRequestHandled = onFocusRequestHandled,
                                onSelectionChange = onSelectionTitle,
                                onSubmit = {
                                    // Natural flow: Enter on the title opens the section and
                                    // jumps into its content, keeping the IME alive.
                                    if (!block.expanded) {
                                        onUpdate { current ->
                                            (current as ToggleBlock).copy(expanded = true)
                                        }
                                    }
                                    toggleContentFocus = true
                                },
                                spans = block.titleSpans,
                                fontFamily = fontFamily,
                                darkTheme = darkTheme,
                                highlightQuery = highlightQuery,
                                highlightStyle = highlightStyle,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        AnimatedVisibility(visible = block.expanded) {
                            BlockTextField(
                                value = block.content,
                                onValueChange = { value ->
                                    if (value.endsWith("\n\n")) {
                                        onDoubleEnter(block.id)
                                    } else {
                                        onTextChange(value)
                                    }
                                },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                placeholder = stringResource(R.string.block_toggle_content_placeholder),
                                onFocus = onFocus,
                                requestFocus = toggleContentFocus,
                                onFocusRequestHandled = { toggleContentFocus = false },
                                onSelectionChange = onSelectionContent,
                                spans = block.contentSpans,
                                fontFamily = fontFamily,
                                darkTheme = darkTheme,
                                highlightQuery = highlightQuery,
                                highlightStyle = highlightStyle,
                                modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
                            )
                        }
                    }

                    is LinkBlock -> LinkCard(
                        block = block,
                        focusUrl = focusRequest,
                        onUpdate = onUpdate,
                        onDelete = onDelete,
                        onFocusRequestHandled = onFocusRequestHandled,
                    )

                    is QuoteBlock -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(vertical = 8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Spacer(Modifier.width(12.dp))
                        BlockTextField(
                            value = block.text,
                            onValueChange = { value ->
                                if (value.endsWith("\n\n") || value == "\n") {
                                    onDoubleEnter(block.id)
                                } else {
                                    onUpdate { current -> (current as QuoteBlock).copy(text = value) }
                                }
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontStyle = FontStyle.Italic,
                            ),
                            placeholder = stringResource(R.string.block_quote_placeholder),
                            onFocus = onFocus,
                            requestFocus = focusRequest,
                            onFocusRequestHandled = onFocusRequestHandled,
                            onSelectionChange = { start, end ->
                                onSelection(start, end, com.blackeye.kribo.ui.note.NoteEditorViewModel.BlockField.MAIN)
                            },
                            spans = block.spans,
                            fontFamily = fontFamily,
                            darkTheme = darkTheme,
                            highlightQuery = highlightQuery,
                            highlightStyle = highlightStyle,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    is AttachmentBlock -> AttachmentCard(
                        block = block,
                        attachment = attachment,
                        highlightQuery = highlightQuery,
                        highlightStyle = highlightStyle,
                        onDelete = onDelete,
                        onOpenImage = onOpenImage,
                        onSaveText = onSaveAttachmentText,
                        onSaveAndHome = onSaveAndHome,
                    )
                }
            }

            if (focused && block !is AttachmentBlock && block !is LinkBlock) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_delete_block),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        if (block is TaskBlock) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
            )
        }
    }
}

@Composable
private fun NoteTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    fontFamily: FontFamily? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("editor-title-field"),
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = fontFamily ?: MaterialTheme.typography.headlineSmall.fontFamily,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = false,
        maxLines = 3,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onSubmit() }),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.block_title_placeholder),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            }
        },
    )
}

/**
 * Builds the visible string: plain text + inline spans + search highlight, all with
 * identity offset mapping so IME/Gboard caret math never shifts.
 */
private fun buildFormattedSpans(
    text: String,
    spans: List<InlineSpan>,
    darkTheme: Boolean,
    highlightQuery: String,
    highlightStyle: TextSpanStyle,
): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    var decorations = emptyList<Triple<Int, Int, androidx.compose.ui.text.style.TextDecoration>>()
    for (span in spans) {
        val start = span.start.coerceIn(0, text.length)
        val end = span.end.coerceIn(start, text.length)
        if (start >= end) continue
        when (span.type) {
            SpanType.BOLD -> builder.addStyle(TextSpanStyle(fontWeight = FontWeight.Bold), start, end)
            SpanType.ITALIC -> builder.addStyle(
                TextSpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                start,
                end,
            )
            SpanType.UNDERLINE, SpanType.STRIKETHROUGH -> {
                val added = if (span.type == SpanType.UNDERLINE) {
                    androidx.compose.ui.text.style.TextDecoration.Underline
                } else {
                    androidx.compose.ui.text.style.TextDecoration.LineThrough
                }
                // Union with earlier decorations overlapping this range (never lose one).
                val overlapping = decorations.filter { it.first < end && it.second > start }
                var merged = added
                overlapping.forEach { merged += it.third }
                decorations += Triple(start, end, added)
                builder.addStyle(TextSpanStyle(textDecoration = merged), start, end)
            }
            SpanType.TEXT_COLOR -> {
                val tone = span.tone ?: continue
                builder.addStyle(
                    TextSpanStyle(color = InlineColors.textColor(tone, darkTheme)),
                    start,
                    end,
                )
            }
            SpanType.HIGHLIGHT -> {
                val tone = span.tone ?: continue
                builder.addStyle(
                    TextSpanStyle(background = InlineColors.highlightColor(tone, darkTheme)),
                    start,
                    end,
                )
            }
        }
    }
    if (highlightQuery.isNotBlank()) {
        SearchText.matches(text, highlightQuery).forEach { match ->
            builder.addStyle(
                highlightStyle,
                match.start.coerceIn(0, text.length),
                match.end.coerceIn(match.start, text.length),
            )
        }
    }
    return builder.toAnnotatedString()
}

@Composable
private fun BlockTextField(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    placeholder: String,
    onFocus: () -> Unit,
    highlightQuery: String,
    highlightStyle: SpanStyle,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
    onFocusRequestHandled: () -> Unit = {},
    onSelectionChange: (Int, Int) -> Unit = { _, _ -> Unit },
    onSubmit: (() -> Unit)? = null,
    singleLine: Boolean = onSubmit != null,
    spans: List<InlineSpan> = emptyList(),
    fontFamily: FontFamily? = null,
    darkTheme: Boolean = true,
) {
    val transformation = remember(spans, darkTheme, highlightQuery, highlightStyle) {
        VisualTransformation { text ->
            TransformedText(
                buildFormattedSpans(text.text, spans, darkTheme, highlightQuery, highlightStyle),
                OffsetMapping.Identity,
            )
        }
    }
    val requester = remember { BringIntoViewRequester() }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    var cursorRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    // A newly inserted block (Enter / toolbar) must keep the IME up: request focus and show the
    // keyboard explicitly, retrying across a few frames while LazyColumn composes and places it.
    // A note opened from Home never sets this flag, so reading mode stays keyboard-free.
    LaunchedEffect(requestFocus) {
        if (!requestFocus) return@LaunchedEffect
        repeat(FOCUS_ATTEMPTS) { attempt ->
            if (attempt > 0) withFrameNanos { }
            val focused = runCatching { focusRequester.requestFocus() }.isSuccess
            if (focused) {
                // Let the focus event reach the text input session before asking for the IME.
                withFrameNanos { }
                keyboard?.show()
                onFocusRequestHandled()
                return@LaunchedEffect
            }
        }
        // Not composed yet (long note): the view-model keeps the request until the block appears.
    }

    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            val cursor = fieldValue.selection.start.coerceIn(0, value.length)
            fieldValue = TextFieldValue(value, TextRange(cursor))
        }
    }
    // The toolbar reads the caret from this signal: it survives button taps because the
    // buttons never take text focus away, and it is also reported on focus/tap.
    LaunchedEffect(fieldValue.selection) {
        onSelectionChange(fieldValue.selection.start, fieldValue.selection.end)
    }
    // After every keystroke/Enter the cursor rect is already valid: keep it above the IME and
    // below the formatting toolbar, even in very long notes.
    LaunchedEffect(fieldValue.text, fieldValue.selection) {
        withFrameNanos { }
        cursorRect?.let { rect -> runCatching { requester.bringIntoView(rect) } }
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { updated ->
            fieldValue = updated
            onValueChange(updated.text)
        },
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .focusRequester(focusRequester)
            .onFocusChanged { state -> if (state.isFocused) onFocus() }
            .testTag("editor-block-field"),
        textStyle = textStyle.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = fontFamily ?: textStyle.fontFamily,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = transformation,
        singleLine = singleLine,
        keyboardOptions = if (onSubmit != null) {
            KeyboardOptions(imeAction = ImeAction.Next)
        } else {
            KeyboardOptions.Default
        },
        keyboardActions = if (onSubmit != null) {
            KeyboardActions(onNext = { onSubmit.invoke() })
        } else {
            KeyboardActions.Default
        },
        onTextLayout = { layout ->
            val cursor = fieldValue.selection.end
                .coerceIn(0, layout.layoutInput.text.length)
            cursorRect = layout.getCursorRect(cursor)
        },
        decorationBox = { innerTextField ->
            Box {
                if (fieldValue.text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                innerTextField()
            }
        },
    )
}

/**
 * #40/#43 polish: a link is either **compact** (one clean row, no empty label placeholder)
 * or in **edit** mode (label + URL + Done). A freshly inserted empty link starts in edit
 * mode; a saved link lands compact and returns there when its URL field loses focus.
 */
@Composable
private fun LinkCard(
    block: LinkBlock,
    focusUrl: Boolean,
    onUpdate: ((NoteBlock) -> NoteBlock) -> Unit,
    onDelete: () -> Unit,
    onFocusRequestHandled: () -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val urlFocus = remember { FocusRequester() }
    var editing by remember(block.id) { mutableStateOf(block.url.isBlank()) }
    var hadUrlFocus by remember(block.id) { mutableStateOf(false) }
    var copied by remember(block.id) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1500)
            copied = false
        }
    }
    LaunchedEffect(focusUrl) {
        if (focusUrl) {
            editing = true
            runCatching { urlFocus.requestFocus() }
            withFrameNanos { }
            keyboard?.show()
            onFocusRequestHandled()
        }
    }
    LaunchedEffect(editing) {
        if (editing) {
            withFrameNanos { }
            runCatching { urlFocus.requestFocus() }
            keyboard?.show()
        }
    }
    val openUrl: () -> Unit = {
        UrlSafety.normalizeUrl(block.url)?.let { openSafeUrl(context, it) }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        if (editing) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = block.label,
                        onValueChange = { value ->
                            onUpdate { current -> (current as LinkBlock).copy(label = value) }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor-link-label"),
                        textStyle = MaterialTheme.typography.titleSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        maxLines = 1,
                        decorationBox = { inner ->
                            Box {
                                if (block.label.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.link_title_hint),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_delete_block),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = block.url,
                        onValueChange = { value ->
                            onUpdate { current -> (current as LinkBlock).copy(url = value) }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(urlFocus)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    hadUrlFocus = true
                                } else if (hadUrlFocus && block.url.isNotBlank()) {
                                    hadUrlFocus = false
                                    editing = false
                                }
                            }
                            .testTag("editor-link-url"),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        maxLines = 1,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { if (block.url.isNotBlank()) editing = false },
                        ),
                        decorationBox = { inner ->
                            Box {
                                if (block.url.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.link_url_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    IconButton(
                        onClick = {
                            keyboard?.hide()
                            if (block.url.isNotBlank()) editing = false
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("editor-link-done"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.cd_link_done),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = openUrl)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (block.label.isNotBlank()) {
                        Text(
                            text = block.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = BlockReorder.formatCompactDisplayUrl(block.url),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = BlockReorder.formatCompactDisplayUrl(block.url),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = {
                        copyToClipboard(context, "link", block.url)
                        copied = true
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("editor-link-copy"),
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(
                            if (copied) R.string.link_copied else R.string.cd_copy_link,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = openUrl,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("editor-link-open"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.cd_open_link),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { editing = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("editor-link-edit"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.cd_edit_link),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("editor-link-delete"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_delete_block),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private val ReorderCardHeight = 72.dp
private val ReorderCardGap = 4.dp

/**
 * Compact reorder workspace: fixed header + block search + draggable 1–2 line cards.
 * Text fields are replaced by cards so the keyboard and [KeyboardScrollGate] never interfere.
 */
@Composable
private fun ColumnScope.ReorderPane(
    blocks: List<NoteBlock>,
    noteTitle: String,
    hiddenFirst: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    flashBlockId: String?,
    onDone: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onPreviewMove: (String, String) -> Unit,
    onJumpTop: (String) -> Unit,
    onJumpBottom: (String) -> Unit,
    onMoveBy: (String, Int) -> Unit,
    onMoveTo: (String) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var draggingId by remember { mutableStateOf<String?>(null) }
    var lastTargetId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val entries = remember(blocks, query, hiddenFirst) {
        val base = if (query.isBlank()) {
            blocks.withIndex().toList()
        } else {
            BlockReorder.filterBlocksForSearch(blocks, query)
        }
        if (hiddenFirst) base.filterNot { it.index == 0 } else base
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.SwapVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.reorder_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = onDone,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("reorder-done"),
            ) {
                Text(
                    text = stringResource(R.string.reorder_done),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (query.isBlank()) {
            Text(
                text = noteTitle.ifBlank { stringResource(R.string.note_title_edit) },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reorder-search"),
            placeholder = { Text(stringResource(R.string.reorder_search_hint)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_reorder_clear_search),
                        )
                    }
                }
            } else {
                null
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(6.dp))

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.reorder_no_matches),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ReorderCardGap),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(items = entries, key = { it.value.id }) { entry ->
                    val block = entry.value
                    val dragging = draggingId == block.id
                    ReorderBlockCard(
                        block = block,
                        position = entry.index + 1,
                        draggable = query.isBlank(),
                        dragging = dragging,
                        dragOffset = if (dragging) dragOffset else 0f,
                        flash = flashBlockId == block.id,
                        onDragStart = {
                            draggingId = block.id
                            lastTargetId = null
                            dragOffset = 0f
                            onDragStart()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { deltaY ->
                            dragOffset += deltaY
                            val info = listState.layoutInfo
                            val visible = info.visibleItemsInfo.firstOrNull { it.key == block.id }
                            if (visible != null) {
                                val fingerY = visible.offset + dragOffset + visible.size / 2f
                                val target = info.visibleItemsInfo.firstOrNull { candidate ->
                                    candidate.key != block.id &&
                                        fingerY >= candidate.offset &&
                                        fingerY < candidate.offset + candidate.size
                                }
                                if (target == null) {
                                    lastTargetId = null
                                } else {
                                    val targetId = target.key.toString()
                                    if (targetId != lastTargetId) {
                                        lastTargetId = targetId
                                        onPreviewMove(block.id, targetId)
                                        // Keep the card under the finger after the swap.
                                        dragOffset = fingerY - target.offset - visible.size / 2f
                                        haptic.performHapticFeedback(
                                            HapticFeedbackType.TextHandleMove,
                                        )
                                    }
                                }
                                // Auto-scroll when the finger nears the top or bottom edge.
                                val threshold = with(density) { 80.dp.toPx() }
                                val viewportStart = info.viewportStartOffset
                                val viewportEnd = info.viewportEndOffset
                                val scroll = when {
                                    fingerY < viewportStart + threshold ->
                                        -((viewportStart + threshold - fingerY) / 6f)
                                    fingerY > viewportEnd - threshold ->
                                        ((fingerY - (viewportEnd - threshold)) / 6f)
                                    else -> 0f
                                }
                                if (scroll != 0f) {
                                    scope.launch { listState.scrollBy(scroll) }
                                }
                            }
                        },
                        onDragEnd = {
                            draggingId = null
                            lastTargetId = null
                            dragOffset = 0f
                            onDragEnd()
                        },
                        onJumpTop = { onJumpTop(block.id) },
                        onJumpBottom = { onJumpBottom(block.id) },
                        onMoveBy = { delta -> onMoveBy(block.id, delta) },
                        onMoveTo = { onMoveTo(block.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderBlockCard(
    block: NoteBlock,
    position: Int,
    draggable: Boolean,
    dragging: Boolean,
    dragOffset: Float,
    flash: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onJumpTop: () -> Unit,
    onJumpBottom: () -> Unit,
    onMoveBy: (Int) -> Unit,
    onMoveTo: () -> Unit,
) {
    var menuOpen by remember(block.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(ReorderCardHeight)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
            .then(if (dragging) Modifier.shadow(12.dp, shape) else Modifier)
            .testTag("reorder-card-${block.id}"),
        shape = shape,
        color = if (flash) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (dragging) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            },
        ),
        tonalElevation = if (dragging) 6.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (draggable) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .pointerInput(block.id) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    onDrag(amount.y)
                                },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                            )
                        }
                        .testTag("reorder-handle-${block.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragIndicator,
                        contentDescription = stringResource(R.string.cd_reorder_drag),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "#$position",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = reorderTypeLabel(block),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = reorderPreview(block),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("reorder-menu-${block.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LowPriority,
                        contentDescription = stringResource(R.string.cd_reorder_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    ReorderMenuItem(
                        labelRes = R.string.reorder_move_top,
                        icon = Icons.Rounded.VerticalAlignTop,
                    ) {
                        menuOpen = false
                        onJumpTop()
                    }
                    ReorderMenuItem(
                        labelRes = R.string.reorder_move_bottom,
                        icon = Icons.Rounded.VerticalAlignBottom,
                    ) {
                        menuOpen = false
                        onJumpBottom()
                    }
                    ReorderMenuItem(
                        labelRes = R.string.reorder_move_to,
                        icon = Icons.Rounded.NearMe,
                    ) {
                        menuOpen = false
                        onMoveTo()
                    }
                    ReorderMenuItem(
                        labelRes = R.string.reorder_move_up_one,
                        icon = Icons.Rounded.KeyboardArrowUp,
                    ) {
                        menuOpen = false
                        onMoveBy(-1)
                    }
                    ReorderMenuItem(
                        labelRes = R.string.reorder_move_down_one,
                        icon = Icons.Rounded.KeyboardArrowDown,
                    ) {
                        menuOpen = false
                        onMoveBy(1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderMenuItem(
    labelRes: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun reorderTypeLabel(block: NoteBlock): String = when (block) {
    is TitleBlock -> stringResource(
        if (block.level <= 1) R.string.reorder_type_h1 else R.string.reorder_type_h2,
    )
    is ParagraphBlock -> stringResource(R.string.reorder_type_text)
    is TaskBlock -> stringResource(R.string.reorder_type_task)
    is ToggleBlock -> stringResource(R.string.reorder_type_toggle)
    is QuoteBlock -> stringResource(R.string.reorder_type_quote)
    is LinkBlock -> stringResource(R.string.reorder_type_link)
    is AttachmentBlock -> stringResource(R.string.reorder_type_image)
}

@Composable
private fun reorderPreview(block: NoteBlock): String {
    val empty = stringResource(R.string.reorder_empty_block)
    val raw = when (block) {
        is TitleBlock -> block.text
        is ParagraphBlock -> block.text
        is TaskBlock -> block.text
        is QuoteBlock -> block.text
        is ToggleBlock -> listOf(block.title, block.content)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        is LinkBlock -> block.label.ifBlank { BlockReorder.formatCompactDisplayUrl(block.url) }
        is AttachmentBlock -> block.fileName.ifBlank { block.previewText }
    }
    return raw.trim().ifBlank { empty }
}

/** Searchable "Move to…" sheet: type the destination section or pick it from the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveBlockSheet(
    moving: NoteBlock,
    blocks: List<NoteBlock>,
    hiddenFirst: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (targetBlockId: String, placeBefore: Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var placeBefore by remember { mutableStateOf(false) }
    val movingPreview = reorderPreview(moving)

    val targets = remember(blocks, query, moving.id, hiddenFirst) {
        val base = if (query.isBlank()) {
            blocks.withIndex().toList()
        } else {
            BlockReorder.filterBlocksForSearch(blocks, query)
        }
        base.filter { entry ->
            entry.value.id != moving.id && !(hiddenFirst && entry.index == 0)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.reorder_move_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.reorder_moving, movingPreview),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("move-to-search"),
                placeholder = { Text(stringResource(R.string.reorder_move_target_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = placeBefore,
                    onClick = { placeBefore = true },
                    label = { Text(stringResource(R.string.reorder_before)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                FilterChip(
                    selected = !placeBefore,
                    onClick = { placeBefore = false },
                    label = { Text(stringResource(R.string.reorder_after)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (targets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.reorder_no_matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(items = targets, key = { it.value.id }) { entry ->
                        val target = entry.value
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable { onConfirm(target.id, placeBefore) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("move-to-target-${target.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "#${entry.index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(44.dp),
                                maxLines = 1,
                                softWrap = false,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = reorderTypeLabel(target),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = reorderPreview(target),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TooltipIconButtonX(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    testTag: String,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .testTag(testTag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun UrlChipsRow(text: String) {
    val urls = remember(text) { UrlSafety.extractSafeUrls(text) }
    if (urls.isEmpty()) return
    val context = LocalContext.current
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        urls.forEach { url ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier = Modifier
                        .clickable { openSafeUrl(context, url) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = UrlSafety.hostOf(url),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NewParagraphRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.editor_new_paragraph),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Opens only http(s) links; anything else (javascript:, file:, …) is silently ignored. */
private fun openSafeUrl(context: Context, url: String) {
    val normalized = UrlSafety.normalizeUrl(url) ?: return
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    } catch (_: Exception) {
        // Non-matching browser or malformed URL: keep editing undisturbed.
    }
}

@Composable
private fun AttachmentCard(
    block: AttachmentBlock,
    attachment: AttachmentEntity?,
    highlightQuery: String,
    highlightStyle: SpanStyle,
    onDelete: () -> Unit,
    onOpenImage: (String, String) -> Unit,
    onSaveText: (AttachmentBlock, String) -> Unit,
    onSaveAndHome: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val path = attachment?.filePath
    val label = attachmentLabel(block)
    var expanded by remember(block.id) { mutableStateOf(false) }
    var editing by remember(block.id) { mutableStateOf(false) }
    var draft by remember(block.id) { mutableStateOf(block.previewText) }
    var copied by remember(block.id) { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Column {
            val bitmap = rememberAttachmentBitmap(path, maxLongSide = 1400)
            if (bitmap != null && path != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 300.dp)
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                        .background(Color.Black)
                        .clickable { onOpenImage(path, label) },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.attachment_view_full),
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ZoomIn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(18.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                if (block.previewText.isNotBlank()) {
                    StatusPill(text = stringResource(R.string.badge_ocr))
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_delete_block),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (block.previewText.isNotBlank()) {
                if (editing) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            label = { Text(stringResource(R.string.attachment_edit_ocr_label)) },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    draft = block.previewText
                                    editing = false
                                },
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            TextButton(
                                onClick = {
                                    onSaveText(block, draft.trim())
                                    editing = false
                                },
                            ) {
                                Text(stringResource(R.string.attachment_save_text))
                            }
                        }
                    }
                } else {
                    Text(
                        text = SearchText.highlight(block.previewText, highlightQuery, highlightStyle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        maxLines = if (expanded) Int.MAX_VALUE else 6,
                        overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    )
                    AttachmentActionsRow(
                        expanded = expanded,
                        copied = copied,
                        onToggleExpand = { expanded = !expanded },
                        onCopy = {
                            copyToClipboard(context, label, block.previewText)
                            copied = true
                        },
                        onEdit = {
                            draft = block.previewText
                            editing = true
                        },
                    )
                }
            } else {
                Spacer(Modifier.height(10.dp))
            }

            if (onSaveAndHome != null && block.previewText.isNotBlank()) {
                Button(
                    onClick = onSaveAndHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .heightIn(min = 48.dp)
                        .testTag("ocr-save-home"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ocr_save_home),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AttachmentActionsRow(
    expanded: Boolean,
    copied: Boolean,
    onToggleExpand: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("attachment-actions"),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        maxItemsInEachRow = 3,
    ) {
        AttachmentActionButton(
            label = stringResource(
                if (expanded) R.string.attachment_view_less else R.string.attachment_view_all,
            ),
            icon = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            tag = "attachment-action-view",
            onClick = onToggleExpand,
        )
        AttachmentActionButton(
            label = stringResource(
                if (copied) R.string.attachment_copied_short else R.string.attachment_copy_short,
            ),
            icon = Icons.Rounded.ContentCopy,
            tag = "attachment-action-copy",
            onClick = onCopy,
        )
        AttachmentActionButton(
            label = stringResource(R.string.attachment_edit_short),
            icon = Icons.Rounded.Edit,
            tag = "attachment-action-edit",
            onClick = onEdit,
        )
    }
}

@Composable
private fun AttachmentActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    onClick: () -> Unit,
) {
    TextButton(
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
private fun attachmentLabel(block: AttachmentBlock): String = when {
    block.pageNumber == 1 -> stringResource(R.string.attachment_scan_page, block.pageNumber)
    block.pageNumber > 1 -> stringResource(R.string.attachment_page, block.pageNumber)
    else -> stringResource(R.string.attachment_image)
}

@Composable
private fun ZoomableImageViewer(
    path: String,
    title: String,
    onDismiss: () -> Unit,
) {
    val bitmap = rememberAttachmentBitmap(path, maxLongSide = 3200)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    AppModal(
        onDismissRequest = onDismiss,
        title = title,
        dismissLabel = stringResource(R.string.action_close),
        fullScreen = true,
        scrollable = false,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, 6f)
                        scale = next
                        offset = if (next > 1f) offset + pan else Offset.Zero
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                    contentScale = ContentScale.Fit,
                )
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.attachment_zoom_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
private fun rememberAttachmentBitmap(path: String?, maxLongSide: Int): Bitmap? {
    val bitmap by produceState<Bitmap?>(initialValue = null, path, maxLongSide) {
        value = if (path.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeSampled(path, maxLongSide) }
        }
    }
    return bitmap
}

private fun decodeSampled(path: String, maxLongSide: Int): Bitmap? {
    val file = File(path)
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    val longest = max(bounds.outWidth, bounds.outHeight)
    while (longest / (sample * 2) >= maxLongSide) sample *= 2
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    )
}

private fun shareFile(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

/** Sanitized note title used as the exported file name (`.pdf` / `.md`). */
private fun sanitizedExportName(title: String, noteId: Long): String {
    val clean = title
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "-")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', '.')
    return clean.take(60).ifBlank { "Kribo-$noteId" }
}

private const val DELETE_UNDO_WINDOW_MS = 6_000L

/** Frames a freshly created block may spend waiting for LazyColumn to place it. */
private const val FOCUS_ATTEMPTS = 4

/** Seven block actions in the editor footer. */
private const val BLOCK_ACTION_COUNT = 8

/** Below this lane width the actions switch to a scrollable strip. */
private val TOOLBAR_SCROLL_MIN_LANE_WIDTH = 288.dp

/** At this font scale H1/H2 must not shrink past legibility, so the strip scrolls. */
private const val TOOLBAR_LARGE_FONT_SCALE = 1.4f

/** Below this cell width the footer actions would be unreadable, so they become scrollable. */
private val TOOLBAR_MIN_CELL_WIDTH = 34.dp

/** Fixed cell used only by the narrow-screen scroll fallback. */
private val TOOLBAR_SCROLL_CELL_WIDTH = 44.dp

/** Cell height shared by the toolbar strip and its edge fade. */
private val TOOLBAR_CELL_HEIGHT = 48.dp

/** The keyboard-hide action is anchored outside the scroll area, so it stays narrow. */
private val TOOLBAR_IME_BUTTON_WIDTH = 38.dp

/** Right-edge fade hinting that the action strip scrolls on very narrow screens. */
private val TOOLBAR_EDGE_FADE_WIDTH = 14.dp
