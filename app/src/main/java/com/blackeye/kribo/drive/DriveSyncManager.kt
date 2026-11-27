package com.blackeye.kribo.drive

import com.blackeye.kribo.data.DEFAULT_FOLDER_GENERAL
import com.blackeye.kribo.data.NoteEntity
import com.blackeye.kribo.data.NoteRepository
import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.ocr.AttachmentStore
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

sealed interface DriveSyncResult {
    data class Success(val notes: Int, val attachments: Int) : DriveSyncResult
    data object AuthRequired : DriveSyncResult
    data class Error(val kind: DriveSyncError) : DriveSyncResult
}

enum class DriveSyncError { NETWORK, AUTH, OAUTH_CONFIGURATION, UNKNOWN }

/**
 * Offline-first two-way sync with Drive's private `drive.file` scope.
 *
 * Local Room data is always written first. A sync failure never deletes local notes.
 * Format:
 *  - Kribo/ (appProperties.kriboRoot=true)
 *  - sync-manifest.json (schemaVersion=1)
 *  - note-<id>.json
 *  - att-<id>.<ext>
 */
class DriveSyncManager(
    private val repository: NoteRepository,
    private val attachmentStore: AttachmentStore,
    private val api: DriveApiClient,
    private val tombstones: DriveTombstoneStore,
    private val auth: DriveAuthSource,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    suspend fun sync(): DriveSyncResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { syncLocked() }
                .getOrElse { throwable -> mapFailure(throwable) }
        }
    }

    private suspend fun syncLocked(): DriveSyncResult {
        if (auth.validToken() == null) return DriveSyncResult.AuthRequired

        val session = auth.sessionSnapshot()
        val root = api.ensureRootFolder(session.rootFolderId)
        auth.saveRootFolder(root.id)

        val files = api.listFiles(root.id)
        val filesByName = files.associateBy { it.name }
        val manifestFile = filesByName[MANIFEST_NAME]
        val manifest = if (manifestFile == null) {
            DriveManifest()
        } else {
            runCatching {
                json.decodeFromString<DriveManifest>(api.downloadText(manifestFile.id))
            }.getOrDefault(DriveManifest())
        }

        val localFoldersBefore = repository.getAllFolders()
        val folderIdByLowerName = localFoldersBefore
            .associate { it.name.lowercase(Locale.ROOT) to it.id }
            .toMutableMap()
        val remoteFolderToLocal = mutableMapOf<Long, Long>()
        manifest.folders.forEach { remote ->
            val localId = repository.findOrCreateFolder(
                name = remote.name,
                colorHex = remote.colorHex,
                iconKey = remote.iconKey,
            )
            folderIdByLowerName[remote.name.lowercase(Locale.ROOT)] = localId
            remoteFolderToLocal[remote.id] = localId
        }
        val folders = repository.getAllFolders()
        val folderById = folders.associateBy { it.id }

        val localNotes = repository.getAllNotes().associateBy { it.id }.toMutableMap()
        val localTombstones = tombstones.all().toMutableMap()
        manifest.notes.mapNotNull { meta ->
            meta.deletedAt?.let { deleted -> meta.id to deleted }
        }.toMap().let(tombstones::merge)
        val remoteNotes = manifest.notes.associateBy { it.id }
        val remoteAttachments = manifest.attachments.mapNotNull { (key, value) ->
            key.toLongOrNull()?.let { it to value }
        }.toMap()

        val newNoteMeta = linkedMapOf<Long, RemoteNoteMeta>()
        val newAttachments = linkedMapOf<Long, RemoteAttachmentMeta>()
        var uploadedNotes = 0
        var uploadedAttachments = 0

        val ids = linkedSetOf<Long>()
        ids += localNotes.keys
        ids += localTombstones.keys
        ids += remoteNotes.keys

        for (id in ids.sorted()) {
            val local = localNotes[id]
            val localDeleted = localTombstones[id]
            val remote = remoteNotes[id]
            val remoteFile = remote?.file?.let(filesByName::get)
                ?: filesByName["note-$id.json"]

            when {
                // A local tombstone wins unless the remote edit is strictly newer.
                local == null && localDeleted != null -> {
                    if (remote != null && remote.deletedAt == null && remote.updatedAt > localDeleted) {
                        val loaded = downloadRemoteNote(remote, remoteFile)
                        if (loaded != null) {
                            applyRemoteNote(
                                loaded,
                                remoteFolderToLocal,
                                folderById,
                                filesByName,
                                newAttachments,
                            )
                            localTombstones.remove(id)
                            newNoteMeta[id] = RemoteNoteMeta(
                                id = id,
                                updatedAt = loaded.updatedAt,
                                deletedAt = null,
                                file = "note-$id.json",
                            )
                        }
                    } else {
                        val tombstoneAt = maxOf(remote?.deletedAt ?: 0L, localDeleted)
                        tombstones.mark(id, tombstoneAt)
                        newNoteMeta[id] = RemoteNoteMeta(
                            id = id,
                            updatedAt = maxOf(remote?.updatedAt ?: 0L, localDeleted),
                            deletedAt = tombstoneAt,
                            file = null,
                        )
                        remoteFile?.let { runCatching { api.deleteFile(it.id) } }
                    }
                }

                // No local row and no tombstone: import a live remote note.
                local == null -> {
                    if (remote != null) {
                        if (remote.deletedAt != null) {
                            localTombstones[id] = remote.deletedAt
                            tombstones.mark(id, remote.deletedAt)
                            newNoteMeta[id] = remote.copy(file = null)
                        } else {
                            val loaded = downloadRemoteNote(remote, remoteFile)
                            if (loaded != null) {
                                applyRemoteNote(
                                    loaded,
                                    remoteFolderToLocal,
                                    folderById,
                                    filesByName,
                                    newAttachments,
                                )
                                newNoteMeta[id] = RemoteNoteMeta(
                                    id = id,
                                    updatedAt = loaded.updatedAt,
                                    deletedAt = null,
                                    file = "note-$id.json",
                                )
                            }
                        }
                    }
                }

                // Local live note.
                else -> {
                    val remoteDeletedAt = remote?.deletedAt
                    val remoteUpdatedAt = remote?.updatedAt ?: 0L
                    when {
                        remoteDeletedAt != null && remoteDeletedAt >= local.updatedAt -> {
                            repository.deleteNote(id)
                            tombstones.mark(id, remoteDeletedAt)
                            localTombstones[id] = remoteDeletedAt
                            localNotes.remove(id)
                            newNoteMeta[id] = RemoteNoteMeta(
                                id = id,
                                updatedAt = remoteDeletedAt,
                                deletedAt = remoteDeletedAt,
                                file = null,
                            )
                            remoteFile?.let { runCatching { api.deleteFile(it.id) } }
                        }

                        remote != null && remoteUpdatedAt > local.updatedAt -> {
                            val loaded = downloadRemoteNote(remote, remoteFile)
                            if (loaded != null) {
                                applyRemoteNote(
                                    loaded,
                                    remoteFolderToLocal,
                                    folderById,
                                    filesByName,
                                    newAttachments,
                                )
                                newNoteMeta[id] = RemoteNoteMeta(
                                    id = id,
                                    updatedAt = loaded.updatedAt,
                                    deletedAt = null,
                                    file = "note-$id.json",
                                )
                            }
                        }

                        else -> {
                            val uploaded = uploadLocalNote(
                                note = local,
                                root = root,
                                filesByName = filesByName,
                                remoteAttachments = remoteAttachments,
                                folderById = folderById,
                                newAttachments = newAttachments,
                            )
                            newNoteMeta[id] = uploaded.meta
                            uploadedNotes++
                            uploadedAttachments += uploaded.attachmentUploads
                        }
                    }
                }
            }
        }

        // Keep attachments referenced by remote notes that were not re-downloaded.
        remoteAttachments.values.forEach { meta ->
            if (meta.noteId in localNotes.keys) newAttachments.putIfAbsent(meta.id, meta)
        }

        val manifestOut = DriveManifest(
            schemaVersion = 1,
            folders = repository.getAllFolders().map {
                RemoteFolder(
                    id = it.id,
                    name = it.name,
                    colorHex = it.colorHex,
                    iconKey = it.iconKey,
                )
            },
            notes = newNoteMeta.values.sortedBy { it.id },
            attachments = newAttachments.mapKeys { it.key.toString() },
        )
        val manifestBytes = json.encodeToString(manifestOut).toByteArray(Charsets.UTF_8)
        if (manifestFile == null) {
            api.createFile(
                root.id,
                MANIFEST_NAME,
                "application/json",
                manifestBytes,
                mapOf("kriboSchema" to "1"),
            )
        } else {
            api.updateFile(
                manifestFile.id,
                MANIFEST_NAME,
                "application/json",
                manifestBytes,
                mapOf("kriboSchema" to "1"),
            )
        }

        val now = System.currentTimeMillis()
        auth.saveLastSync(now, repository.getAllNotes().size, newAttachments.size)
        return DriveSyncResult.Success(
            notes = repository.getAllNotes().size,
            attachments = newAttachments.size,
        )
    }

    private suspend fun downloadRemoteNote(
        meta: RemoteNoteMeta,
        file: DriveFile?,
    ): RemoteNote? {
        if (file == null) return null
        return runCatching {
            json.decodeFromString<RemoteNote>(api.downloadText(file.id))
        }.getOrNull()
    }

    private suspend fun applyRemoteNote(
        remote: RemoteNote,
        remoteFolderToLocal: Map<Long, Long>,
        folderById: Map<Long, com.blackeye.kribo.data.FolderEntity>,
        filesByName: Map<String, DriveFile>,
        newAttachments: MutableMap<Long, RemoteAttachmentMeta>,
    ) {
        val localFolderId = remoteFolderToLocal[remote.folderId]
            ?: remote.folderName?.let { name ->
                folderById.values.firstOrNull {
                    it.name.equals(name, ignoreCase = true)
                }?.id
            }
            ?: DEFAULT_FOLDER_GENERAL

        val existingBlocks = BlockCodec.decode(remote.blocks.toString())
        // The note must exist before attachment rows can satisfy their foreign key.
        repository.upsertSyncedNote(
            noteId = remote.id,
            folderId = localFolderId,
            title = remote.title,
            blocks = existingBlocks,
            updatedAt = remote.updatedAt,
            remoteOcrText = remote.ocrText,
            fontStyle = remote.fontStyle,
            isPinned = remote.isPinned,
        )

        val existingAttachments = repository.getAttachments(remote.id)
        val existingByName = existingAttachments.associateBy {
            it.filePath.substringAfterLast('/')
        }
        val remoteToLocal = mutableMapOf<Long, Long>()
        remote.attachments.forEach { ref ->
            val existing = existingByName[ref.fileName]
                ?.takeIf { sha256(File(it.filePath)) == ref.sha256 }
            val localId = if (existing != null) {
                existing.id
            } else {
                val extension = ref.fileName.substringAfterLast('.', "jpg")
                val remoteFile = filesByName["att-${ref.id}.$extension"]
                    ?: filesByName.entries.firstOrNull {
                        it.key.startsWith("att-${ref.id}.")
                    }?.value
                val bytes = remoteFile?.let { api.downloadBytes(it.id) } ?: return@forEach
                val file = attachmentStore.saveBytes(remote.id, ref.fileName, bytes)
                repository.addAttachment(
                    noteId = remote.id,
                    filePath = file.absolutePath,
                    mimeType = ref.mimeType,
                    extractedText = "",
                )
            }
            remoteToLocal[ref.id] = localId
            newAttachments[ref.id] = RemoteAttachmentMeta(
                id = ref.id,
                sha256 = ref.sha256,
                mimeType = ref.mimeType,
                fileName = ref.fileName,
                noteId = remote.id,
            )
        }

        if (remoteToLocal.isNotEmpty()) {
            val mapped = existingBlocks.map { block ->
                if (block is AttachmentBlock && block.attachmentId != null) {
                    block.copy(attachmentId = remoteToLocal[block.attachmentId] ?: block.attachmentId)
                } else {
                    block
                }
            }
            repository.upsertSyncedNote(
                noteId = remote.id,
                folderId = localFolderId,
                title = remote.title,
                blocks = mapped,
                updatedAt = remote.updatedAt,
                remoteOcrText = remote.ocrText,
                fontStyle = remote.fontStyle,
                isPinned = remote.isPinned,
            )
        }
        tombstones.clear(remote.id)
    }

    private data class UploadedNote(
        val meta: RemoteNoteMeta,
        val attachmentUploads: Int,
    )

    private suspend fun uploadLocalNote(
        note: NoteEntity,
        root: DriveFile,
        filesByName: Map<String, DriveFile>,
        remoteAttachments: Map<Long, RemoteAttachmentMeta>,
        folderById: Map<Long, com.blackeye.kribo.data.FolderEntity>,
        newAttachments: MutableMap<Long, RemoteAttachmentMeta>,
    ): UploadedNote {
        val blocks = BlockCodec.decode(note.blocksJson)
        val refs = mutableListOf<RemoteAttachmentRef>()
        var uploads = 0
        repository.getAttachments(note.id).forEach { attachment ->
            val file = File(attachment.filePath)
            if (!file.isFile) return@forEach
            val hash = sha256(file)
            val extension = file.extension.ifBlank { "bin" }
            val remoteName = "att-${attachment.id}.$extension"
            val remoteFile = filesByName[remoteName]
            val previous = remoteAttachments[attachment.id]
            if (previous?.sha256 != hash || remoteFile == null) {
                val bytes = file.readBytes()
                val props = mapOf("kriboAttachmentId" to attachment.id.toString())
                if (remoteFile == null) {
                    api.createFile(root.id, remoteName, attachment.mimeType, bytes, props)
                } else {
                    api.updateFile(remoteFile.id, remoteName, attachment.mimeType, bytes, props)
                }
                uploads++
            }
            refs += RemoteAttachmentRef(
                id = attachment.id,
                fileName = file.name,
                mimeType = attachment.mimeType,
                sha256 = hash,
            )
            newAttachments[attachment.id] = RemoteAttachmentMeta(
                id = attachment.id,
                sha256 = hash,
                mimeType = attachment.mimeType,
                fileName = file.name,
                noteId = note.id,
            )
        }

        val remoteNote = RemoteNote(
            id = note.id,
            folderId = note.folderId,
            folderName = note.folderId?.let { folderById[it]?.name },
            title = note.title,
            updatedAt = note.updatedAt,
            deletedAt = null,
            isPinned = note.isPinned,
            blocks = json.parseToJsonElement(note.blocksJson),
            plainText = note.plainTextIndex,
            ocrText = note.ocrTextIndex,
            fontStyle = note.fontStyle,
            attachments = refs,
        )
        val name = "note-${note.id}.json"
        val bytes = json.encodeToString(remoteNote).toByteArray(Charsets.UTF_8)
        val existing = filesByName[name]
        if (existing == null) {
            api.createFile(root.id, name, "application/json", bytes)
        } else {
            api.updateFile(existing.id, name, "application/json", bytes)
        }
        return UploadedNote(
            meta = RemoteNoteMeta(
                id = note.id,
                updatedAt = note.updatedAt,
                deletedAt = null,
                file = name,
            ),
            attachmentUploads = uploads,
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun mapFailure(throwable: Throwable): DriveSyncResult {
        val kind = when (throwable) {
            is DriveAuthRequiredException -> DriveSyncError.AUTH
            is DriveApiException -> when (throwable.statusCode) {
                401 -> DriveSyncError.AUTH
                403 -> DriveSyncError.OAUTH_CONFIGURATION
                else -> DriveSyncError.NETWORK
            }
            is java.io.IOException -> DriveSyncError.NETWORK
            else -> DriveSyncError.UNKNOWN
        }
        return DriveSyncResult.Error(kind)
    }

    companion object {
        const val MANIFEST_NAME = "sync-manifest.json"
        const val SCHEMA_VERSION = 1
    }
}
