package com.blackeye.kribo.ui.note

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blackeye.kribo.data.KriboDatabase
import com.blackeye.kribo.data.NoteRepository
import com.blackeye.kribo.data.blocks.Caret
import com.blackeye.kribo.data.blocks.ListBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import com.blackeye.kribo.data.blocks.ToggleBlock
import com.blackeye.kribo.data.seedDefaultFolders
import com.blackeye.kribo.ocr.AttachmentStore
import com.blackeye.kribo.ui.preferences.UiPreferences
import com.blackeye.kribo.ui.preferences.VoiceEngineMode
import com.blackeye.kribo.ui.settings.AppLanguage
import com.blackeye.kribo.voice.VoiceModelManager
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoiceDictationTest {

    private lateinit var database: KriboDatabase
    private lateinit var root: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = File(context.cacheDir, "dictation-test-${System.currentTimeMillis()}").apply { mkdirs() }
        database = Room.inMemoryDatabaseBuilder(context, KriboDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedDefaultFolders(database.openHelper.writableDatabase)
    }

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun resolveDictationLanguageTagFollowsActiveAppLanguage() {
        assertEquals("es-MX", resolveDictationLanguageTag(AppLanguage.SPANISH, Locale.US))
        assertEquals("es-ES", resolveDictationLanguageTag(AppLanguage.SPANISH, Locale("es", "ES")))
        assertEquals("en-US", resolveDictationLanguageTag(AppLanguage.ENGLISH, Locale("es", "MX")))
        assertEquals("en-GB", resolveDictationLanguageTag(AppLanguage.ENGLISH, Locale.UK))
        assertEquals("es-AR", resolveDictationLanguageTag(AppLanguage.SYSTEM, Locale("es", "AR")))
    }

    @Test
    fun pcm16ToNormalizedFloatScalesCleanlyIntoUnitRange() {
        val pcm = shortArrayOf(0, Short.MAX_VALUE, Short.MIN_VALUE, 16_384, -16_384)
        val floats = pcm16ToNormalizedFloat(pcm, 5)
        assertEquals(5, floats.size)
        assertEquals(0f, floats[0], 0.0001f)
        assertEquals(32767f / 32768f, floats[1], 0.0001f)
        assertEquals(-1f, floats[2], 0.0001f)
        assertEquals(0.5f, floats[3], 0.0001f)
        assertEquals(-0.5f, floats[4], 0.0001f)
    }

    @Test
    fun offlineMoonshineEngineReportsModelMissingWhenPackNotInstalled() {
        val manager = VoiceModelManager(File(root, "voice_models"))
        var reportedError: DictationError? = null
        var lastState: DictationState? = null
        val engine = MoonshineOfflineDictationEngine(
            modelManager = manager,
            callbacks = object : VoiceDictationCallbacks {
                override fun onPartial(text: String) = Unit
                override fun onFinal(text: String) = Unit
                override fun onStateChange(state: DictationState) {
                    lastState = state
                }

                override fun onError(error: DictationError) {
                    reportedError = error
                }
            },
            mainPoster = { it() },
        )
        engine.start(AppLanguage.SPANISH)
        assertEquals(DictationError.MODEL_MISSING, reportedError)
        assertEquals(DictationState.IDLE, lastState)
        engine.destroy()
    }

    @Test
    fun voiceEnginePreferenceDefaultsToSystemAndPersistsOfflineMode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UiPreferences(context)
        assertEquals(VoiceEngineMode.SYSTEM, prefs.voiceEngineMode.value)
        prefs.setVoiceEngineMode(VoiceEngineMode.OFFLINE_MOONSHINE)
        assertEquals(VoiceEngineMode.OFFLINE_MOONSHINE, UiPreferences(context).voiceEngineMode.value)
        prefs.setVoiceEngineMode(VoiceEngineMode.SYSTEM)
    }

    @Test
    fun insertDictatedTextInsertsAtCaretSupportsUndoRedoAndNotifiesAutosync() = runBlocking {
        val dataChanges = AtomicInteger(0)
        val repository = NoteRepository(
            database = database,
            attachmentsRoot = root,
            onDataChanged = { dataChanges.incrementAndGet() },
        )
        val initialBlock = ParagraphBlock(id = "p-1", text = "Comprar leche")
        val noteId = repository.createNote(folderId = 1L, blocks = listOf(initialBlock), title = "Lista")
        val changesAfterCreate = dataChanges.get()

        val viewModel = NoteEditorViewModel(
            repository = repository,
            attachmentStore = AttachmentStore(root),
            noteId = noteId,
        )
        waitUntil { !viewModel.isLoading }

        // Focused block insertion at caret with clean spacing.
        viewModel.onFocus("p-1")
        viewModel.onSelection("p-1", start = 13, end = 13)
        viewModel.insertDictatedText("y pan integral")

        assertEquals(
            "Comprar leche y pan integral",
            (viewModel.blocks.single() as ParagraphBlock).text,
        )
        assertTrue(viewModel.canUndo)

        // Wait for immediate save to persist and trigger repository onDataChanged (Drive auto-sync).
        waitUntil { dataChanges.get() > changesAfterCreate }
        assertTrue(dataChanges.get() > changesAfterCreate)

        // Undo removes the dictated phrase; Redo brings it back.
        viewModel.undo()
        assertEquals("Comprar leche", (viewModel.blocks.single() as ParagraphBlock).text)
        assertTrue(viewModel.canRedo)
        viewModel.redo()
        assertEquals(
            "Comprar leche y pan integral",
            (viewModel.blocks.single() as ParagraphBlock).text,
        )

        // Unfocused dictation appends a new ParagraphBlock cleanly.
        viewModel.focusedBlockId = null
        viewModel.insertDictatedText("Recordar llamar al banco")
        assertEquals(2, viewModel.blocks.size)
        assertEquals(
            "Recordar llamar al banco",
            (viewModel.blocks.last() as ParagraphBlock).text,
        )
    }

    @Test
    fun dictationIntoTitleInsertsAtCaretStripsNewlinesPreservesFocusAndNeverCreatesBodyParagraph() = runBlocking {
        val dataChanges = AtomicInteger(0)
        val repository = NoteRepository(
            database = database,
            attachmentsRoot = root,
            onDataChanged = { dataChanges.incrementAndGet() },
        )
        val bodyBlock = ParagraphBlock(id = "p-body", text = "Cuerpo intacto")
        val noteId = repository.createNote(
            folderId = 1L,
            blocks = listOf(bodyBlock),
            title = "Reunión mensual",
        )
        val changesAfterCreate = dataChanges.get()

        val viewModel = NoteEditorViewModel(
            repository = repository,
            attachmentStore = AttachmentStore(root),
            noteId = noteId,
        )
        waitUntil { !viewModel.isLoading }

        // User taps the note title and puts the cursor between "Reunión" and "mensual" (index 7).
        viewModel.onTitleFocus(start = 7, end = 7)
        assertTrue(viewModel.isTitleFocused)
        assertNull(viewModel.focusedBlockId)

        // Tap toolbar-mic: freeze the Title target at caret 7.
        val captured = viewModel.captureDictationTarget()
        assertEquals(DictationTarget.Title(Caret(7, 7)), captured)

        // Even if a stray focus shift touches a body block after mic press, captured Title wins.
        viewModel.onFocus("p-body")
        viewModel.insertDictatedText("de ventas\ny producto")

        assertEquals("Reunión de ventas y producto mensual", viewModel.noteTitle)
        assertTrue(viewModel.isTitleFocused)
        assertNull(viewModel.focusedBlockId)
        assertNull(viewModel.focusRequestId)
        assertEquals(1, viewModel.blocks.size)
        assertEquals("Cuerpo intacto", (viewModel.blocks.single() as ParagraphBlock).text)

        // Second continuous phrase appends right after the first inside Title, never creating blocks.
        viewModel.insertDictatedText("urgente")
        assertEquals("Reunión de ventas y producto urgente mensual", viewModel.noteTitle)
        assertTrue(viewModel.isTitleFocused)
        assertNull(viewModel.focusedBlockId)
        assertEquals(1, viewModel.blocks.size)
        assertEquals("Cuerpo intacto", (viewModel.blocks.single() as ParagraphBlock).text)

        // Auto-save persists the dictated title and notifies Drive auto-sync.
        waitUntil { dataChanges.get() > changesAfterCreate }
        waitUntil { repository.getNote(noteId)?.title == "Reunión de ventas y producto urgente mensual" }

        // Undo/Redo restores title cleanly while keeping focus in Title.
        assertTrue(viewModel.canUndo)
        viewModel.undo()
        assertEquals("Reunión de ventas y producto mensual", viewModel.noteTitle)
        assertTrue(viewModel.isTitleFocused)
        assertNull(viewModel.focusedBlockId)
        viewModel.undo()
        assertEquals("Reunión mensual", viewModel.noteTitle)
        assertTrue(viewModel.isTitleFocused)
        viewModel.redo()
        assertEquals("Reunión de ventas y producto mensual", viewModel.noteTitle)
        assertTrue(viewModel.isTitleFocused)
    }

    @Test
    fun dictationInsertsAtExactCaretAcrossEveryEditableBlockType() = runBlocking {
        val repository = NoteRepository(
            database = database,
            attachmentsRoot = root,
        )
        val initialBlocks = listOf(
            TitleBlock(id = "h1", text = "Proyecto", level = 1),
            TitleBlock(id = "h2", text = "Resumen", level = 2),
            ParagraphBlock(id = "p", text = "Texto base"),
            ListBlock(id = "ol", text = "Paso", ordered = true),
            ListBlock(id = "ul", text = "Elemento", ordered = false),
            TaskBlock(id = "task", text = "Revisar"),
            QuoteBlock(id = "quote", text = "Solo sé"),
            ToggleBlock(id = "toggle", title = "Sección", content = "Detalle inicial", expanded = true),
        )
        val noteId = repository.createNote(
            folderId = 1L,
            blocks = initialBlocks,
            title = "Auditoría",
        )
        val viewModel = NoteEditorViewModel(
            repository = repository,
            attachmentStore = AttachmentStore(root),
            noteId = noteId,
        )
        waitUntil { !viewModel.isLoading }

        // 1. H1 (strips newlines cleanly so headings stay single-line).
        viewModel.clearCapturedDictationTarget()
        viewModel.onFocus("h1")
        viewModel.onSelection("h1", 8, 8)
        assertEquals(DictationTarget.Block("h1", isToggleTitle = false, Caret(8, 8)), viewModel.captureDictationTarget())
        viewModel.insertDictatedText("Kribo\n2026")
        assertEquals("Proyecto Kribo 2026", (viewModel.blocks[0] as TitleBlock).text)
        assertNull(viewModel.focusRequestId)

        // 2. H2.
        viewModel.clearCapturedDictationTarget()
        viewModel.onFocus("h2")
        viewModel.onSelection("h2", 7, 7)
        assertEquals(DictationTarget.Block("h2", isToggleTitle = false, Caret(7, 7)), viewModel.captureDictationTarget())
        viewModel.insertDictatedText("ejecutivo")
        assertEquals("Resumen ejecutivo", (viewModel.blocks[1] as TitleBlock).text)
        assertNull(viewModel.focusRequestId)

        // 3. ParagraphBlock (mid-sentence caret).
        viewModel.clearCapturedDictationTarget()
        viewModel.onFocus("p")
        viewModel.onSelection("p", 5, 5)
        assertEquals(DictationTarget.Block("p", isToggleTitle = false, Caret(5, 5)), viewModel.captureDictationTarget())
        viewModel.insertDictatedText("editado")
        assertEquals("Texto editado base", (viewModel.blocks[2] as ParagraphBlock).text)
        assertNull(viewModel.focusRequestId)

        // 4. Ordered ListBlock (1. 2. 3.).
        viewModel.clearCapturedDictationTarget()
        viewModel.onFocus("ol")
        viewModel.onSelection("ol", 4, 4)
        assertEquals(DictationTarget.Block("ol", isToggleTitle = false, Caret(4, 4)), viewModel.captureDictationTarget())
        viewModel.insertDictatedText("uno completado")
        assertEquals("Paso uno completado", (viewModel.blocks[3] as ListBlock).text)
        assertNull(viewModel.focusRequestId)

        // 5. Bullet ListBlock (•).
        viewModel.clearCapturedDictationTarget()
        viewModel.onFocus("ul")
        viewModel.onSelection("ul", 8, 8)
        assertEquals(DictationTarget.Block("ul", isToggleTitle = false, Caret(8, 8)), viewModel.captureDictationTarget())
        viewModel.insertDictatedText("importante")
        assertEquals("Elemento importante", (viewModel.blocks[4] as ListBlock).text)
        assertNull(viewModel.focusRequestId)

        // 6. TaskBlock (checklist).
        viewModel.clearCapturedDictationTarget()
        viewModel.onFocus("task")
        viewModel.onSelection("task", 7, 7)
        assertEquals(DictationTarget.Block("task", isToggleTitle = false, Caret(7, 7)), viewModel.captureDictationTarget())
        viewModel.insertDictatedText("factura pendiente")
        assertEquals("Revisar factura pendiente", (viewModel.blocks[5] as TaskBlock).text)
        assertNull(viewModel.focusRequestId)

        // 7. QuoteBlock (cita).
        viewModel.clearCapturedDictationTarget()
        viewModel.onFocus("quote")
        viewModel.onSelection("quote", 7, 7)
        assertEquals(DictationTarget.Block("quote", isToggleTitle = false, Caret(7, 7)), viewModel.captureDictationTarget())
        viewModel.insertDictatedText("que nada sé")
        assertEquals("Solo sé que nada sé", (viewModel.blocks[6] as QuoteBlock).text)
        assertNull(viewModel.focusRequestId)

        // 8a. ToggleBlock title (must only update title, never content).
        viewModel.clearCapturedDictationTarget()
        viewModel.onFocus("toggle", NoteEditorViewModel.BlockField.TOGGLE_TITLE)
        viewModel.onSelection("toggle", 7, 7, NoteEditorViewModel.BlockField.TOGGLE_TITLE)
        assertEquals(
            DictationTarget.Block("toggle", isToggleTitle = true, Caret(7, 7)),
            viewModel.captureDictationTarget(),
        )
        viewModel.insertDictatedText("oculta")
        val toggleAfterTitle = viewModel.blocks[7] as ToggleBlock
        assertEquals("Sección oculta", toggleAfterTitle.title)
        assertEquals("Detalle inicial", toggleAfterTitle.content)
        assertNull(viewModel.focusRequestId)

        // 8b. ToggleBlock content (mid-sentence caret; must only update content, never title).
        viewModel.clearCapturedDictationTarget()
        viewModel.onFocus("toggle", NoteEditorViewModel.BlockField.TOGGLE_CONTENT)
        viewModel.onSelection("toggle", 7, 7, NoteEditorViewModel.BlockField.TOGGLE_CONTENT)
        assertEquals(
            DictationTarget.Block("toggle", isToggleTitle = false, Caret(7, 7)),
            viewModel.captureDictationTarget(),
        )
        viewModel.insertDictatedText("extra")
        val toggleAfterContent = viewModel.blocks[7] as ToggleBlock
        assertEquals("Sección oculta", toggleAfterContent.title)
        assertEquals("Detalle extra inicial", toggleAfterContent.content)
        assertNull(viewModel.focusRequestId)

        // Zero extra blocks were created across all 9 block/sub-field insertions, and Title stayed untouched.
        assertEquals(8, viewModel.blocks.size)
        assertEquals("Auditoría", viewModel.noteTitle)

        // 9. Fallback ONLY when literally nothing was focused prior to mic press.
        viewModel.prepareForReorder()
        assertNull(viewModel.captureDictationTarget())
        viewModel.insertDictatedText("Párrafo de respaldo")
        assertEquals(9, viewModel.blocks.size)
        assertEquals("Párrafo de respaldo", (viewModel.blocks.last() as ParagraphBlock).text)
        // Continuous second phrase in the same unfocused session appends to that same fallback paragraph.
        viewModel.insertDictatedText("continuado")
        assertEquals(9, viewModel.blocks.size)
        assertEquals("Párrafo de respaldo continuado", (viewModel.blocks.last() as ParagraphBlock).text)
    }

    private suspend fun waitUntil(timeoutMs: Long = 2_000L, predicate: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            delay(15L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Condition did not become true within ${timeoutMs}ms", predicate())
    }
}
