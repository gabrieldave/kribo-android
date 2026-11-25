package com.blackeye.kribo.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.data.blocks.ParagraphBlock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real Room v1 → v2 migration. Creates a v1 database with the exact v1 DDL, opens the v2
 * database through [MIGRATION_1_2] and verifies the user's notes survive byte-for-byte.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Migration1To2Test {

    private lateinit var context: Context
    private lateinit var databaseFile: java.io.File
    private var database: KriboDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseFile = context.getDatabasePath("migration-1-2-kribo.db")
        databaseFile.parentFile?.mkdirs()
        databaseFile.delete()
    }

    @After
    fun tearDown() {
        database?.close()
        databaseFile.delete()
    }

    @Test
    fun migratesFromV1ToV2PreservingNotes() = runBlocking {
        val legacyContent = "Lista del súper\nLeche\nPan integral"
        createVersion1Database(
            noteId = 5L,
            content = legacyContent,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_050_000L,
        )

        database = Room.databaseBuilder(
            context,
            KriboDatabase::class.java,
            databaseFile.absolutePath,
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        val note = database!!.noteDao().getById(5L)
        assertNotNull("The v1 note must survive the migration", note)
        assertEquals("Lista del súper", note?.title)
        assertEquals(legacyContent, note?.plainTextIndex)
        assertEquals("", note?.ocrTextIndex)
        assertEquals(DEFAULT_FOLDER_GENERAL, note?.folderId)
        assertEquals(1_700_000_000_000L, note?.createdAt)
        assertEquals(1_700_000_050_000L, note?.updatedAt)

        val blocks = BlockCodec.decode(note?.blocksJson)
        assertEquals(1, blocks.size)
        assertTrue(blocks.first() is ParagraphBlock)
        assertEquals(legacyContent, (blocks.first() as ParagraphBlock).text)

        val folders = database!!.folderDao().getAll()
        assertEquals(3, folders.size)
        assertEquals(listOf("General", "Recibos", "Ideas"), folders.map { it.name })
    }

    @Test
    fun migrationLeavesAllNewTablesOperational() = runBlocking {
        createVersion1Database(noteId = 1L, content = "Uno")

        database = Room.databaseBuilder(
            context,
            KriboDatabase::class.java,
            databaseFile.absolutePath,
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        val db = database!!
        assertEquals(3, db.openHelper.readableDatabase.version)

        val noteId = db.noteDao().insert(
            NoteEntity(
                folderId = DEFAULT_FOLDER_IDEAS,
                title = "Nueva",
                blocksJson = "[]",
                plainTextIndex = "",
                ocrTextIndex = "",
                isPinned = false,
                createdAt = 10L,
                updatedAt = 10L,
            ),
        )
        assertTrue(noteId > 0L)

        val attachmentId = db.attachmentDao().insert(
            AttachmentEntity(
                noteId = noteId,
                filePath = "attachments/$noteId/page.jpg",
                mimeType = "image/jpeg",
                extractedText = "texto OCR",
                createdAt = 11L,
            ),
        )
        assertTrue(attachmentId > 0L)
        assertEquals(1, db.attachmentDao().getForNote(noteId).size)

        // ON DELETE CASCADE removes attachments with their note.
        db.noteDao().deleteById(noteId)
        assertEquals(0, db.attachmentDao().getForNote(noteId).size)
    }

    private fun createVersion1Database(
        noteId: Long,
        content: String,
        createdAt: Long = 1_600_000_000_000L,
        updatedAt: Long = 1_600_000_000_000L,
    ) {
        val legacy = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS `notes` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`content` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)",
            )
            legacy.execSQL(
                "INSERT INTO notes (id, content, createdAt, updatedAt) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(noteId, content, createdAt, updatedAt),
            )
            legacy.version = 1
        } finally {
            legacy.close()
        }
    }
}
