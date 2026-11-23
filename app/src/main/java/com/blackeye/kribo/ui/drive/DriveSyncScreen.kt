package com.blackeye.kribo.ui.drive

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackeye.kribo.AppContainer
import com.blackeye.kribo.R
import com.blackeye.kribo.drive.DriveAuthOutcome
import com.blackeye.kribo.drive.DriveSyncError
import com.blackeye.kribo.drive.DriveSyncResult
import com.blackeye.kribo.ui.common.findActivity
import com.blackeye.kribo.ui.common.formatNoteDate
import com.blackeye.kribo.ui.components.AppModal
import com.blackeye.kribo.ui.components.AppScreen

enum class DriveSyncMessage {
    DONE,
    CANCELLED,
    NETWORK,
    AUTH,
    CONFIGURATION,
    UNKNOWN,
}

@Composable
fun DriveSyncScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val auth = container.driveAuthManager
    val manager = container.driveSyncManager
    val prefs = container.drivePrefs
    val scheduler = container.driveSyncScheduler
    val session by auth.session.collectAsStateWithLifecycle()

    var syncing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<DriveSyncMessage?>(null) }
    var autoSync by remember { mutableStateOf(prefs.autoSync) }
    var wifiOnly by remember { mutableStateOf(prefs.wifiOnly) }
    var pendingSync by remember { mutableStateOf(false) }
    var connectTrigger by remember { mutableIntStateOf(0) }
    var syncTrigger by remember { mutableIntStateOf(0) }
    var showDisconnect by remember { mutableStateOf(false) }

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val host = activity
        if (result.resultCode != Activity.RESULT_OK || result.data == null || host == null) {
            message = DriveSyncMessage.CANCELLED
            return@rememberLauncherForActivityResult
        }
        when (auth.completeAuthorization(host, result.data!!)) {
            DriveAuthOutcome.Connected -> {
                if (pendingSync) {
                    pendingSync = false
                    syncTrigger += 1
                }
            }
            DriveAuthOutcome.Cancelled -> message = DriveSyncMessage.CANCELLED
            DriveAuthOutcome.OAuthConfiguration -> message = DriveSyncMessage.CONFIGURATION
            else -> message = DriveSyncMessage.UNKNOWN
        }
    }

    LaunchedEffect(connectTrigger) {
        if (connectTrigger == 0) return@LaunchedEffect
        val host = activity
        if (host == null) {
            message = DriveSyncMessage.UNKNOWN
            return@LaunchedEffect
        }
        when (val outcome = auth.authorize(host)) {
            DriveAuthOutcome.Connected -> {
                message = null
                if (pendingSync) {
                    pendingSync = false
                    syncTrigger += 1
                }
            }
            is DriveAuthOutcome.Resolution -> authLauncher.launch(
                IntentSenderRequest.Builder(outcome.pendingIntent).build(),
            )
            DriveAuthOutcome.Cancelled -> message = DriveSyncMessage.CANCELLED
            DriveAuthOutcome.OAuthConfiguration -> message = DriveSyncMessage.CONFIGURATION
            else -> message = DriveSyncMessage.UNKNOWN
        }
    }

    LaunchedEffect(syncTrigger) {
        if (syncTrigger == 0) return@LaunchedEffect
        if (auth.validToken() == null) {
            pendingSync = true
            connectTrigger += 1
            return@LaunchedEffect
        }
        syncing = true
        message = null
        when (val result = manager.sync()) {
            is DriveSyncResult.Success -> message = DriveSyncMessage.DONE
            DriveSyncResult.AuthRequired -> {
                pendingSync = true
                connectTrigger += 1
                message = DriveSyncMessage.AUTH
            }
            is DriveSyncResult.Error -> {
                message = when (result.kind) {
                    DriveSyncError.NETWORK -> DriveSyncMessage.NETWORK
                    DriveSyncError.AUTH -> DriveSyncMessage.AUTH
                    DriveSyncError.OAUTH_CONFIGURATION -> DriveSyncMessage.CONFIGURATION
                    DriveSyncError.UNKNOWN -> DriveSyncMessage.UNKNOWN
                }
            }
        }
        syncing = false
    }

    val requestSync: () -> Unit = {
        if (auth.validToken() == null) {
            pendingSync = true
            connectTrigger += 1
        } else {
            syncTrigger += 1
        }
    }

    AppScreen(
        title = stringResource(R.string.drive_sync_title),
        onBack = onBack,
    ) {
        DriveSyncContent(
            connected = session.connected,
            email = session.email,
            lastSyncAt = session.lastSyncAt,
            notes = session.lastNotes,
            attachments = session.lastAttachments,
            syncing = syncing,
            autoSync = autoSync,
            wifiOnly = wifiOnly,
            message = message,
            onConnect = { connectTrigger += 1 },
            onSync = requestSync,
            onAutoChange = {
                autoSync = it
                prefs.autoSync = it
                scheduler.update()
            },
            onWifiChange = {
                wifiOnly = it
                prefs.wifiOnly = it
                if (autoSync) scheduler.update()
            },
            onDisconnect = { showDisconnect = true },
        )
    }

    if (showDisconnect) {
        AppModal(
            onDismissRequest = { showDisconnect = false },
            title = stringResource(R.string.drive_disconnect_title),
            dismissLabel = stringResource(R.string.action_cancel),
            confirmLabel = stringResource(R.string.drive_disconnect),
            onConfirm = {
                showDisconnect = false
                autoSync = false
                prefs.autoSync = false
                scheduler.update()
                auth.disconnect()
                message = null
            },
        ) {
            Text(
                text = stringResource(R.string.drive_disconnect_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun DriveSyncContent(
    connected: Boolean,
    email: String?,
    lastSyncAt: Long,
    notes: Int,
    attachments: Int,
    syncing: Boolean,
    autoSync: Boolean,
    wifiOnly: Boolean,
    message: DriveSyncMessage?,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onAutoChange: (Boolean) -> Unit,
    onWifiChange: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("drive-sync-content"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.drive_privacy_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!connected) {
            Button(
                onClick = onConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("drive-connect"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.drive_connect),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.drive_connected_account),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = email?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.drive_account_google),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Text(
                text = if (lastSyncAt > 0L) {
                    stringResource(R.string.drive_last_sync, formatNoteDate(lastSyncAt))
                } else {
                    stringResource(R.string.drive_never_synced)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.drive_sync_counts, notes, attachments),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onSync,
                enabled = !syncing,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("drive-sync-now"),
            ) {
                if (syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (syncing) R.string.drive_syncing else R.string.drive_sync_now,
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SwitchRow(
                icon = Icons.Rounded.Sync,
                title = stringResource(R.string.drive_auto_sync),
                subtitle = stringResource(R.string.drive_auto_sync_body),
                checked = autoSync,
                tag = "drive-auto",
                onCheckedChange = onAutoChange,
            )
            SwitchRow(
                icon = Icons.Rounded.Cloud,
                title = stringResource(R.string.drive_wifi_only),
                subtitle = stringResource(R.string.drive_wifi_only_body),
                checked = wifiOnly,
                tag = "drive-wifi",
                onCheckedChange = onWifiChange,
            )
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("drive-disconnect"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.LinkOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.drive_disconnect),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        message?.let {
            Text(
                text = stringResource(messageText(it)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (it == DriveSyncMessage.DONE) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
private fun SwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
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
                .testTag(tag),
            contentAlignment = Alignment.Center,
        ) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun messageText(message: DriveSyncMessage): Int = when (message) {
    DriveSyncMessage.DONE -> R.string.drive_message_done
    DriveSyncMessage.CANCELLED -> R.string.drive_message_cancelled
    DriveSyncMessage.NETWORK -> R.string.drive_message_network
    DriveSyncMessage.AUTH -> R.string.drive_message_auth
    DriveSyncMessage.CONFIGURATION -> R.string.drive_message_config
    DriveSyncMessage.UNKNOWN -> R.string.drive_message_unknown
}
