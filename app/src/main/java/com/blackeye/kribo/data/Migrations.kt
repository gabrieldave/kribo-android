package com.blackeye.kribo.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.data.blocks.ParagraphBlock

/**
 * v1 → v2 · non destructive.
 *
 * Adds folders, block JSON, the text/OCR indexes and attachments. Every v1 note keeps its
 * content byte-for-byte in `plainTextIndex` and is represented as a single ParagraphBlock.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()

        db.execSQL(CREATE_FOLDERS)
        db.execSQL(CREATE_FOLDER_NAME_INDEX)
        seedDefaultFolders(db, now)

        db.execSQL(CREATE_NOTES_V2)

        val cursor = db.query("SELECT id, content, createdAt, updatedAt FROM notes")
        cursor.use { rows ->
            val idIndex = rows.getColumnIndexOrThrow("id")
            val contentIndex = rows.getColumnIndexOrThrow("content")
            val createdIndex = rows.getColumnIndexOrThrow("createdAt")
            val updatedIndex = rows.getColumnIndexOrThrow("updatedAt")
            while (rows.moveToNext()) {
                val id = rows.getLong(idIndex)
                val content = rows.getString(contentIndex).orEmpty()
                val createdAt = rows.getLong(createdIndex)
                val updatedAt = rows.getLong(updatedIndex)
                val blocks = BlockCodec.legacyParagraphs(content)
                val title = content.lineSequence()
                    .firstOrNull { it.isNotBlank() }
                    ?.trim()
                    .orEmpty()
                    .take(120)
                db.execSQL(
                    "INSERT INTO `notes_v2` " +
                        "(`id`, `folderId`, `title`, `blocksJson`, `plainTextIndex`, `ocrTextIndex`, " +
                        "`isPinned`, `createdAt`, `updatedAt`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>(
                        id,
                        1L,
                        title,
                        BlockCodec.encode(blocks),
                        content,
                        "",
                        0,
                        createdAt,
                        updatedAt,
                    ),
                )
            }
        }

        db.execSQL("DROP TABLE `notes`")
        db.execSQL("ALTER TABLE `notes_v2` RENAME TO `notes`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_folderId` ON `notes` (`folderId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_updatedAt` ON `notes` (`updatedAt`)")

        db.execSQL(CREATE_ATTACHMENTS)
        db.execSQL(CREATE_ATTACHMENTS_INDEX)
    }
}

/**
 * v2 → v3 · non destructive.
 *
 * Adds the per-note editorial font (`fontStyle`, default SANS) for #42. Existing notes
 * keep every block and every byte; they simply read back as SANS.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `notes` ADD COLUMN `fontStyle` TEXT NOT NULL DEFAULT 'SANS'")
    }
}

internal const val DEFAULT_FOLDER_GENERAL = 1L
internal const val DEFAULT_FOLDER_RECEIPTS = 2L
internal const val DEFAULT_FOLDER_IDEAS = 3L

/** Seeds the three friendly starter folders. Safe to call on fresh installs and migrations. */
fun seedDefaultFolders(db: SupportSQLiteDatabase, now: Long = System.currentTimeMillis()) {
    db.execSQL(
        "INSERT OR IGNORE INTO `folders` (`id`, `name`, `iconKey`, `colorHex`, `sortOrder`, `createdAt`) " +
            "VALUES (1, 'General', 'general', '#00D8FF', 0, ?)",
        arrayOf(now),
    )
    db.execSQL(
        "INSERT OR IGNORE INTO `folders` (`id`, `name`, `iconKey`, `colorHex`, `sortOrder`, `createdAt`) " +
            "VALUES (2, 'Recibos', 'receipt', '#5EEAD4', 1, ?)",
        arrayOf(now),
    )
    db.execSQL(
        "INSERT OR IGNORE INTO `folders` (`id`, `name`, `iconKey`, `colorHex`, `sortOrder`, `createdAt`) " +
            "VALUES (3, 'Ideas', 'idea', '#A78BFA', 2, ?)",
        arrayOf(now),
    )
}

private const val CREATE_FOLDERS =
    "CREATE TABLE IF NOT EXISTS `folders` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`name` TEXT NOT NULL, " +
        "`iconKey` TEXT NOT NULL, " +
        "`colorHex` TEXT NOT NULL, " +
        "`sortOrder` INTEGER NOT NULL, " +
        "`createdAt` INTEGER NOT NULL)"

private const val CREATE_FOLDER_NAME_INDEX =
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_folders_name` ON `folders` (`name`)"

private const val CREATE_NOTES_V2 =
    "CREATE TABLE IF NOT EXISTS `notes_v2` (" +
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
        "ON UPDATE NO ACTION ON DELETE SET NULL )"

private const val CREATE_ATTACHMENTS =
    "CREATE TABLE IF NOT EXISTS `attachments` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`noteId` INTEGER NOT NULL, " +
        "`filePath` TEXT NOT NULL, " +
        "`mimeType` TEXT NOT NULL, " +
        "`extractedText` TEXT NOT NULL, " +
        "`createdAt` INTEGER NOT NULL, " +
        "FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) " +
        "ON UPDATE NO ACTION ON DELETE CASCADE )"

private const val CREATE_ATTACHMENTS_INDEX =
    "CREATE INDEX IF NOT EXISTS `index_attachments_noteId` ON `attachments` (`noteId`)"
