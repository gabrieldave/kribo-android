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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackeye.kribo.BuildConfig
import com.blackeye.kribo.R
import com.blackeye.kribo.billing.BillingUiState
import com.blackeye.kribo.billing.ProLimits
import com.blackeye.kribo.ui.billing.PaywallReason
import com.blackeye.kribo.ui.billing.ProPaywallSheet
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
    isPro: Boolean,
    onBack: () -> Unit,
    onPurchase: () -> Unit,
    onRestorePurchases: (onResult: (Boolean) -> Unit) -> Unit,
    onLicenses: () -> Unit,
    onOpenWifi: () -> Unit,
    onOpenDrive: () -> Unit,
) {
    val context = LocalContext.current
    var connectionsExpanded by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var paywallReason by remember { mutableStateOf<PaywallReason?>(null) }
    var proRestoreMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val restoreFound = stringResource(R.string.paywall_restore_found)
    val restoreNotFound = stringResource(R.string.paywall_restore_not_found)
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
        CompactAppearanceCard(
            selectedTheme = selectedTheme,
            selectedLanguage = selectedLanguage,
            isPro = isPro,
            onThemeSelected = { uiPreferences.setTheme(it) },
            onLanguageSelected = { AppLanguage.apply(it) },
            onLockedTheme = { paywallReason = PaywallReason.THEME_LOCKED },
        )

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
                locked = !isPro,
                onClick = {
                    if (ProLimits.canExportBackup(isPro)) {
                        backupLauncher.launch("kribo-backup-${System.currentTimeMillis()}.zip")
                    } else {
                        paywallReason = PaywallReason.BACKUP_LOCKED
                    }
                },
            )
        }

        SectionLabel(stringResource(R.string.settings_section_pro))
        ProCard(
            billingState = billingState,
            isPro = isPro,
            onPurchase = onPurchase,
            onRestorePurchases = {
                onRestorePurchases { owned ->
                    proRestoreMessage = if (owned) restoreFound else restoreNotFound
                }
            },
            restoreMessage = proRestoreMessage,
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
                        locked = !isPro,
                        onClick = {
                            if (isPro) {
                                onOpenDrive()
                            } else {
                                paywallReason = PaywallReason.DRIVE_LOCKED
                            }
                        },
                    )
                    SettingsDivider()
                    ConnectionRow(
                        icon = Icons.Rounded.Wifi,
                        title = stringResource(R.string.settings_connection_wifi),
                        locked = !isPro,
                        onClick = {
                            if (ProLimits.canUseWifi(isPro)) {
                                onOpenWifi()
                            } else {
                                paywallReason = PaywallReason.WIFI_LOCKED
                            }
                        },
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
            SettingsDivider()
            AboutLinkRow(
                icon = Icons.Rounded.Description,
                title = stringResource(R.string.settings_terms_title),
                subtitle = stringResource(R.string.settings_terms_subtitle),
                onClick = { openTerms(context) },
            )
            SettingsDivider()
            AboutLinkRow(
                icon = Icons.Rounded.DeleteOutline,
                title = stringResource(R.string.settings_delete_account_title),
                subtitle = stringResource(R.string.settings_delete_account_subtitle),
                onClick = { openDeleteAccount(context) },
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

    paywallReason?.let { reason ->
        ProPaywallSheet(
            reason = reason,
            price = billingState.formattedPrice,
            onDismiss = { paywallReason = null },
            onPurchase = {
                paywallReason = null
                onPurchase()
            },
            onRestore = onRestorePurchases,
        )
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

private fun openPrivacyPolicy(context: Context) =
    openLegalPage(context, "/es/privacy", "/privacy")

private fun openTerms(context: Context) =
    openLegalPage(context, "/es/terms", "/terms")

private fun openDeleteAccount(context: Context) =
    openLegalPage(context, "/es/delete-account", "/delete-account")

private fun openLegalPage(context: Context, spanishPath: String, englishPath: String) {
    val language = AppLanguage.current()
    val spanish = language == AppLanguage.SPANISH ||
        (language == AppLanguage.SYSTEM && Locale.getDefault().language.equals("es", ignoreCase = true))
    val url = "https://kribo.iareal.net" + if (spanish) spanishPath else englishPath
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
    locked: Boolean = false,
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
            imageVector = if (locked) Icons.Rounded.Lock else Icons.Rounded.ChevronRight,
            contentDescription = if (locked) stringResource(R.string.cd_pro_feature) else null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun CompactAppearanceCard(
    selectedTheme: AppTheme,
    selectedLanguage: AppLanguage,
    isPro: Boolean,
    onThemeSelected: (AppTheme) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onLockedTheme: () -> Unit,
) {
    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .testTag("settings-appearance-card"),
        ) {
            Text(
                text = stringResource(R.string.settings_theme_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            CompactThemeGrid(
                selectedTheme = selectedTheme,
                isPro = isPro,
                onThemeSelected = onThemeSelected,
                onLockedTheme = onLockedTheme,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            CompactLanguageRow(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = onLanguageSelected,
            )
        }
    }
}

private val COMPACT_THEMES = listOf(
    AppTheme.PURE_LIGHT,
    AppTheme.WARM_PAPER,
    AppTheme.ROSE,
    AppTheme.AMBER,
    AppTheme.OBSIDIAN,
    AppTheme.AMOLED,
    AppTheme.EMERALD,
    AppTheme.OCEAN,
    AppTheme.VIOLET,
)

@Composable
private fun CompactThemeGrid(
    selectedTheme: AppTheme,
    isPro: Boolean,
    onThemeSelected: (AppTheme) -> Unit,
    onLockedTheme: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        COMPACT_THEMES.chunked(3).forEach { rowThemes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowThemes.forEach { theme ->
                    val locked = !isPro && theme in ProLimits.PRO_THEMES
                    ThemeSwatchButton(
                        theme = theme,
                        selected = theme == selectedTheme,
                        locked = locked,
                        onClick = {
                            if (locked) onLockedTheme() else onThemeSelected(theme)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatchButton(
    theme: AppTheme,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = Color(theme.previewBackground)
    val foreground = when (theme) {
        AppTheme.PURE_LIGHT -> Color(0xFF0D1117)
        AppTheme.WARM_PAPER -> Color(0xFF1C1917)
        AppTheme.ROSE -> Color(0xFF2B0B18)
        AppTheme.AMBER -> Color(0xFF2A1A05)
        AppTheme.OBSIDIAN, AppTheme.AMOLED,
        AppTheme.EMERALD, AppTheme.OCEAN, AppTheme.VIOLET,
        -> Color.White
    }
    val accent = Color(theme.previewAccent)
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(background)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                },
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("theme-${theme.id}"),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = theme.compactLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (locked) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = stringResource(R.string.cd_pro_feature),
                    tint = accent,
                    modifier = Modifier.size(15.dp),
                )
            } else if (selected) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun CompactLanguageRow(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppLanguage.entries.forEach { language ->
            LanguagePill(
                language = language,
                selected = language == selectedLanguage,
                onClick = { onLanguageSelected(language) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LanguagePill(
    language: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("language-${language.name.lowercase()}"),
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            },
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = language.compactLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AppTheme.compactLabel(): String = stringResource(
    when (this) {
        AppTheme.PURE_LIGHT -> R.string.theme_short_light
        AppTheme.WARM_PAPER -> R.string.theme_short_paper
        AppTheme.ROSE -> R.string.theme_short_rose
        AppTheme.AMBER -> R.string.theme_short_amber
        AppTheme.OBSIDIAN -> R.string.theme_short_obsidian
        AppTheme.AMOLED -> R.string.theme_short_amoled
        AppTheme.EMERALD -> R.string.theme_short_emerald
        AppTheme.OCEAN -> R.string.theme_short_ocean
        AppTheme.VIOLET -> R.string.theme_short_violet
    },
)

@Composable
private fun AppLanguage.compactLabel(): String = stringResource(
    when (this) {
        AppLanguage.SYSTEM -> R.string.language_auto_short
        AppLanguage.SPANISH -> R.string.language_spanish
        AppLanguage.ENGLISH -> R.string.language_english
    },
)

@Composable
private fun ProCard(
    billingState: BillingUiState,
    isPro: Boolean,
    onPurchase: () -> Unit,
    onRestorePurchases: () -> Unit,
    restoreMessage: String? = null,
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

            if (isPro) {
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
    }
}

@Composable
private fun ConnectionRow(
    icon: ImageVector,
    title: String,
    badge: String? = null,
    locked: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
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
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        when {
            locked -> Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.cd_pro_feature),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )

            badge != null -> StatusPill(text = badge)

            onClick != null -> Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
