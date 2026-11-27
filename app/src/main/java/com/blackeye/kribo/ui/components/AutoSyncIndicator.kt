package com.blackeye.kribo.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blackeye.kribo.R
import com.blackeye.kribo.drive.AutoSyncUiState

/**
 * Discrete, fixed-size Drive auto-sync indicator that never shifts surrounding layout
 * when transitioning between IDLE, SYNCING and SYNCED.
 */
@Composable
fun AutoSyncStatusIcon(
    state: AutoSyncUiState,
    modifier: Modifier = Modifier,
) {
    val syncingDesc = stringResource(R.string.cd_auto_sync_syncing)
    val syncedDesc = stringResource(R.string.cd_auto_sync_synced)
    Box(
        modifier = modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = state, label = "auto-sync-status") { current ->
            when (current) {
                AutoSyncUiState.SYNCING -> CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .semantics { contentDescription = syncingDesc },
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )

                AutoSyncUiState.SYNCED -> Icon(
                    imageVector = Icons.Rounded.CloudDone,
                    contentDescription = syncedDesc,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )

                AutoSyncUiState.IDLE -> Unit
            }
        }
    }
}
