package com.blackeye.kribo.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.blackeye.kribo.data.blocks.TitleBlock
import com.blackeye.kribo.ui.billing.PaywallReason
import com.blackeye.kribo.ui.billing.ProPaywallContent
import com.blackeye.kribo.ui.drive.DriveSyncContent
import com.blackeye.kribo.ui.drive.DriveSyncMessage
import com.blackeye.kribo.ui.home.BulkSelectionBar
import com.blackeye.kribo.ui.home.QuickCaptureBar
import com.blackeye.kribo.ui.preferences.AppTheme
import com.blackeye.kribo.ui.preferences.VoiceEngineMode
import com.blackeye.kribo.ui.settings.AppLanguage
import com.blackeye.kribo.ui.settings.CompactAppearanceCard
import com.blackeye.kribo.ui.settings.KriboWebSettingsRow
import com.blackeye.kribo.ui.settings.VoiceDictationSettingsCard
import com.blackeye.kribo.voice.VoiceModelDownloadState
import com.blackeye.kribo.voice.VoiceModelLanguage
import com.blackeye.kribo.ui.transfer.WifiTransferContent
import com.blackeye.kribo.transfer.WifiTransferServer
import com.blackeye.kribo.ui.note.AttachmentActionsRow
import com.blackeye.kribo.ui.note.EditorFormatBar
import com.blackeye.kribo.ui.note.EditorToolbar
import com.blackeye.kribo.data.blocks.ActiveStyles
import com.blackeye.kribo.ui.theme.KriboTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Anti-Squish UI Gate at 360 dp in Spanish and English, at 100% and 200% font scale.
 * Fails if a button/chip collapses to a narrow column or grows past 1.8 normal lines.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LayoutOverflowTest {

    @get:Rule
    val compose = createComposeRule()

    private data class UiConfig(val locale: Locale, val fontScale: Float)

    private val baseContext: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun ocrCardActionsNeverSquishInEitherLanguage() {
        var config by mutableStateOf(UiConfig(Locale("es"), 1f))
        compose.setContent {
            val context = remember(config.locale) { localized(baseContext, config.locale) }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(TEST_DENSITY, config.fontScale),
            ) {
                KriboTheme {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        AttachmentActionsRow(
                            expanded = false,
                            copied = false,
                            onToggleExpand = {},
                            onCopy = {},
                            onEdit = {},
                        )
                    }
                }
            }
        }

        for (fontScale in listOf(1f, 2f)) {
            for (locale in listOf(Locale("es"), Locale("en"))) {
                compose.runOnIdle { config = UiConfig(locale, fontScale) }
                compose.waitForIdle()
                assertControlFits("attachment-action-view", fontScale)
                assertControlFits("attachment-action-copy", fontScale)
                assertControlFits("attachment-action-edit", fontScale)
            }
        }
    }

    @Test
    fun bulkSelectionBarFits360DpInBothLanguages() {
        var config by mutableStateOf(UiConfig(Locale("es"), 1f))
        compose.setContent {
            val context = remember(config.locale) { localized(baseContext, config.locale) }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(TEST_DENSITY, config.fontScale),
            ) {
                KriboTheme {
                    Box(
                        modifier = Modifier
                            .width(FOOTER_LANE_DP.dp)
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        BulkSelectionBar(
                            anyPinned = false,
                            onMove = {},
                            onPin = {},
                            onCopy = {},
                            onShareToAi = {},
                            onDelete = {},
                        )
                    }
                }
            }
        }

        val lanePx = FOOTER_LANE_DP * TEST_DENSITY
        val tags = listOf(
            "home-bulk-move",
            "home-bulk-pin",
            "home-bulk-copy",
            "home-bulk-ai",
            "home-bulk-delete",
        )
        for (fontScale in listOf(1f, 2f)) {
            for (locale in listOf(Locale("es"), Locale("en"))) {
                compose.runOnIdle { config = UiConfig(locale, fontScale) }
                compose.waitForIdle()
                tags.forEach { tag ->
                    assertControlFits(tag, fontScale)
                    val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true)
                        .fetchSemanticsNode().boundsInRoot
                    assertTrue(
                        "$tag overflows lane at scale $fontScale (${locale.language}): ${bounds.right} > $lanePx",
                        bounds.left >= 0f && bounds.right <= lanePx + 1f,
                    )
                }
            }
        }
    }

    @Test
    fun editorToolbarAndQuickCaptureFit360Dp() {
        var config by mutableStateOf(UiConfig(Locale("es"), 1f))
        compose.setContent {
            val context = remember(config.locale) { localized(baseContext, config.locale) }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(TEST_DENSITY, config.fontScale),
            ) {
                KriboTheme {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        Column {
                            EditorToolbar(
                                onAdd = {},
                                onAttachImage = {},
                                activeBlock = TitleBlock(text = "Título", level = 1),
                            )
                            QuickCaptureBar(
                                text = "Texto de prueba con varias palabras",
                                onTextChange = {},
                                onSend = {},
                                onOpenEditor = {},
                                onScan = {},
                                freeScansLeft = 2,
                                largeFont = false,
                            )
                        }
                    }
                }
            }
        }

        for (fontScale in listOf(1f, 2f)) {
            for (locale in listOf(Locale("es"), Locale("en"))) {
                compose.runOnIdle { config = UiConfig(locale, fontScale) }
                compose.waitForIdle()
                val toolbarButtons = compose.onAllNodesWithTag("editor-toolbar-button", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                assertEquals("Expected Aa, voice mic plus the ten block actions", 12, toolbarButtons.size)
                assertControlFits("toolbar-format", fontScale, maxHeightDp = 60f)
                assertControlFits("toolbar-mic", fontScale, maxHeightDp = 60f)
                assertControlFits("toolbar-h1", fontScale, maxHeightDp = 60f)
                assertControlFits("toolbar-h2", fontScale, maxHeightDp = 60f)
                assertControlFits("toolbar-olist", fontScale, maxHeightDp = 60f)
                assertControlFits("toolbar-ulist", fontScale, maxHeightDp = 60f)
                toolbarButtons.forEachIndexed { index, node ->
                    val bounds = node.boundsInRoot
                    val heightDp = bounds.height / TEST_DENSITY
                    val widthDp = bounds.width / TEST_DENSITY
                    // The 12-action strip scrolls smoothly at 44dp/cell; off-screen cells clip in
                    // semantics bounds, while visible leading cells must never collapse or squish.
                    if (index < 6) {
                        assertTrue("Squished toolbar button at scale $fontScale: $heightDp", heightDp <= 60f)
                        assertTrue("Collapsed toolbar button at scale $fontScale: $widthDp", widthDp >= 40f)
                    }
                }
                assertControlFits("home-quick-send", fontScale, maxHeightDp = 64f)
                assertControlFits("home-quick-scan", fontScale, maxHeightDp = 64f)
                assertControlFits("home-quick-editor", fontScale, maxHeightDp = 64f)
                val field = compose.onNodeWithTag("home-quick-capture-field")
                    .fetchSemanticsNode().boundsInRoot
                assertTrue(
                    "Quick capture field collapsed at scale $fontScale",
                    field.width / TEST_DENSITY >= 80f,
                )
            }
        }
    }

    @Test
    fun editorToolbarScrollsAllTwelveActionsAt360DpInBothLanguages() {
        // Real footer lane on a 360dp phone: 360 minus AppScreen's 20dp side margins (320dp).
        var config by mutableStateOf(UiConfig(Locale("es"), 1f))
        compose.setContent {
            val context = remember(config.locale) { localized(baseContext, config.locale) }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(TEST_DENSITY, config.fontScale),
            ) {
                KriboTheme {
                    Box(
                        modifier = Modifier
                            .width(FOOTER_LANE_DP.dp)
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        EditorToolbar(
                            onAdd = {},
                            onAttachImage = {},
                            activeBlock = null,
                        )
                    }
                }
            }
        }

        for (fontScale in listOf(1f, 2f)) {
            for (locale in listOf(Locale("es"), Locale("en"))) {
                compose.runOnIdle { config = UiConfig(locale, fontScale) }
                compose.waitForIdle()

                val actions = compose
                    .onAllNodesWithTag("editor-toolbar-button", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                assertEquals("Expected Aa, voice mic plus the ten block actions", 12, actions.size)

                listOf(
                    "toolbar-format",
                    "toolbar-mic",
                    "toolbar-h1",
                    "toolbar-h2",
                    "toolbar-olist",
                    "toolbar-ulist",
                ).forEach { tag ->
                    compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
                    compose.waitForIdle()
                    assertControlFits(tag, fontScale, maxHeightDp = 60f)
                }

                // Trailing actions remain reachable via smooth horizontal scroll without squishing.
                listOf("toolbar-toggle", "toolbar-link", "toolbar-image").forEach { tag ->
                    compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
                    compose.waitForIdle()
                    assertControlFits(tag, fontScale, maxHeightDp = 60f)
                }
            }
        }
    }

    @Test
    fun dictationListeningBannerFits360DpInBothLanguages() {
        compose.mainClock.autoAdvance = false
        var config by mutableStateOf(UiConfig(Locale("es"), 1f))
        compose.setContent {
            val context = remember(config.locale) { localized(baseContext, config.locale) }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(TEST_DENSITY, config.fontScale),
            ) {
                KriboTheme {
                    Box(
                        modifier = Modifier
                            .width(FOOTER_LANE_DP.dp)
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        EditorToolbar(
                            onAdd = {},
                            onAttachImage = {},
                            activeBlock = null,
                            isDictating = true,
                            dictationPartial = "Dictando una nota larga de prueba",
                        )
                    }
                }
            }
        }

        for (fontScale in listOf(1f, 2f)) {
            for (locale in listOf(Locale("es"), Locale("en"))) {
                config = UiConfig(locale, fontScale)
                compose.mainClock.advanceTimeBy(100L)
                assertControlFits("dictation-listening-banner", fontScale, maxHeightDp = 72f)
                assertControlFits("dictation-stop", fontScale, maxHeightDp = 64f)
                assertControlFits("toolbar-mic", fontScale, maxHeightDp = 60f)
            }
        }
    }

    @Test
    fun editorFormatBarFits360Dp() {
        compose.setContent {
            KriboTheme {
                Box(
                    modifier = Modifier
                        .width(FOOTER_LANE_DP.dp)
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    EditorFormatBar(
                        onBack = {},
                        onToggleFormatMode = {},
                        activeStyles = ActiveStyles(bold = true),
                        noteFont = com.blackeye.kribo.data.NoteFont.SERIF,
                        onToggleStyle = { _, _ -> },
                        onClearFormatting = {},
                        onSetFont = {},
                        darkTheme = false,
                    )
                }
            }
        }
        compose.waitForIdle()

        listOf("format-back", "format-bold", "format-italic", "format-underline",
            "format-strike", "format-color", "format-highlight", "format-font", "format-clear",
        ).forEach { tag -> assertControlFits(tag, 1f) }
    }

    @Test
    fun compactAppearanceCardFits360DpInBothLanguages() {
        var config by mutableStateOf(UiConfig(Locale("es"), 1f))
        compose.setContent {
            val context = remember(config.locale) { localized(baseContext, config.locale) }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(TEST_DENSITY, config.fontScale),
            ) {
                KriboTheme {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        CompactAppearanceCard(
                            selectedTheme = AppTheme.OBSIDIAN,
                            selectedLanguage = AppLanguage.SYSTEM,
                            isPro = false,
                            onThemeSelected = {},
                            onLanguageSelected = {},
                            onLockedTheme = {},
                        )
                    }
                }
            }
        }

        val controls = listOf(
            "theme-pure_light",
            "theme-warm_paper",
            "theme-rose",
            "theme-amber",
            "theme-obsidian",
            "theme-amoled",
            "theme-emerald",
            "theme-ocean",
            "theme-violet",
            "language-system",
            "language-spanish",
            "language-english",
        )
        for (fontScale in listOf(1f, 2f)) {
            for (locale in listOf(Locale("es"), Locale("en"))) {
                compose.runOnIdle { config = UiConfig(locale, fontScale) }
                compose.waitForIdle()
                if (fontScale == 1f) {
                    val card = compose.onNodeWithTag("settings-appearance-card")
                        .fetchSemanticsNode().boundsInRoot
                    val cardHeightDp = card.height / TEST_DENSITY
                    assertTrue(
                        "Appearance card is ${cardHeightDp}dp, expected <= 360dp for 9 themes",
                        cardHeightDp <= 360f,
                    )
                }
                controls.forEach { tag -> assertControlFits(tag, fontScale) }
            }
        }
    }

    @Test
    fun proPaywallFits360Dp() {
        var config by mutableStateOf(UiConfig(Locale("es"), 1f))
        compose.setContent {
            val context = remember(config.locale) { localized(baseContext, config.locale) }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(TEST_DENSITY, config.fontScale),
            ) {
                KriboTheme {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        ProPaywallContent(
                            reason = PaywallReason.OCR_LIMIT,
                            price = "Precio local",
                            restoreMessage = null,
                            onPurchase = {},
                            onRestore = {},
                        )
                    }
                }
            }
        }
        for (fontScale in listOf(1f, 2f)) {
            for (locale in listOf(Locale("es"), Locale("en"))) {
                compose.runOnIdle { config = UiConfig(locale, fontScale) }
                compose.waitForIdle()
                assertControlFits("pro-paywall-cta", fontScale)
                assertControlFits("pro-paywall-restore", fontScale)
            }
        }
    }

    @Test
    fun wifiTransferFits360Dp() {
        var config by mutableStateOf(UiConfig(Locale("es"), 1f))
        val wifiState = WifiTransferServer.State(
            running = true,
            port = 8765,
            pin = "123456",
            ip = "192.168.1.45",
            wifi = true,
        )
        compose.setContent {
            val context = remember(config.locale) { localized(baseContext, config.locale) }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(TEST_DENSITY, config.fontScale),
            ) {
                KriboTheme {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        WifiTransferContent(
                            state = wifiState,
                            url = "http://192.168.1.45:8765/?pin=123456",
                            qrBitmap = null,
                            copied = false,
                            onToggle = {},
                            onCopy = {},
                        )
                    }
                }
            }
        }
        for (fontScale in listOf(1f, 2f)) {
            for (locale in listOf(Locale("es"), Locale("en"))) {
                compose.runOnIdle { config = UiConfig(locale, fontScale) }
                compose.waitForIdle()
                assertControlFits("wifi-toggle", fontScale)
                assertControlFits("wifi-copy", fontScale)
            }
        }
    }

    @Test
    fun driveSyncDisconnectedFits360Dp() {
        var config by mutableStateOf(UiConfig(Locale("es"), 1f))
        compose.setContent {
            val context = remember(config.locale) { localized(baseContext, config.locale) }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(TEST_DENSITY, config.fontScale),
            ) {
                KriboTheme {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        DriveSyncContent(
                            connected = false,
                            email = null,
                            lastSyncAt = 0L,
                            notes = 0,
                            attachments = 0,
                            syncing = false,
                            autoSync = false,
                            wifiOnly = true,
                            message = null,
                            onConnect = {},
                            onSync = {},
                            onAutoChange = {},
                            onWifiChange = {},
                            onDisconnect = {},
                        )
                    }
                }
            }
        }
        for (fontScale in listOf(1f, 2f)) {
            for (locale in listOf(Locale("es"), Locale("en"))) {
                compose.runOnIdle { config = UiConfig(locale, fontScale) }
                compose.waitForIdle()
                assertControlFits("drive-connect", fontScale)
                assertControlFits("drive-web-url", fontScale)
                assertControlFits("drive-web-copy", fontScale)
                assertControlFits("drive-web-send", fontScale)
            }
        }
    }

    @Test
    fun driveSyncConnectedFits360Dp() {
        var config by mutableStateOf(UiConfig(Locale("es"), 1f))
        compose.setContent {
            val context = remember(config.locale) { localized(baseContext, config.locale) }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(TEST_DENSITY, config.fontScale),
            ) {
                KriboTheme {
                    Column(
                        modifier = Modifier
                            .width(360.dp)
                            .verticalScroll(rememberScrollState())
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        DriveSyncContent(
                            connected = true,
                            email = "cuenta@example.com",
                            lastSyncAt = 1_700_000_000_000L,
                            notes = 12,
                            attachments = 4,
                            syncing = false,
                            autoSync = true,
                            wifiOnly = true,
                            message = DriveSyncMessage.DONE,
                            onConnect = {},
                            onSync = {},
                            onAutoChange = {},
                            onWifiChange = {},
                            onDisconnect = {},
                        )
                    }
                }
            }
        }
        for (fontScale in listOf(1f, 2f)) {
            for (locale in listOf(Locale("es"), Locale("en"))) {
                compose.runOnIdle { config = UiConfig(locale, fontScale) }
                compose.waitForIdle()
                compose.onNodeWithTag("drive-sync-now", useUnmergedTree = true).performScrollTo()
                assertControlFits("drive-sync-now", fontScale)
                compose.onNodeWithTag("drive-auto", useUnmergedTree = true).performScrollTo()
                assertControlFits("drive-auto", fontScale)
                compose.onNodeWithTag("drive-wifi", useUnmergedTree = true).performScrollTo()
                assertControlFits("drive-wifi", fontScale)
                compose.onNodeWithTag("drive-disconnect", useUnmergedTree = true).performScrollTo()
                assertControlFits("drive-disconnect", fontScale)
                compose.onNodeWithTag("drive-web-url", useUnmergedTree = true).performScrollTo()
                assertControlFits("drive-web-url", fontScale)
                compose.onNodeWithTag("drive-web-copy", useUnmergedTree = true).performScrollTo()
                assertControlFits("drive-web-copy", fontScale)
                compose.onNodeWithTag("drive-web-send", useUnmergedTree = true).performScrollTo()
                assertControlFits("drive-web-send", fontScale)
            }
        }
    }

    @Test
    fun settingsKriboWebRowFits360Dp() {
        var config by mutableStateOf(UiConfig(Locale("es"), 1f))
        compose.setContent {
            val context = remember(config.locale) { localized(baseContext, config.locale) }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(TEST_DENSITY, config.fontScale),
            ) {
                KriboTheme {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        KriboWebSettingsRow(
                            onOpen = {},
                            onCopy = {},
                        )
                    }
                }
            }
        }
        for (fontScale in listOf(1f, 2f)) {
            for (locale in listOf(Locale("es"), Locale("en"))) {
                compose.runOnIdle { config = UiConfig(locale, fontScale) }
                compose.waitForIdle()
                assertControlFits("settings-kribo-web-copy", fontScale, maxHeightDp = 64f)
                val rowBounds = compose.onNodeWithTag("settings-kribo-web-row", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val rowWidthDp = rowBounds.width / TEST_DENSITY
                val rowHeightDp = rowBounds.height / TEST_DENSITY
                assertTrue("settings-kribo-web-row collapsed to ${rowWidthDp}dp", rowWidthDp >= 300f)
                assertTrue("settings-kribo-web-row squished to ${rowHeightDp}dp at scale $fontScale", rowHeightDp <= 140f)
            }
        }
    }

    @Test
    fun voiceDictationSettingsCardFits360DpInBothLanguages() {
        var config by mutableStateOf(UiConfig(Locale("es"), 1f))
        var states by mutableStateOf<Map<VoiceModelLanguage, VoiceModelDownloadState>>(
            mapOf(
                VoiceModelLanguage.SPANISH to VoiceModelDownloadState.NotDownloaded,
                VoiceModelLanguage.ENGLISH to VoiceModelDownloadState.Ready(
                    VoiceModelLanguage.ENGLISH.totalBytes,
                ),
            ),
        )
        compose.setContent {
            val context = remember(config.locale) { localized(baseContext, config.locale) }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(TEST_DENSITY, config.fontScale),
            ) {
                KriboTheme {
                    Column(
                        modifier = Modifier
                            .width(360.dp)
                            .verticalScroll(rememberScrollState())
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        VoiceDictationSettingsCard(
                            selectedMode = VoiceEngineMode.OFFLINE_MOONSHINE,
                            modelStates = states,
                            onSelectMode = {},
                            onDownload = {},
                            onCancelDownload = {},
                            onDelete = {},
                        )
                    }
                }
            }
        }

        for (fontScale in listOf(1f, 2f)) {
            for (locale in listOf(Locale("es"), Locale("en"))) {
                compose.runOnIdle {
                    config = UiConfig(locale, fontScale)
                    states = mapOf(
                        VoiceModelLanguage.SPANISH to VoiceModelDownloadState.Downloading(
                            progressPercent = 47,
                            downloadedBytes = 15_000_000L,
                            totalBytes = VoiceModelLanguage.SPANISH.totalBytes,
                        ),
                        VoiceModelLanguage.ENGLISH to VoiceModelDownloadState.Ready(
                            VoiceModelLanguage.ENGLISH.totalBytes,
                        ),
                    )
                }
                compose.waitForIdle()
                compose.onNodeWithTag("voice-engine-system", useUnmergedTree = true).performScrollTo()
                val sysBounds = compose.onNodeWithTag("voice-engine-system", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                assertTrue(
                    "voice-engine-system collapsed at scale $fontScale",
                    sysBounds.width / TEST_DENSITY >= 280f,
                )
                compose.onNodeWithTag("voice-engine-moonshine", useUnmergedTree = true).performScrollTo()
                val offBounds = compose.onNodeWithTag("voice-engine-moonshine", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                assertTrue(
                    "voice-engine-moonshine collapsed at scale $fontScale",
                    offBounds.width / TEST_DENSITY >= 280f,
                )
                compose.onNodeWithTag("voice-pack-es-action", useUnmergedTree = true).performScrollTo()
                assertControlFits("voice-pack-es-action", fontScale)
                compose.onNodeWithTag("voice-pack-en-action", useUnmergedTree = true).performScrollTo()
                assertControlFits("voice-pack-en-action", fontScale)

                // Also validate NotDownloaded button width/height.
                compose.runOnIdle {
                    states = mapOf(
                        VoiceModelLanguage.SPANISH to VoiceModelDownloadState.NotDownloaded,
                        VoiceModelLanguage.ENGLISH to VoiceModelDownloadState.NotDownloaded,
                    )
                }
                compose.waitForIdle()
                compose.onNodeWithTag("voice-pack-es-action", useUnmergedTree = true).performScrollTo()
                assertControlFits("voice-pack-es-action", fontScale)
                compose.onNodeWithTag("voice-pack-en-action", useUnmergedTree = true).performScrollTo()
                assertControlFits("voice-pack-en-action", fontScale)
            }
        }
    }

    private fun assertControlFits(tag: String, fontScale: Float, maxHeightDp: Float = 86.4f) {
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val widthDp = bounds.width / TEST_DENSITY
        val heightDp = bounds.height / TEST_DENSITY
        assertTrue("$tag collapsed to ${widthDp}dp at scale $fontScale", widthDp >= 30f)
        assertTrue("$tag squished to ${heightDp}dp at scale $fontScale", heightDp <= maxHeightDp)
    }

    private fun localized(base: Context, locale: Locale): Context {
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    private companion object {
        const val TEST_DENSITY = 3.0f
        const val FOOTER_LANE_DP = 320f
    }
}
