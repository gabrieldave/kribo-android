package com.blackeye.kribo.drive

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blackeye.kribo.data.DEFAULT_FOLDER_GENERAL
import com.blackeye.kribo.data.KriboDatabase
import com.blackeye.kribo.data.NoteRepository
import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.data.blocks.InlineSpan
import com.blackeye.kribo.data.blocks.ListBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.SpanType
import com.blackeye.kribo.data.seedDefaultFolders
import com.blackeye.kribo.ocr.AttachmentStore
import com.blackeye.kribo.security.InMemorySecureStore
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DriveSyncManagerTest {

    private lateinit var database: KriboDatabase
    private lateinit var repository: NoteRepository
    private lateinit var attachmentStore: AttachmentStore
    private lateinit var tombstones: DriveTombstoneStore
    private lateinit var api: FakeDriveApiClient
    private lateinit var auth: FakeDriveAuth
    private lateinit var manager: DriveSyncManager
    private lateinit var root: File

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = File(context.cacheDir, "drive-test-${System.currentTimeMillis()}").apply { mkdirs() }
        database = Room.inMemoryDatabaseBuilder(context, KriboDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedDefaultFolders(database.openHelper.writableDatabase)
        tombstones = DriveTombstoneStore(InMemorySecureStore())
        repository = NoteRepository(
            database = database,
            attachmentsRoot = root,
            onNoteDeleted = { id, deletedAt -> tombstones.mark(id, deletedAt) },
            onFolderDeleted = { folderId -> tombstones.markFolderDeleted(folderId) },
        )
        attachmentStore = AttachmentStore(root)
        api = FakeDriveApiClient()
        auth = FakeDriveAuth()
        manager = DriveSyncManager(repository, attachmentStore, api, tombstones, auth)
    }

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun initialUploadCreatesManifestNoteAndAttachment() = runBlocking {
        val noteId = repository.createNote(
            folderId = 1L,
            blocks = listOf(ParagraphBlock(text = "Hola desde Kribo")),
        )
        val file = attachmentStore.saveBytes(noteId, "photo.jpg", byteArrayOf(1, 2, 3, 4))
        repository.addAttachment(noteId, file.absolutePath, "image/jpeg", "texto")

        val result = manager.sync()
        assertTrue("sync failed: $result", result is DriveSyncResult.Success)
        assertNotNull(api.text("sync-manifest.json"))
        assertTrue(api.text("note-$noteId.json")!!.contains("Hola desde Kribo"))
        assertTrue(api.names().any { it.startsWith("att-$noteId") || it.startsWith("att-") })
        val manifest = json.decodeFromString<DriveManifest>(api.text("sync-manifest.json")!!)
        assertTrue(manifest.notes.any { it.id == noteId })
        assertTrue(manifest.attachments.isNotEmpty())
        assertEquals(1, api.attachmentUploads)
    }

    @Test
    fun downloadsNewRemoteNote() = runBlocking {
        seedRemoteNote(remoteNote(42L, 1_000L, "Nota remota"))

        val result = manager.sync()
        assertTrue(result is DriveSyncResult.Success)
        val imported = repository.getNote(42L)
        assertNotNull(imported)
        assertEquals("Nota remota", imported!!.title)
        assertTrue(BlockCodec.decode(imported.blocksJson).any {
            it is ParagraphBlock && it.text == "Nota remota"
        })
    }

    @Test
    fun remoteNewerWinsOverLocalNote() = runBlocking {
        repository.upsertSyncedNote(
            noteId = 7L,
            folderId = 1L,
            title = "Vieja",
            blocks = listOf(ParagraphBlock(text = "texto viejo")),
            updatedAt = 1_000L,
        )
        seedRemoteNote(remoteNote(7L, 2_000L, "texto nuevo"))

        manager.sync()
        val local = repository.getNote(7L)!!
        assertEquals(2_000L, local.updatedAt)
        assertTrue(BlockCodec.decode(local.blocksJson).any {
            it is ParagraphBlock && it.text == "texto nuevo"
        })
    }

    @Test
    fun tombstonesPreventResurrection() = runBlocking {
        val id = repository.createNote(1L, listOf(ParagraphBlock(text = "borrar")))
        repository.deleteNote(id)
        assertTrue(tombstones.all().containsKey(id))

        manager.sync()
        val manifest = json.decodeFromString<DriveManifest>(api.text("sync-manifest.json")!!)
        assertNotNull(manifest.notes.firstOrNull { it.id == id }?.deletedAt)

        seedRemoteNote(remoteNote(id, 500L, "no debe volver"))
        manager.sync()
        assertEquals(null, repository.getNote(id))
    }

    @Test
    fun attachmentsAreDeduplicatedBySha256() = runBlocking {
        val id = repository.createNote(1L, listOf(ParagraphBlock(text = "con foto")))
        val file = attachmentStore.saveBytes(id, "photo.jpg", byteArrayOf(9, 8, 7))
        repository.addAttachment(id, file.absolutePath, "image/jpeg", "")

        manager.sync()
        val firstUploads = api.attachmentUploads
        assertEquals(1, firstUploads)

        manager.sync()
        assertEquals(firstUploads, api.attachmentUploads)
    }

    @Test
    fun networkFailureNeverDeletesLocalNotes() = runBlocking {
        val id = repository.createNote(1L, listOf(ParagraphBlock(text = "local")))
        api.fail = true

        val result = manager.sync()
        assertTrue(result is DriveSyncResult.Error)
        assertEquals(DriveSyncError.NETWORK, (result as DriveSyncResult.Error).kind)
        assertNotNull(repository.getNote(id))
    }

    @Test
    fun syncsIsPinnedFolderStyleAndListBlocksBothWays() = runBlocking {
        val localFolderId = repository.createFolder(
            name = "Proyectos",
            iconKey = "work",
            colorHex = "#10B981",
        )!!
        val noteId = repository.createNote(
            folderId = localFolderId,
            blocks = listOf(
                ListBlock(
                    text = "Paso uno",
                    ordered = true,
                    spans = listOf(InlineSpan(0, 4, SpanType.BOLD)),
                ),
                ListBlock(text = "Viñeta", ordered = false),
            ),
            title = "Checklist Web",
        )
        repository.setPinned(noteId, true)

        assertTrue(manager.sync() is DriveSyncResult.Success)

        val uploadedManifest = json.decodeFromString<DriveManifest>(api.text("sync-manifest.json")!!)
        val uploadedFolder = uploadedManifest.folders.first { it.name == "Proyectos" }
        assertEquals("#10B981", uploadedFolder.colorHex)
        assertEquals("work", uploadedFolder.iconKey)

        val uploadedNote = json.decodeFromString<RemoteNote>(api.text("note-$noteId.json")!!)
        assertEquals(true, uploadedNote.isPinned)
        val uploadedBlocks = BlockCodec.decode(uploadedNote.blocks.toString())
        assertEquals(2, uploadedBlocks.size)
        assertTrue((uploadedBlocks[0] as ListBlock).ordered)
        assertFalse((uploadedBlocks[1] as ListBlock).ordered)

        // Now download a remote folder with colorHex/iconKey and a pinned remote note with ListBlock.
        val webFolder = RemoteFolder(
            id = 1_764_200_000_001L,
            name = "Mac",
            colorHex = "#F59E0B",
            iconKey = "laptop",
        )
        val webNoteId = 1_764_201_234_567L
        val webBlocks = listOf(
            ListBlock(id = "web-1", text = "Compilar Debug", ordered = true),
            ListBlock(id = "web-2", text = "Auditar UI", ordered = true),
            ListBlock(id = "web-3", text = "Nota suelta", ordered = false),
        )
        val webRemoteNote = RemoteNote(
            id = webNoteId,
            folderId = webFolder.id,
            folderName = webFolder.name,
            title = "Desde Web",
            updatedAt = 1_764_201_234_999L,
            isPinned = true,
            blocks = json.parseToJsonElement(BlockCodec.encode(webBlocks)),
            plainText = BlockCodec.plainText(webBlocks),
        )
        api.seed(
            "note-$webNoteId.json",
            json.encodeToString(webRemoteNote).toByteArray(),
            "application/json",
        )
        val updatedManifest = uploadedManifest.copy(
            folders = uploadedManifest.folders + webFolder,
            notes = uploadedManifest.notes + RemoteNoteMeta(
                id = webNoteId,
                updatedAt = webRemoteNote.updatedAt,
                deletedAt = null,
                file = "note-$webNoteId.json",
            ),
        )
        api.seed(
            "sync-manifest.json",
            json.encodeToString(updatedManifest).toByteArray(),
            "application/json",
        )

        assertTrue(manager.sync() is DriveSyncResult.Success)

        val importedFolder = repository.getAllFolders().first { it.name == "Mac" }
        assertEquals("#F59E0B", importedFolder.colorHex)
        assertEquals("laptop", importedFolder.iconKey)

        val importedNote = repository.getNote(webNoteId)!!
        assertEquals(importedFolder.id, importedNote.folderId)
        assertEquals(true, importedNote.isPinned)
        val importedBlocks = BlockCodec.decode(importedNote.blocksJson)
        assertEquals(3, importedBlocks.size)
        assertTrue((importedBlocks[0] as ListBlock).ordered)
        assertEquals("Compilar Debug", (importedBlocks[0] as ListBlock).text)
        assertFalse((importedBlocks[2] as ListBlock).ordered)

        // Delete the 13-digit Web note locally and verify tombstone persists and prevents resurrection.
        repository.deleteNote(webNoteId)
        assertTrue(tombstones.all().containsKey(webNoteId))
        assertTrue(manager.sync() is DriveSyncResult.Success)
        assertEquals(null, repository.getNote(webNoteId))
        val postDeleteManifest = json.decodeFromString<DriveManifest>(api.text("sync-manifest.json")!!)
        assertNotNull(postDeleteManifest.notes.first { it.id == webNoteId }.deletedAt)
    }

    @Test
    fun httpDriveApiClientPaginatesSearchFilesUntilNextPageTokenEnds() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val client = HttpDriveApiClient(
            tokenProvider = { "test-token" },
            requestExecutor = { url, _, _, _ ->
                requestedUrls += url
                when (requestedUrls.size) {
                    1 -> """
                        {
                          "nextPageToken": "tok+2/==",
                          "files": [
                            {"id": "f1", "name": "note-1.json", "mimeType": "application/json"},
                            {"id": "f2", "name": "note-2.json", "mimeType": "application/json"}
                          ]
                        }
                    """.trimIndent().toByteArray()
                    2 -> """
                        {
                          "nextPageToken": "tok-3",
                          "files": [
                            {"id": "f3", "name": "note-3.json", "mimeType": "application/json"}
                          ]
                        }
                    """.trimIndent().toByteArray()
                    else -> """
                        {
                          "files": [
                            {"id": "f4", "name": "sync-manifest.json", "mimeType": "application/json"}
                          ]
                        }
                    """.trimIndent().toByteArray()
                }
            },
        )

        val files = client.listFiles("root-folder")

        assertEquals(3, requestedUrls.size)
        assertTrue(requestedUrls[0].contains("fields=nextPageToken,files("))
        assertFalse(requestedUrls[0].contains("pageToken="))
        assertTrue(requestedUrls[1].contains("pageToken=tok%2B2%2F%3D%3D"))
        assertTrue(requestedUrls[2].contains("pageToken=tok-3"))
        assertEquals(listOf("f1", "f2", "f3", "f4"), files.map { it.id })
    }

    @Test
    fun remoteFolderRenameColorAndIconUpdateInPlaceWithoutDuplicating() = runBlocking {
        val folderId = repository.createFolder(
            name = "Trabajo",
            iconKey = "folder",
            colorHex = "#00D8FF",
        )!!
        val noteId = repository.createNote(
            folderId = folderId,
            blocks = listOf(ParagraphBlock(text = "Pendientes")),
            title = "Oficina",
        )
        assertTrue(manager.sync() is DriveSyncResult.Success)
        val countBefore = repository.getAllFolders().size

        val manifest = json.decodeFromString<DriveManifest>(api.text("sync-manifest.json")!!)
        val editedManifest = manifest.copy(
            folders = manifest.folders.map { folder ->
                if (folder.id == folderId) {
                    folder.copy(name = "Clientes", colorHex = "#F59E0B", iconKey = "work")
                } else {
                    folder
                }
            },
        )
        api.seed(
            "sync-manifest.json",
            json.encodeToString(editedManifest).toByteArray(),
            "application/json",
        )

        assertTrue(manager.sync() is DriveSyncResult.Success)

        val foldersAfter = repository.getAllFolders()
        assertEquals(countBefore, foldersAfter.size)
        assertFalse(foldersAfter.any { it.name == "Trabajo" })
        val renamed = foldersAfter.first { it.id == folderId }
        assertEquals("Clientes", renamed.name)
        assertEquals("#F59E0B", renamed.colorHex)
        assertEquals("work", renamed.iconKey)
        assertEquals(folderId, repository.getNote(noteId)!!.folderId)
    }

    @Test
    fun remoteFolderDeletionMovesOrphanNotesToGeneralAndProtectsGeneral() = runBlocking {
        val tempFolderId = repository.createFolder(name = "Temporal", colorHex = "#EF4444")!!
        val noteId = repository.createNote(
            folderId = tempFolderId,
            blocks = listOf(ParagraphBlock(text = "Nota huérfana")),
            title = "Huérfana",
        )
        assertTrue(manager.sync() is DriveSyncResult.Success)

        // Simulate Kribo Web deleting "Temporal" (and verify General id 1 is protected even if absent).
        val manifest = json.decodeFromString<DriveManifest>(api.text("sync-manifest.json")!!)
        val withoutTemp = manifest.copy(
            folders = manifest.folders.filterNot {
                it.id == tempFolderId || it.id == DEFAULT_FOLDER_GENERAL
            },
        )
        api.seed(
            "sync-manifest.json",
            json.encodeToString(withoutTemp).toByteArray(),
            "application/json",
        )

        assertTrue(manager.sync() is DriveSyncResult.Success)

        val foldersAfter = repository.getAllFolders()
        assertFalse(foldersAfter.any { it.id == tempFolderId })
        assertTrue(foldersAfter.any { it.id == DEFAULT_FOLDER_GENERAL })
        assertEquals(DEFAULT_FOLDER_GENERAL, repository.getNote(noteId)!!.folderId)
    }

    @Test
    fun downloadsWebAttachmentWithoutOcrAndNeverDuplicates() = runBlocking {
        val webNoteId = 1_764_209_000_001L
        val webAttId = 1_764_209_000_002L
        val imageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
        val imageHash = sha256Hex(imageBytes)
        val blocks = listOf(
            ParagraphBlock(id = "p-1", text = "Captura desde Mac"),
            AttachmentBlock(
                id = "att-blk-1",
                attachmentId = webAttId,
                fileName = "Captura.PNG",
                mimeType = "image/png",
                previewText = "",
                pageNumber = 0,
                manual = true,
            ),
        )
        val remote = RemoteNote(
            id = webNoteId,
            folderId = DEFAULT_FOLDER_GENERAL,
            folderName = "General",
            title = "Nota con foto PC",
            updatedAt = 1_764_209_000_500L,
            blocks = json.parseToJsonElement(BlockCodec.encode(blocks)),
            plainText = "Captura desde Mac",
            ocrText = "",
            attachments = listOf(
                RemoteAttachmentRef(
                    id = webAttId,
                    fileName = "Captura.PNG",
                    mimeType = "image/png",
                    sha256 = imageHash,
                ),
            ),
        )
        // Kribo Web stores lowercase extension + kriboAttachmentId in appProperties.
        api.seed(
            name = "att-$webAttId.png",
            bytes = imageBytes,
            mimeType = "image/png",
            appProperties = mapOf("kriboAttachmentId" to webAttId.toString()),
        )
        api.seed(
            name = "note-$webNoteId.json",
            bytes = json.encodeToString(remote).toByteArray(),
            mimeType = "application/json",
        )
        val manifest = DriveManifest(
            schemaVersion = 1,
            folders = listOf(RemoteFolder(DEFAULT_FOLDER_GENERAL, "General")),
            notes = listOf(
                RemoteNoteMeta(
                    id = webNoteId,
                    updatedAt = remote.updatedAt,
                    deletedAt = null,
                    file = "note-$webNoteId.json",
                ),
            ),
            attachments = mapOf(
                webAttId.toString() to RemoteAttachmentMeta(
                    id = webAttId,
                    sha256 = imageHash,
                    mimeType = "image/png",
                    fileName = "Captura.PNG",
                    noteId = webNoteId,
                ),
            ),
        )
        api.seed("sync-manifest.json", json.encodeToString(manifest).toByteArray(), "application/json")

        assertTrue(manager.sync() is DriveSyncResult.Success)

        val localAttachments = repository.getAttachments(webNoteId)
        assertEquals(1, localAttachments.size)
        val downloaded = localAttachments.first()
        assertEquals(webAttId, downloaded.id)
        assertEquals("", downloaded.extractedText)
        assertTrue(File(downloaded.filePath).isFile)
        assertEquals(imageHash, sha256Hex(File(downloaded.filePath).readBytes()))

        val localNote = repository.getNote(webNoteId)!!
        val attBlock = BlockCodec.decode(localNote.blocksJson).filterIsInstance<AttachmentBlock>().single()
        assertEquals(downloaded.id, attBlock.attachmentId)
        assertEquals("", attBlock.previewText)

        // Syncing a second time (and even after a remote text edit) must never duplicate the attachment.
        val uploadsBefore = api.attachmentUploads
        assertTrue(manager.sync() is DriveSyncResult.Success)
        assertEquals(1, repository.getAttachments(webNoteId).size)
        assertEquals(uploadsBefore, api.attachmentUploads)

        val updatedRemote = remote.copy(updatedAt = remote.updatedAt + 5_000L)
        api.seed("note-$webNoteId.json", json.encodeToString(updatedRemote).toByteArray(), "application/json")
        val manifest2 = json.decodeFromString<DriveManifest>(api.text("sync-manifest.json")!!)
        api.seed(
            "sync-manifest.json",
            json.encodeToString(
                manifest2.copy(
                    notes = manifest2.notes.map {
                        if (it.id == webNoteId) it.copy(updatedAt = updatedRemote.updatedAt) else it
                    },
                ),
            ).toByteArray(),
            "application/json",
        )
        assertTrue(manager.sync() is DriveSyncResult.Success)
        assertEquals(1, repository.getAttachments(webNoteId).size)
    }

    @Test
    fun autoSyncDebouncesEditsAndSerializesConcurrentTriggersWithMutex() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val active = AtomicInteger(0)
            val maxActive = AtomicInteger(0)
            val runs = AtomicInteger(0)
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val secondDone = CompletableDeferred<Unit>()
            val network = FakeNetworkMonitor(online = true, wifi = true)

            val scheduler = DriveAutoSyncScheduler(
                syncAction = {
                    val current = active.incrementAndGet()
                    maxActive.accumulateAndGet(current, ::maxOf)
                    val runNumber = runs.incrementAndGet()
                    if (runNumber == 1) {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    }
                    active.decrementAndGet()
                    if (runNumber == 2) secondDone.complete(Unit)
                    DriveSyncResult.Success(notes = 1, attachments = 0)
                },
                isPro = { true },
                isDriveConnected = { true },
                isAutoSyncEnabled = { true },
                isWifiOnly = { true },
                network = network,
                scope = scope,
                debounceMs = 60L,
                foregroundCooldownMs = 45_000L,
                syncedBadgeMs = 80L,
            )

            // Five rapid edits must coalesce into one single sync run.
            repeat(5) {
                scheduler.onLocalDataChanged()
                delay(15L)
            }
            firstStarted.await()
            assertEquals(AutoSyncUiState.SYNCING, scheduler.state.value)

            // Trigger another edit while run #1 is still in flight; mutex must serialize run #2.
            scheduler.onLocalDataChanged()
            delay(90L)
            assertEquals(1, runs.get())
            releaseFirst.complete(Unit)
            secondDone.await()

            assertEquals(1, maxActive.get())
            assertEquals(2, runs.get())
            delay(130L)
            assertEquals(AutoSyncUiState.IDLE, scheduler.state.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun autoSyncRespectsWifiOnlyQueuesRetryAndForegroundCooldown() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            var pro = false
            var wifiOnly = true
            var clock = 100_000L
            val runs = AtomicInteger(0)
            val network = FakeNetworkMonitor(online = true, wifi = false)

            val scheduler = DriveAutoSyncScheduler(
                syncAction = {
                    runs.incrementAndGet()
                    DriveSyncResult.Success(notes = 1, attachments = 0)
                },
                isPro = { pro },
                isDriveConnected = { true },
                isAutoSyncEnabled = { true },
                isWifiOnly = { wifiOnly },
                network = network,
                scope = scope,
                debounceMs = 40L,
                foregroundCooldownMs = 45_000L,
                syncedBadgeMs = 50L,
                nowMs = { clock },
            )

            // Free user: must never schedule or run sync.
            scheduler.onLocalDataChanged()
            delay(80L)
            assertEquals(0, runs.get())
            assertFalse(scheduler.hasPendingRetry)

            // PRO user on cellular with Wi-Fi Only ON: must queue retry silently without syncing.
            pro = true
            scheduler.onLocalDataChanged()
            delay(80L)
            assertEquals(0, runs.get())
            assertTrue(scheduler.hasPendingRetry)
            assertTrue(network.registered)
            assertEquals(AutoSyncUiState.IDLE, scheduler.state.value)

            // Regaining Wi-Fi triggers the queued retry automatically and unregisters callback.
            network.wifi = true
            network.fireReconnect()
            delay(80L)
            assertEquals(1, runs.get())
            assertFalse(scheduler.hasPendingRetry)
            assertFalse(network.registered)

            // Foreground within 45s cooldown must be skipped.
            clock += 10_000L
            scheduler.onAppForeground()
            delay(60L)
            assertEquals(1, runs.get())

            // Foreground after 45s cooldown runs cleanly.
            clock += 46_000L
            scheduler.onAppForeground()
            delay(60L)
            assertEquals(2, runs.get())
        } finally {
            scope.cancel()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun remoteNote(id: Long, updatedAt: Long, text: String): RemoteNote {
        val blocks = BlockCodec.encode(listOf(ParagraphBlock(text = text)))
        return RemoteNote(
            id = id,
            folderId = 1L,
            folderName = "General",
            title = text,
            updatedAt = updatedAt,
            blocks = json.parseToJsonElement(blocks),
            plainText = text,
        )
    }

    private fun seedRemoteNote(note: RemoteNote) {
        val noteName = "note-${note.id}.json"
        api.seed(noteName, json.encodeToString(note).toByteArray(), "application/json")
        val manifest = DriveManifest(
            schemaVersion = 1,
            folders = listOf(RemoteFolder(1L, "General")),
            notes = listOf(
                RemoteNoteMeta(
                    id = note.id,
                    updatedAt = note.updatedAt,
                    deletedAt = note.deletedAt,
                    file = noteName,
                ),
            ),
            attachments = emptyMap(),
        )
        api.seed(
            "sync-manifest.json",
            json.encodeToString(manifest).toByteArray(),
            "application/json",
        )
    }

    private class FakeDriveAuth : DriveAuthSource {
        var token: String? = "token"
        var session = DriveSession(
            email = "test@example.com",
            accessToken = "token",
            tokenExpiry = Long.MAX_VALUE,
        )

        override fun validToken(): String? = token
        override fun sessionSnapshot(): DriveSession = session
        override fun saveRootFolder(id: String) {
            session = session.copy(rootFolderId = id)
        }

        override fun saveLastSync(at: Long, notes: Int, attachments: Int) {
            session = session.copy(lastSyncAt = at, lastNotes = notes, lastAttachments = attachments)
        }
    }

    private class FakeDriveApiClient : DriveApiClient {
        var fail = false
        var attachmentUploads = 0
        private var counter = 0
        private val files = linkedMapOf<String, DriveFile>()
        private val contents = mutableMapOf<String, ByteArray>()
        private val root = DriveFile("root", "Kribo", "application/vnd.google-apps.folder")

        override suspend fun ensureRootFolder(existingId: String?): DriveFile {
            checkOnline()
            return root
        }

        override suspend fun listFiles(folderId: String): List<DriveFile> {
            checkOnline()
            return files.values.toList()
        }

        override suspend fun downloadText(fileId: String): String {
            checkOnline()
            return String(contents[fileId] ?: throw IOException("missing"))
        }

        override suspend fun downloadBytes(fileId: String): ByteArray {
            checkOnline()
            return contents[fileId] ?: throw IOException("missing")
        }

        override suspend fun createFile(
            folderId: String,
            name: String,
            mimeType: String,
            bytes: ByteArray,
            appProperties: Map<String, String>,
        ): DriveFile {
            checkOnline()
            val id = "new-${counter++}"
            val file = DriveFile(id, name, mimeType, appProperties = appProperties)
            files[id] = file
            contents[id] = bytes
            if (name.startsWith("att-")) attachmentUploads++
            return file
        }

        override suspend fun updateFile(
            fileId: String,
            name: String,
            mimeType: String,
            bytes: ByteArray,
            appProperties: Map<String, String>,
        ): DriveFile {
            checkOnline()
            val file = DriveFile(fileId, name, mimeType, appProperties = appProperties)
            files[fileId] = file
            contents[fileId] = bytes
            if (name.startsWith("att-")) attachmentUploads++
            return file
        }

        override suspend fun deleteFile(fileId: String) {
            checkOnline()
            files.remove(fileId)
            contents.remove(fileId)
        }

        fun seed(
            name: String,
            bytes: ByteArray,
            mimeType: String,
            appProperties: Map<String, String> = emptyMap(),
        ): DriveFile {
            val existing = files.values.firstOrNull { it.name == name }
            val id = existing?.id ?: "seed-${counter++}"
            val file = DriveFile(id, name, mimeType, appProperties = appProperties)
            files[id] = file
            contents[id] = bytes
            return file
        }

        fun text(name: String): String? =
            files.values.firstOrNull { it.name == name }?.let {
                contents[it.id]?.toString(Charsets.UTF_8)
            }

        fun names(): List<String> = files.values.map { it.name }

        private fun checkOnline() {
            if (fail) throw IOException("network down")
        }
    }

    private class FakeNetworkMonitor(
        var online: Boolean,
        var wifi: Boolean,
    ) : DriveNetworkMonitor {
        var registered = false
            private set
        private var callback: (() -> Unit)? = null

        override fun isOnline(): Boolean = online
        override fun isUnmeteredWifi(): Boolean = online && wifi

        override fun registerForReconnect(onAvailable: () -> Unit) {
            registered = true
            callback = onAvailable
        }

        override fun unregister() {
            registered = false
            callback = null
        }

        fun fireReconnect() {
            callback?.invoke()
        }
    }
}
