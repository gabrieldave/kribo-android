package com.blackeye.kribo.ui.transfer

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackeye.kribo.AppContainer
import com.blackeye.kribo.R
import com.blackeye.kribo.transfer.QrCode
import com.blackeye.kribo.transfer.WifiTransferServer
import com.blackeye.kribo.ui.components.AppScreen
import com.blackeye.kribo.ui.components.TooltipIconButton
import com.blackeye.kribo.ui.onboarding.GuideTopic
import com.blackeye.kribo.ui.onboarding.GuideTopicModal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WifiTransferScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val server = container.wifiTransferServer
    val state by server.state.collectAsStateWithLifecycle()
    var copied by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { server.stop() }
    }

    val url = if (state.running && !state.ip.isNullOrBlank()) {
        "http://${state.ip}:${state.port}/?pin=${state.pin}"
    } else {
        null
    }
    val qr by produceState<Bitmap?>(initialValue = null, url) {
        value = url?.let { content ->
            withContext(Dispatchers.Default) { QrCode.generate(content) }
        }
    }

    AppScreen(
        title = stringResource(R.string.wifi_transfer_title),
        onBack = {
            server.stop()
            onBack()
        },
        actions = {
            TooltipIconButton(
                icon = Icons.AutoMirrored.Rounded.HelpOutline,
                contentDescription = stringResource(R.string.cd_help),
                tooltip = stringResource(R.string.cd_help),
                onClick = { showHelp = true },
                testTag = "wifi-help",
            )
        },
    ) {
        WifiTransferContent(
            state = state,
            url = url,
            qrBitmap = qr,
            copied = copied,
            onToggle = {
                if (state.running) {
                    server.stop()
                } else {
                    scope.launch(Dispatchers.IO) { server.start() }
                }
            },
            onCopy = {
                url?.let { value ->
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Kribo", value))
                    copied = true
                }
            },
        )
    }

    if (showHelp) {
        WifiHelpModal(onDismiss = { showHelp = false })
    }
}

@Composable
internal fun WifiTransferContent(
    state: WifiTransferServer.State,
    url: String?,
    qrBitmap: Bitmap?,
    copied: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wifi-transfer-content"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (state.running) R.string.wifi_status_active
                            else R.string.wifi_status_stopped,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            if (state.running) R.string.wifi_stop else R.string.wifi_start,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("wifi-toggle"),
                    contentAlignment = Alignment.Center,
                ) {
                    Switch(
                        checked = state.running,
                        onCheckedChange = { onToggle() },
                    )
                }
            }
        }

        if (state.startFailed) {
            Text(
                text = stringResource(R.string.wifi_start_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (!state.wifi) {
            Text(
                text = stringResource(R.string.wifi_no_wifi),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.running && url != null) {
            Text(
                text = stringResource(R.string.wifi_url_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                SelectionContainer {
                    Text(
                        text = url,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Button(
                onClick = onCopy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("wifi-copy"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (copied) R.string.wifi_copied else R.string.wifi_copy_url,
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.wifi_pin_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = state.pin,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                qrBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.wifi_scan_qr),
                        modifier = Modifier
                            .size(150.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Text(
                text = stringResource(R.string.wifi_scan_qr),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = stringResource(R.string.wifi_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.wifi_security_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WifiHelpModal(onDismiss: () -> Unit) {
    GuideTopicModal(topic = GuideTopic.WIFI_TRANSFER, onDismiss = onDismiss)
}
