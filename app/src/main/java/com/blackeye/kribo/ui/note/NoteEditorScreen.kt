package com.blackeye.kribo.ui.note

import android.content.ClipData
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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.blackeye.kribo.data.blocks.NoteBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import com.blackeye.kribo.data.blocks.ToggleBlock
import com.blackeye.kribo.ui.common.SearchText
import com.blackeye.kribo.ui.components.AppModal
import com.blackeye.kribo.ui.components.AppScreen
import com.blackeye.kribo.ui.components.StatusPill
import com.blackeye.kribo.ui.components.TooltipAction
import com.blackeye.kribo.ui.components.TooltipIconButton
import com.blackeye.kribo.ui.components.hideKeyboardOnScroll
import com.blackeye.kribo.ui.components.isImeVisible
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
    val screenTitle = titleBlock?.take(40) ?: stringResource(R.string.note_title_edit)
    val listState = rememberLazyListState()

    var showExport by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf(false) }
    var showAttachChooser by remember { mutableStateOf(false) }
    var viewerPath by remember { mutableStateOf<String?>(null) }
    var viewerLabel by remember { mutableStateOf("") }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
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
    BackHandler(onBack = exit)

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
    LaunchedEffect(viewModel.focusRequestId) {
        val requestId = viewModel.focusRequestId ?: return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val index = blocks.indexOfFirst { it.id == requestId }
        if (index >= 0) listState.animateScrollToItem(index)
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
            listState.animateScrollToItem(scrollTarget + 1)
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
        footer = {
            EditorToolbar(
                onAdd = viewModel::addBlock,
                onAttachImage = { showAttachChooser = true },
                activeBlock = activeBlock,
            )
        },
    ) {
        if (viewModel.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (blocks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.editor_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    .hideKeyboardOnScroll(),
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
                items(items = blocks, key = { it.id }) { block ->
                    BlockRow(
                        block = block,
                        focused = viewModel.focusedBlockId == block.id,
                        focusRequest = viewModel.focusRequestId == block.id,
                        flash = viewModel.focusRequestId == block.id,
                        attachment = (block as? AttachmentBlock)?.attachmentId?.let(attachmentMap::get),
                        highlightQuery = viewModel.highlightQuery,
                        highlightStyle = highlight,
                        onFocus = { viewModel.onFocus(block.id) },
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
                        onEnterNextTask = { viewModel.addBlock(TaskBlock()) },
                        onSaveAndHome = if (block is AttachmentBlock && block.pageNumber > 0) {
                            exit
                        } else {
                            null
                        },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
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

    if (showFolderPicker) {
        AppModal(
            onDismissRequest = { showFolderPicker = false },
            title = stringResource(R.string.editor_move_note_title),
            dismissLabel = stringResource(R.string.action_cancel),
        ) {
            viewModel.folders.forEach { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable {
                            viewModel.moveToFolder(folder.id)
                            showFolderPicker = false
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
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
) {
    val activeTitleLevel = (activeBlock as? TitleBlock)?.level
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("editor-toolbar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarTextButton(
                label = stringResource(R.string.editor_h1),
                descriptionRes = R.string.cd_block_title,
                tooltip = stringResource(R.string.block_tooltip_h1),
                active = activeTitleLevel == 1,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.testTag("toolbar-h1"),
            ) { onAdd(TitleBlock(level = 1)) }
            ToolbarTextButton(
                label = stringResource(R.string.editor_h2),
                descriptionRes = R.string.cd_block_subtitle,
                tooltip = stringResource(R.string.block_tooltip_h2),
                active = activeTitleLevel == 2,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.testTag("toolbar-h2"),
            ) { onAdd(TitleBlock(level = 2)) }
            ToolbarButton(
                Icons.AutoMirrored.Rounded.Subject,
                R.string.cd_block_paragraph,
                tooltipRes = R.string.block_tooltip_paragraph,
                active = activeBlock is ParagraphBlock,
            ) { onAdd(ParagraphBlock()) }
            ToolbarButton(
                Icons.Rounded.CheckBox,
                R.string.cd_block_task,
                tooltipRes = R.string.block_tooltip_task,
                active = activeBlock is TaskBlock,
            ) { onAdd(TaskBlock()) }
            ToolbarButton(
                Icons.Rounded.UnfoldMore,
                R.string.cd_block_toggle,
                tooltipRes = R.string.block_tooltip_toggle,
                active = activeBlock is ToggleBlock,
            ) { onAdd(ToggleBlock()) }
            ToolbarButton(
                Icons.Rounded.FormatQuote,
                R.string.cd_block_quote,
                tooltipRes = R.string.block_tooltip_quote,
                active = activeBlock is QuoteBlock,
            ) { onAdd(QuoteBlock()) }
            ToolbarButton(
                Icons.Rounded.AddPhotoAlternate,
                R.string.cd_attach_image,
                tooltipRes = R.string.block_tooltip_image,
                active = activeBlock is AttachmentBlock,
            ) { onAttachImage() }
        }

        if (isImeVisible()) {
            TooltipIconButton(
                icon = Icons.Rounded.KeyboardHide,
                contentDescription = stringResource(R.string.cd_hide_keyboard),
                tooltip = stringResource(R.string.cd_hide_keyboard),
                onClick = { keyboard?.hide() },
                modifier = Modifier.padding(start = 4.dp),
                testTag = "editor-hide-keyboard",
            )
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
    TooltipAction(
        tooltip = tooltip,
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else Color.Transparent,
            )
            .testTag("editor-toolbar-button"),
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

@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    descriptionRes: Int,
    tooltipRes: Int,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    TooltipAction(
        tooltip = stringResource(tooltipRes),
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .testTag("editor-toolbar-button"),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(descriptionRes),
            tint = contentColor,
        )
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
                                onUpdate { current -> (current as TitleBlock).copy(text = value) }
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
                                if (block.level <= 1) R.string.block_title_placeholder
                                else R.string.block_subtitle_placeholder,
                            ),
                            onFocus = onFocus,
                            requestFocus = focusRequest,
                            highlightQuery = highlightQuery,
                            highlightStyle = highlightStyle,
                            modifier = Modifier.padding(top = if (block.level <= 1) 14.dp else 10.dp),
                        )
                    }

                    is ParagraphBlock -> BlockTextField(
                        value = block.text,
                        onValueChange = { value ->
                            onUpdate { current -> (current as ParagraphBlock).copy(text = value) }
                        },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = stringResource(R.string.block_paragraph_placeholder),
                        onFocus = onFocus,
                        requestFocus = focusRequest,
                        highlightQuery = highlightQuery,
                        highlightStyle = highlightStyle,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )

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
                            onValueChange = { value ->
                                onUpdate { current -> (current as TaskBlock).copy(text = value) }
                            },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            placeholder = stringResource(R.string.block_task_placeholder),
                            onFocus = onFocus,
                            requestFocus = focusRequest,
                            onSubmit = onEnterNextTask,
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
                                    imageVector = if (block.expanded) {
                                        Icons.Rounded.ExpandLess
                                    } else {
                                        Icons.Rounded.ExpandMore
                                    },
                                    contentDescription = stringResource(
                                        if (block.expanded) R.string.cd_collapse
                                        else R.string.cd_expand,
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            BlockTextField(
                                value = block.title,
                                onValueChange = { value ->
                                    onUpdate { current ->
                                        (current as ToggleBlock).copy(title = value)
                                    }
                                },
                                textStyle = MaterialTheme.typography.titleMedium,
                                placeholder = stringResource(R.string.block_toggle_placeholder),
                                onFocus = onFocus,
                                requestFocus = focusRequest,
                                highlightQuery = highlightQuery,
                                highlightStyle = highlightStyle,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        AnimatedVisibility(visible = block.expanded) {
                            BlockTextField(
                                value = block.content,
                                onValueChange = { value ->
                                    onUpdate { current ->
                                        (current as ToggleBlock).copy(content = value)
                                    }
                                },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                placeholder = stringResource(R.string.block_toggle_content_placeholder),
                                onFocus = onFocus,
                                highlightQuery = highlightQuery,
                                highlightStyle = highlightStyle,
                                modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
                            )
                        }
                    }

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
                                onUpdate { current -> (current as QuoteBlock).copy(text = value) }
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontStyle = FontStyle.Italic,
                            ),
                            placeholder = stringResource(R.string.block_quote_placeholder),
                            onFocus = onFocus,
                            requestFocus = focusRequest,
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

            if (focused && block !is AttachmentBlock) {
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
    onSubmit: (() -> Unit)? = null,
) {
    val transformation = remember(highlightQuery, highlightStyle) {
        if (highlightQuery.isBlank()) {
            VisualTransformation.None
        } else {
            VisualTransformation { text ->
                TransformedText(
                    SearchText.highlight(text.text, highlightQuery, highlightStyle),
                    OffsetMapping.Identity,
                )
            }
        }
    }
    val requester = remember { BringIntoViewRequester() }
    val focusRequester = remember { FocusRequester() }
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    var cursorRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            val cursor = fieldValue.selection.start.coerceIn(0, value.length)
            fieldValue = TextFieldValue(value, TextRange(cursor))
        }
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
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = transformation,
        singleLine = onSubmit != null,
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
