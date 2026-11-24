package com.blackeye.kribo.ui.billing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackeye.kribo.R
import com.blackeye.kribo.ui.components.AppModal

@Composable
fun ProPaywallSheet(
    reason: PaywallReason,
    price: String?,
    onDismiss: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: (onResult: (Boolean) -> Unit) -> Unit,
) {
    var restoreMessage by remember(reason) { mutableStateOf<String?>(null) }
    val restored = stringResource(R.string.paywall_restore_found)
    val notFound = stringResource(R.string.paywall_restore_not_found)

    AppModal(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.paywall_pro_title),
        dismissLabel = stringResource(R.string.action_close),
    ) {
        ProPaywallContent(
            reason = reason,
            price = price,
            restoreMessage = restoreMessage,
            onPurchase = onPurchase,
            onRestore = {
                onRestore { owned ->
                    restoreMessage = if (owned) restored else notFound
                }
            },
        )
    }
}

@Composable
internal fun ProPaywallContent(
    reason: PaywallReason,
    price: String?,
    restoreMessage: String?,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
) {
    val benefits = listOf(
        Triple(Icons.Rounded.Cloud, R.string.paywall_benefit_drive, true),
        Triple(Icons.Rounded.Wifi, R.string.paywall_benefit_wifi, true),
        Triple(Icons.Rounded.DocumentScanner, R.string.paywall_benefit_ocr, false),
        Triple(Icons.Rounded.Palette, R.string.paywall_benefit_themes, false),
        Triple(Icons.Rounded.Folder, R.string.paywall_benefit_folders, false),
        Triple(Icons.Rounded.PhotoLibrary, R.string.paywall_benefit_photos, false),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pro-paywall-content"),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(reasonText(reason)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.paywall_pro_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        // Drive backup and Wi-Fi transfer lead the list and get the accent treatment.
        benefits.forEach { (icon, text, highlighted) ->
            BenefitRow(icon, text, highlighted)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onPurchase,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("pro-paywall-cta"),
        ) {
            Text(
                text = price?.let { stringResource(R.string.paywall_cta_price, it) }
                    ?: stringResource(R.string.paywall_cta),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = onRestore,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("pro-paywall-restore"),
        ) {
            Text(
                text = stringResource(R.string.settings_pro_restore),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (restoreMessage != null) {
            Text(
                text = restoreMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BenefitRow(
    icon: ImageVector,
    textRes: Int,
    highlighted: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier
                        .padding(vertical = 3.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 8.dp)
                } else {
                    Modifier
                },
            )
            .heightIn(min = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (highlighted) FontWeight.SemiBold else null,
        )
    }
}

private fun reasonText(reason: PaywallReason): Int = when (reason) {
    PaywallReason.OCR_LIMIT -> R.string.paywall_reason_ocr
    PaywallReason.FOLDERS_LIMIT -> R.string.paywall_reason_folders
    PaywallReason.PHOTO_LIMIT -> R.string.paywall_reason_photo
    PaywallReason.THEME_LOCKED -> R.string.paywall_reason_theme
    PaywallReason.BACKUP_LOCKED -> R.string.paywall_reason_backup
    PaywallReason.WIFI_LOCKED -> R.string.paywall_reason_wifi
    PaywallReason.DRIVE_LOCKED -> R.string.paywall_reason_drive
    PaywallReason.GENERIC -> R.string.paywall_reason_generic
}
