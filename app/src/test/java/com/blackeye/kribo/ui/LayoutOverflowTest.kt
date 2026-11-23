package com.blackeye.kribo.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.blackeye.kribo.data.blocks.TitleBlock
import com.blackeye.kribo.ui.home.QuickCaptureBar
import com.blackeye.kribo.ui.note.AttachmentActionsRow
import com.blackeye.kribo.ui.note.EditorToolbar
import com.blackeye.kribo.ui.theme.KriboTheme
import java.util.Locale
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
                assertTrue("Missing toolbar buttons: ${toolbarButtons.size}", toolbarButtons.size >= 5)
                assertControlFits("toolbar-h1", fontScale, maxHeightDp = 60f)
                assertControlFits("toolbar-h2", fontScale, maxHeightDp = 60f)
                toolbarButtons.forEach { node ->
                    val bounds = node.boundsInRoot
                    val heightDp = bounds.height / TEST_DENSITY
                    val widthDp = bounds.width / TEST_DENSITY
                    assertTrue("Squished toolbar button at scale $fontScale: $heightDp", heightDp <= 60f)
                    assertTrue("Collapsed toolbar button at scale $fontScale: $widthDp", widthDp >= 44f)
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

    private fun assertControlFits(tag: String, fontScale: Float, maxHeightDp: Float = 86.4f) {
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
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
    }
}
