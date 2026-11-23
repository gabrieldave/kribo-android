package com.blackeye.kribo.drive

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray

@Serializable
data class DriveManifest(
    val schemaVersion: Int = 1,
    val folders: List<RemoteFolder> = emptyList(),
    val notes: List<RemoteNoteMeta> = emptyList(),
    val attachments: Map<String, RemoteAttachmentMeta> = emptyMap(),
)

@Serializable
data class RemoteFolder(val id: Long, val name: String)

@Serializable
data class RemoteNoteMeta(
    val id: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val file: String? = "note-$id.json",
)

@Serializable
data class RemoteAttachmentMeta(
    val id: Long,
    val sha256: String,
    val mimeType: String,
    val fileName: String,
    val noteId: Long,
)

@Serializable
data class RemoteAttachmentRef(
    val id: Long,
    val fileName: String,
    val mimeType: String,
    val sha256: String,
)

@Serializable
data class RemoteNote(
    val id: Long,
    val folderId: Long? = null,
    val folderName: String? = null,
    val title: String = "",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val blocks: JsonElement = JsonArray(emptyList()),
    val plainText: String = "",
    val ocrText: String = "",
    val attachments: List<RemoteAttachmentRef> = emptyList(),
)

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String? = null,
    val modifiedTime: String? = null,
    val appProperties: Map<String, String> = emptyMap(),
    val md5Checksum: String? = null,
)
