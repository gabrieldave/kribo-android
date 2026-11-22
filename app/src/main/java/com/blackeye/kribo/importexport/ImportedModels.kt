package com.blackeye.kribo.importexport

import com.blackeye.kribo.data.blocks.NoteBlock

data class ImportedAttachment(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class ImportedNote(
    val title: String,
    val blocks: List<NoteBlock>,
    val attachments: List<ImportedAttachment> = emptyList(),
    val folderName: String? = null,
)

data class ImportSummary(
    val notes: Int,
    val attachments: Int,
    val folders: Int,
)

class ImportException(message: String) : Exception(message)
