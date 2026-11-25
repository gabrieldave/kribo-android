package com.blackeye.kribo.data

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real Room v2 → v3 migration: a v2 database (no `fontStyle` column) gains the column
 * via [MIGRATION_2_3], existing notes default to SANS and nothing is lost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Migration2To3Test {

    private lateinit var context: Context
    private lateinit var databaseFile: java.io.File
    private var database: KriboDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseFile = context.getDatabasePath("migration-2-3-kribo.db")
        databaseFile.parentFile?.mkdirs()
        databaseFile.delete()
    }

    @After
    fun tearDown() {
        database?.close()
        databaseFile.delete()
    }

    @Test
    fun migratesFromV2ToV3DefaultingFontToSans() = runBlocking {
        createVersion2Database(
            noteId = 7L,
            title = "Legada",
            blocksJson = "[]",
            plainTextIndex = "cuerpo v2",
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

        val db = database!!
        assertEquals(3, db.openHelper.readableDatabase.version)

        val note = db.noteDao().getById(7L)
        assertNotNull("The v2 note must survive the migration", note)
        assertEquals("Legada", note?.title)
        assertEquals("cuerpo v2", note?.plainTextIndex)
        assertEquals("SANS", note?.fontStyle)

        db.noteDao().update(note!!.copy(fontStyle = "SERIF"))
        assertEquals("SERIF", db.noteDao().getById(7L)?.fontStyle)
    }

    /** Exact v2 DDL (same shape as the v2 schema snapshot, without `fontStyle`). */
    private fun createVersion2Database(
        noteId: Long,
        title: String,
        blocksJson: String,
        plainTextIndex: String,
        createdAt: Long,
        updatedAt: Long,
    ) {
        val db = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        db.version = 2
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `folders` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`iconKey` TEXT NOT NULL, " +
                "`colorHex` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_folders_name` ON `folders` (`name`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`folderId` INTEGER, " +
                "`title` TEXT NOT NULL, " +
                "`blocksJson` TEXT NOT NULL, " +
                "`plainTextIndex` TEXT NOT NULL, " +
                "`ocrTextIndex` TEXT NOT NULL, " +
                "`isPinned` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`folderId`) REFERENCES `folders`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_folderId` ON `notes` (`folderId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_updatedAt` ON `notes` (`updatedAt`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `attachments` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`noteId` INTEGER NOT NULL, " +
                "`filePath` TEXT NOT NULL, " +
                "`mimeType` TEXT NOT NULL, " +
                "`extractedText` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_noteId` ON `attachments` (`noteId`)")
        val folderValues = ContentValues().apply {
            put("id", 1L)
            put("name", "General")
            put("iconKey", "general")
            put("colorHex", "#00D8FF")
            put("sortOrder", 0)
            put("createdAt", 10L)
        }
        db.insert("folders", null, folderValues)
        val values = ContentValues().apply {
            put("id", noteId)
            put("folderId", 1L)
            put("title", title)
            put("blocksJson", blocksJson)
            put("plainTextIndex", plainTextIndex)
            put("ocrTextIndex", "")
            put("isPinned", 0)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }
        db.insert("notes", null, values)
        db.close()
    }
}
