package com.blackeye.kribo.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Clickable surface with a native Material tooltip shown on long press.
 *
 * Used by the editor formatting bar and the Home top bar so icon-only actions always explain
 * themselves without adding permanent text.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TooltipAction(
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(
                    text = tooltip,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        state = tooltipState,
    ) {
        Box(
            modifier = modifier.combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
                onLongClick = { scope.launch { tooltipState.show() } },
            ),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

@Composable
fun TooltipIconButton(
    icon: ImageVector,
    contentDescription: String,
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    testTag: String? = null,
) {
    val tintColor = if (tint == Color.Unspecified) LocalContentColor.current else tint
    val boxModifier = modifier
        .size(48.dp)
        .clip(RoundedCornerShape(999.dp))
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    TooltipAction(
        tooltip = tooltip,
        onClick = onClick,
        modifier = boxModifier,
        enabled = enabled,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tintColor else tintColor.copy(alpha = 0.38f),
        )
    }
}

@Composable
fun TooltipTextButton(
    text: String,
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    testTag: String? = null,
) {
    val textColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color
    TooltipAction(
        tooltip = tooltip,
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}
