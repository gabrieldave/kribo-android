package com.blackeye.kribo.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.blackeye.kribo.R
import com.blackeye.kribo.data.export.AiPreset
import com.blackeye.kribo.data.export.NoteExportPayload
import com.blackeye.kribo.data.export.isSpanishContext
import com.blackeye.kribo.data.export.prompt
import com.blackeye.kribo.data.export.shareNotesToAi

private data class PresetUi(
    val preset: AiPreset,
    val icon: ImageVector,
    val titleRes: Int,
    val subtitleRes: Int,
    val testTag: String,
)

private val PRESETS = listOf(
    PresetUi(
        preset = AiPreset.SUMMARY,
        icon = Icons.Rounded.Summarize,
        titleRes = R.string.ai_preset_summary_title,
        subtitleRes = R.string.ai_preset_summary_subtitle,
        testTag = "ai-preset-summary",
    ),
    PresetUi(
        preset = AiPreset.INFOGRAPHIC,
        icon = Icons.Rounded.BarChart,
        titleRes = R.string.ai_preset_infographic_title,
        subtitleRes = R.string.ai_preset_infographic_subtitle,
        testTag = "ai-preset-infographic",
    ),
    PresetUi(
        preset = AiPreset.HTML,
        icon = Icons.Rounded.Code,
        titleRes = R.string.ai_preset_html_title,
        subtitleRes = R.string.ai_preset_html_subtitle,
        testTag = "ai-preset-html",
    ),
    PresetUi(
        preset = AiPreset.ACTION_PLAN,
        icon = Icons.Rounded.TaskAlt,
        titleRes = R.string.ai_preset_plan_title,
        subtitleRes = R.string.ai_preset_plan_subtitle,
        testTag = "ai-preset-plan",
    ),
)

/**
 * #43: "Send to your AI" sheet. One tap attaches the combined `.txt` and copies a ready
 * prompt; the custom field and "file only" cover the remaining cases. Theme adaptive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiShareSheet(
    notes: List<NoteExportPayload>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isSpanish = remember(context) { isSpanishContext(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customPrompt by rememberSaveable { mutableStateOf("") }

    fun send(promptText: String?) {
        val ok = shareNotesToAi(context, notes, promptText)
        if (!ok) {
            Toast.makeText(context, context.getString(R.string.ai_share_error), Toast.LENGTH_SHORT)
                .show()
        }
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("ai-share-sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ai_share_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = if (notes.size == 1) {
                            stringResource(R.string.ai_share_badge_one)
                        } else {
                            stringResource(R.string.ai_share_badge_many, notes.size)
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = stringResource(R.string.ai_share_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PRESETS.forEach { item ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { send(item.preset.prompt(isSpanish)) }
                        .testTag(item.testTag),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(item.titleRes),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(item.subtitleRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = customPrompt,
                onValueChange = { customPrompt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai-share-custom"),
                placeholder = { Text(stringResource(R.string.ai_share_custom_hint)) },
                minLines = 1,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
            )
            if (customPrompt.isNotBlank()) {
                TextButton(
                    onClick = { send(customPrompt.trim()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("ai-share-custom-send"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.ai_share_send_custom),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            OutlinedButton(
                onClick = { send(null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("ai-share-file-only"),
            ) {
                Text(
                    text = stringResource(R.string.ai_share_file_only),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
