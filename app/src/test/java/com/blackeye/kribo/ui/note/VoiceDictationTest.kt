package com.blackeye.kribo.ui.note

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blackeye.kribo.data.KriboDatabase
import com.blackeye.kribo.data.NoteRepository
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.seedDefaultFolders
import com.blackeye.kribo.ocr.AttachmentStore
import com.blackeye.kribo.ui.settings.AppLanguage
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

    private suspend fun waitUntil(timeoutMs: Long = 2_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            delay(15L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Condition did not become true within ${timeoutMs}ms", predicate())
    }
}
