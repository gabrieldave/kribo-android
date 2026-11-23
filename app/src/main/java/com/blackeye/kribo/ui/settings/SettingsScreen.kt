package com.blackeye.kribo.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackeye.kribo.BuildConfig
import com.blackeye.kribo.R
import com.blackeye.kribo.billing.BillingUiState
import com.blackeye.kribo.ui.common.displayNameFor
import com.blackeye.kribo.ui.components.AppModal
import com.blackeye.kribo.ui.components.AppScreen
import com.blackeye.kribo.ui.components.StatusPill
import com.blackeye.kribo.ui.home.LibraryViewModel
import com.blackeye.kribo.ui.home.TransferPhase
import com.blackeye.kribo.ui.preferences.AppTheme
import com.blackeye.kribo.ui.preferences.UiPreferences
import java.util.Locale

@Composable
fun SettingsScreen(
    billingState: BillingUiState,
    library: LibraryViewModel,
    uiPreferences: UiPreferences,
    onBack: () -> Unit,
    onPurchase: () -> Unit,
    onRestorePurchases: () -> Unit,
    onLicenses: () -> Unit,
) {
    val context = LocalContext.current
    var connectionsExpanded by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val selectedLanguage = AppLanguage.current()
    val selectedTheme by uiPreferences.theme.collectAsStateWithLifecycle()
    val importState by library.importState.collectAsStateWithLifecycle()
    val backupState by library.backupState.collectAsStateWithLifecycle()

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val name = context.displayNameFor(uri)
            val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            if (stream != null) library.importFile(name, stream)
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) library.exportBackup(uri, context.contentResolver)
    }

    AppScreen(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
    ) {
        SectionLabel(stringResource(R.string.settings_section_appearance))
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(Icons.Rounded.Palette)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_theme_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_theme_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SettingsDivider()
            AppTheme.entries.forEachIndexed { index, theme ->
                if (index > 0) SettingsDivider()
                ThemeRow(
                    theme = theme,
                    selected = theme == selectedTheme,
                    onClick = { uiPreferences.setTheme(theme) },
                )
            }
        }

        SectionLabel(stringResource(R.string.settings_section_preferences))
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(Icons.Rounded.Language)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_language_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_language_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SettingsDivider()
            AppLanguage.entries.forEachIndexed { index, language ->
                if (index > 0) SettingsDivider()
                LanguageRow(
                    language = language,
                    selected = language == selectedLanguage,
                    onClick = { AppLanguage.apply(language) },
                )
            }
        }

        SectionLabel(stringResource(R.string.settings_section_data))
        SettingsCard {
            SettingsActionRow(
                icon = Icons.Rounded.FileOpen,
                title = stringResource(R.string.settings_import_title),
                subtitle = stringResource(R.string.settings_import_subtitle),
                onClick = { importLauncher.launch(arrayOf("*/*")) },
            )
            SettingsDivider()
            SettingsActionRow(
                icon = Icons.Rounded.Archive,
                title = stringResource(R.string.settings_backup_title),
                subtitle = stringResource(R.string.settings_backup_subtitle),
                onClick = {
                    backupLauncher.launch("kribo-backup-${System.currentTimeMillis()}.zip")
                },
            )
        }

        SectionLabel(stringResource(R.string.settings_section_pro))
        ProCard(
            billingState = billingState,
            onPurchase = onPurchase,
            onRestorePurchases = onRestorePurchases,
        )

        SectionLabel(stringResource(R.string.settings_section_integrations))
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clickable(role = Role.Button) { connectionsExpanded = !connectionsExpanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(Icons.Rounded.Tune)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_connections_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_connections_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (connectionsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(
                        if (connectionsExpanded) R.string.cd_collapse else R.string.cd_expand,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = connectionsExpanded) {
                Column {
                    SettingsDivider()
                    ConnectionRow(
                        icon = Icons.Rounded.Cloud,
                        title = stringResource(R.string.settings_connection_drive),
                    )
                    SettingsDivider()
                    ConnectionRow(
                        icon = Icons.Rounded.Wifi,
                        title = stringResource(R.string.settings_connection_wifi),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(role = Role.Button) { showAbout = true },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.settings_about_title),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showAbout) {
        AppModal(
            onDismissRequest = { showAbout = false },
            title = stringResource(R.string.settings_about_title),
            dismissLabel = stringResource(R.string.action_close),
        ) {
            Text(
                text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            AboutLinkRow(
                icon = Icons.Rounded.PrivacyTip,
                title = stringResource(R.string.settings_privacy_title),
                subtitle = stringResource(R.string.settings_privacy_subtitle),
                onClick = { openPrivacyPolicy(context) },
            )
            SettingsDivider()
            AboutLinkRow(
                icon = Icons.Rounded.Description,
                title = stringResource(R.string.settings_licenses_title),
                subtitle = stringResource(R.string.settings_licenses_subtitle),
                onClick = {
                    showAbout = false
                    onLicenses()
                },
            )
        }
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
            onDismissRequest = library::acknowledgeImport,
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
            onDismissRequest = library::acknowledgeImport,
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

    when (backupState.phase) {
        TransferPhase.RUNNING -> AppModal(
            onDismissRequest = {},
            title = stringResource(R.string.backup_running_title),
        ) {
            if (backupState.total > 0) {
                LinearProgressIndicator(
                    progress = { backupState.done.toFloat() / backupState.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    R.string.backup_progress,
                    backupState.done,
                    backupState.total,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TransferPhase.DONE -> AppModal(
            onDismissRequest = library::acknowledgeBackup,
            title = stringResource(R.string.backup_done_title),
            dismissLabel = stringResource(R.string.action_close),
        ) {
            Text(
                text = stringResource(R.string.backup_done_body, backupState.total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TransferPhase.ERROR -> AppModal(
            onDismissRequest = library::acknowledgeBackup,
            title = stringResource(R.string.backup_error_title),
            dismissLabel = stringResource(R.string.action_close),
        ) {
            Text(
                text = stringResource(R.string.backup_error_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TransferPhase.IDLE -> Unit
    }
}

@Composable
private fun AboutLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun openPrivacyPolicy(context: Context) {
    val language = AppLanguage.current()
    val spanish = language == AppLanguage.SPANISH ||
        (language == AppLanguage.SYSTEM && Locale.getDefault().language.equals("es", ignoreCase = true))
    val url = if (spanish) {
        "https://kribo.iareal.net/es/privacy"
    } else {
        "https://kribo.iareal.net/privacy"
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
    )
}

@Composable
private fun IconTile(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(13.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LanguageRow(
    language: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = language.label(),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        RadioButton(
            selected = selected,
            onClick = null,
        )
    }
}

@Composable
private fun AppLanguage.label(): String = stringResource(
    when (this) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.SPANISH -> R.string.language_spanish
        AppLanguage.ENGLISH -> R.string.language_english
    },
)

@Composable
private fun ThemeRow(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(theme.previewBackground))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(theme.previewAccent)),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = theme.label(),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        RadioButton(
            selected = selected,
            onClick = null,
        )
    }
}

@Composable
private fun AppTheme.label(): String = stringResource(
    when (this) {
        AppTheme.OBSIDIAN -> R.string.theme_obsidian
        AppTheme.WARM_PAPER -> R.string.theme_warm_paper
        AppTheme.AMOLED -> R.string.theme_amoled
        AppTheme.PURE_LIGHT -> R.string.theme_pure_light
    },
)

@Composable
private fun ProCard(
    billingState: BillingUiState,
    onPurchase: () -> Unit,
    onRestorePurchases: () -> Unit,
) {
    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Star)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_pro_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_pro_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (billingState.owned) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.settings_pro_active),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            } else {
                Button(
                    onClick = onPurchase,
                    enabled = billingState.formattedPrice != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    val label = when {
                        billingState.formattedPrice != null -> stringResource(
                            R.string.settings_pro_price,
                            billingState.formattedPrice,
                        )

                        billingState.connected -> stringResource(R.string.settings_pro_unavailable)
                        else -> stringResource(R.string.billing_store_unavailable)
                    }
                    Text(label)
                }
                TextButton(
                    onClick = onRestorePurchases,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.settings_pro_restore))
                }
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    icon: ImageVector,
    title: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        StatusPill(text = stringResource(R.string.settings_coming_soon))
    }
}
