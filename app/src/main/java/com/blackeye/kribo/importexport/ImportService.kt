package com.blackeye.kribo.importexport

import com.blackeye.kribo.data.NoteRepository
import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.ocr.AttachmentStore
import java.io.InputStream

/**
 * Persists imported notes, attachments and ZIP subfolders. Attachments are written through
 * [AttachmentStore] so they live under `filesDir/attachments/<noteId>/`.
 */
class ImportService(
    private val repository: NoteRepository,
    private val attachmentStore: AttachmentStore,
) {
    private val enexImporter = EnexImporter()
    private val markdownImporter = MarkdownImporter()

    suspend fun import(
        displayName: String,
        input: InputStream,
        defaultFolderId: Long?,
    ): ImportSummary {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val folders = mutableSetOf<String>()
        var notes = 0
        var attachments = 0

        suspend fun persist(note: ImportedNote) {
            note.folderName?.takeIf { it.isNotBlank() }?.let(folders::add)
            val folderId = note.folderName
                ?.takeIf { it.isNotBlank() }
                ?.let { repository.findOrCreateFolder(it) }
                ?: defaultFolderId

            val noteId = repository.createNote(
                folderId = folderId,
                blocks = note.blocks,
                title = note.title.ifBlank { BlockCodec.derivedTitle(note.blocks) },
            )
            val attachmentBlocks = mutableListOf<AttachmentBlock>()
            note.attachments.forEach { attachment ->
                val file = attachmentStore.saveBytes(noteId, attachment.fileName, attachment.bytes)
                val attachmentId = repository.addAttachment(
                    noteId = noteId,
                    filePath = file.absolutePath,
                    mimeType = attachment.mimeType,
                    extractedText = "",
                )
                attachmentBlocks += AttachmentBlock(
                    attachmentId = attachmentId,
                    fileName = file.name,
                    mimeType = attachment.mimeType,
                    previewText = "",
                )
                attachments++
            }
            if (attachmentBlocks.isNotEmpty()) {
                repository.saveBlocks(noteId, note.blocks + attachmentBlocks)
            }
            notes++
        }

        when (extension) {
            "enex" -> enexImporter.parse(input) { persist(it) }
            "md", "markdown", "txt" -> {
                val text = input.bufferedReader().use { it.readText() }
                persist(
                    markdownImporter.parse(
                        markdown = text,
                        fallbackTitle = displayName.substringBeforeLast('.').ifBlank { "Importada" },
                    ),
                )
            }

            "zip" -> markdownImporter.parseZip(input) { persist(it) }
            else -> throw ImportException("Unsupported extension: $extension")
        }

        return ImportSummary(notes = notes, attachments = attachments, folders = folders.size)
    }
}
