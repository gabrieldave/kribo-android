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

    private data class SavedAttachment(
        val id: Long,
        val fileName: String,
        val originalName: String,
        val mimeType: String,
    )

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

            // Save every resource first, keeping the original name so markdown image references
            // can be resolved to the attachment row that now lives in private storage.
            val saved = mutableListOf<SavedAttachment>()
            note.attachments.forEach { attachment ->
                val file = attachmentStore.saveBytes(noteId, attachment.fileName, attachment.bytes)
                val attachmentId = repository.addAttachment(
                    noteId = noteId,
                    filePath = file.absolutePath,
                    mimeType = attachment.mimeType,
                    extractedText = "",
                )
                saved += SavedAttachment(
                    id = attachmentId,
                    fileName = file.name,
                    originalName = attachment.fileName,
                    mimeType = attachment.mimeType,
                )
                attachments++
            }

            val linked = mutableSetOf<Long>()
            val mergedBlocks = note.blocks.map { block ->
                if (block is AttachmentBlock && block.attachmentId == null && block.fileName.isNotBlank()) {
                    val match = saved.firstOrNull {
                        it.originalName.equals(block.fileName, ignoreCase = true) ||
                            it.fileName.equals(block.fileName, ignoreCase = true) ||
                            it.fileName.substringAfterLast('/')
                                .equals(block.fileName.substringAfterLast('/'), ignoreCase = true)
                    }
                    if (match != null) {
                        linked += match.id
                        block.copy(
                            attachmentId = match.id,
                            fileName = match.fileName,
                            mimeType = match.mimeType,
                        )
                    } else {
                        block
                    }
                } else {
                    block
                }
            }.toMutableList()

            // Evernote resources are not referenced by position in our converter: append any
            // resource that did not correspond to an inline markdown image.
            saved.filter { it.id !in linked }.forEach { attachment ->
                mergedBlocks += AttachmentBlock(
                    attachmentId = attachment.id,
                    fileName = attachment.fileName,
                    mimeType = attachment.mimeType,
                    previewText = "",
                )
            }

            if (saved.isNotEmpty() || mergedBlocks != note.blocks) {
                repository.saveBlocks(noteId, mergedBlocks)
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
