package com.blackeye.kribo.drive

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blackeye.kribo.data.KriboDatabase
import com.blackeye.kribo.data.NoteRepository
import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.seedDefaultFolders
import com.blackeye.kribo.ocr.AttachmentStore
import com.blackeye.kribo.security.InMemorySecureStore
import java.io.File
import java.io.IOException
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
        repository = NoteRepository(database, root) { id, deletedAt ->
            tombstones.mark(id, deletedAt)
        }
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

        fun seed(name: String, bytes: ByteArray, mimeType: String): DriveFile {
            val id = "seed-${counter++}"
            val file = DriveFile(id, name, mimeType)
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
}
