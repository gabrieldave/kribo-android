package com.blackeye.kribo.ui.note

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blackeye.kribo.AppContainer
import com.blackeye.kribo.R
import com.blackeye.kribo.data.AttachmentEntity
import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.NoteBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import com.blackeye.kribo.data.blocks.ToggleBlock
import com.blackeye.kribo.ui.components.AppModal
import com.blackeye.kribo.ui.components.AppScreen
import com.blackeye.kribo.ui.components.StatusPill
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NoteEditorScreen(
    noteId: Long,
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: NoteEditorViewModel = viewModel(
        key = "note-editor-$noteId",
        factory = viewModelFactory {
            initializer { NoteEditorViewModel(container.noteRepository, noteId) }
        },
    )

    val blocks = viewModel.blocks
    val attachmentMap = remember(viewModel.attachments) {
        viewModel.attachments.associateBy { it.id }
    }
    val titleBlock = blocks.filterIsInstance<TitleBlock>()
        .firstOrNull { it.text.isNotBlank() }
        ?.text
    val screenTitle = titleBlock?.take(40) ?: stringResource(R.string.note_title_edit)

    var showExport by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf(false) }

    val exit: () -> Unit = { viewModel.flushAndExit(onBack) }
    BackHandler(onBack = exit)

    fun exportAndShare(pdf: Boolean) {
        showExport = false
        scope.launch {
            val file = runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    if (pdf) {
                        container.exportService.exportNotePdf(noteId, File(dir, "kribo-$noteId.pdf"))
                    } else {
                        container.exportService.exportNoteMarkdown(noteId, File(dir, "kribo-$noteId.md"))
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

    AppScreen(
        title = screenTitle,
        onBack = exit,
        scrollable = false,
        actions = {
            IconButton(
                onClick = viewModel::togglePinned,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = stringResource(R.string.cd_pin),
                    tint = if (viewModel.isPinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(
                onClick = { showExport = true },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = stringResource(R.string.cd_export),
                )
            }
        },
        footer = {
            EditorToolbar(onAdd = viewModel::addBlock)
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
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(items = blocks, key = { it.id }) { block ->
                    BlockRow(
                        block = block,
                        focused = viewModel.focusedBlockId == block.id,
                        attachment = (block as? AttachmentBlock)?.attachmentId?.let(attachmentMap::get),
                        onFocus = { viewModel.onFocus(block.id) },
                        onUpdate = { transform ->
                            viewModel.updateBlock(block.id, transform)
                        },
                        onDelete = { viewModel.removeBlock(block.id) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
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
}

@Composable
private fun EditorToolbar(onAdd: (NoteBlock) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarButton(Icons.Rounded.Title, R.string.cd_block_title) {
            onAdd(TitleBlock(level = 1))
        }
        ToolbarButton(Icons.Rounded.Title, R.string.cd_block_subtitle) {
            onAdd(TitleBlock(level = 2))
        }
        ToolbarButton(Icons.AutoMirrored.Rounded.Subject, R.string.cd_block_paragraph) {
            onAdd(ParagraphBlock())
        }
        ToolbarButton(Icons.Rounded.CheckBox, R.string.cd_block_task) {
            onAdd(TaskBlock())
        }
        ToolbarButton(Icons.Rounded.UnfoldMore, R.string.cd_block_toggle) {
            onAdd(ToggleBlock())
        }
        ToolbarButton(Icons.Rounded.FormatQuote, R.string.cd_block_quote) {
            onAdd(QuoteBlock())
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    descriptionRes: Int,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(descriptionRes),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BlockRow(
    block: NoteBlock,
    focused: Boolean,
    attachment: AttachmentEntity?,
    onFocus: () -> Unit,
    onUpdate: ((NoteBlock) -> NoteBlock) -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 6.dp),
                        )
                    }

                    is ToggleBlock -> Column(modifier = Modifier.fillMaxWidth()) {
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
                            modifier = Modifier.weight(1f),
                        )
                    }

                    is AttachmentBlock -> AttachmentCard(
                        block = block,
                        attachment = attachment,
                        onDelete = onDelete,
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
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { state -> if (state.isFocused) onFocus() },
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
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
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Column {
            val bitmap = rememberAttachmentBitmap(attachment?.filePath)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = block.fileName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = block.fileName.ifBlank { stringResource(R.string.attachment_scan) },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
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
                Text(
                    text = block.previewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun rememberAttachmentBitmap(path: String?): Bitmap? {
    val bitmap by produceState<Bitmap?>(initialValue = null, path) {
        value = if (path.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeSampled(path, maxLongSide = 900) }
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
