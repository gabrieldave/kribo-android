package com.blackeye.kribo.voice

import ai.moonshine.voice.JNI
import com.blackeye.kribo.ui.settings.AppLanguage
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoiceModelManagerTest {

    private lateinit var tempRoot: File

    @Before
    fun setUp() {
        tempRoot = File(
            System.getProperty("java.io.tmpdir"),
            "kribo-voice-model-test-${System.nanoTime()}",
        ).apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    @Test
    fun catalogUsesOnlyMitTinyStreamingPackages() {
        VoiceModelLanguage.entries.forEach { lang ->
            assertEquals(JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING, lang.modelArch)
            assertTrue(
                "Expected MIT tiny-streaming package for ${lang.code}, got ${lang.baseUrl}",
                lang.baseUrl.contains("/tiny-streaming-${lang.code}/"),
            )
            assertEquals(8, lang.files.size)
            assertTrue(lang.approxMb in 20..60)
        }
    }

    @Test
    fun crc32cMatchesRfc3720StandardTestVector() {
        val sample = File(tempRoot, "rfc3720.txt").apply { writeText("123456789") }
        // Standard Castagnoli CRC32C of "123456789" is 0xE3069283 -> Base64 "4waSgw==".
        assertEquals("4waSgw==", Crc32c.checksumBase64(sample))
    }

    @Test
    fun resolveVoiceModelLanguageMapsAppAndSystemLocales() {
        assertEquals(VoiceModelLanguage.SPANISH, resolveVoiceModelLanguage(AppLanguage.SPANISH, Locale.US))
        assertEquals(VoiceModelLanguage.ENGLISH, resolveVoiceModelLanguage(AppLanguage.ENGLISH, Locale("es", "MX")))
        assertEquals(VoiceModelLanguage.SPANISH, resolveVoiceModelLanguage(AppLanguage.SYSTEM, Locale("es", "ES")))
        assertEquals(VoiceModelLanguage.ENGLISH, resolveVoiceModelLanguage(AppLanguage.SYSTEM, Locale.UK))
    }

    @Test
    fun atomicDownloadCompletesValidatesAndDeletesCleanly() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val specsByUrl = VoiceModelLanguage.SPANISH.files.associateBy {
                VoiceModelLanguage.SPANISH.fileUrl(it)
            }
            val downloader = object : VoiceFileDownloader {
                override suspend fun download(
                    url: String,
                    destination: File,
                    onBytesRead: (deltaBytes: Long) -> Unit,
                ) {
                    val spec = specsByUrl.getValue(url)
                    destination.parentFile?.mkdirs()
                    // Write exact expected byte count sparsely via RandomAccessFile for instant test speed.
                    java.io.RandomAccessFile(destination, "rw").use { raf ->
                        raf.setLength(spec.expectedBytes)
                    }
                    onBytesRead(spec.expectedBytes)
                }
            }

            val done = CompletableDeferred<Boolean>()
            val manager = VoiceModelManager(
                modelsRoot = File(tempRoot, "voice_models"),
                downloader = downloader,
                freeSpaceBytes = { 500L * 1024L * 1024L },
                verifyChecksums = false,
                scope = scope,
            )

            assertFalse(manager.isInstalled(VoiceModelLanguage.SPANISH))
            assertNull(manager.modelDir(VoiceModelLanguage.SPANISH))

            manager.startDownload(VoiceModelLanguage.SPANISH) { ok -> done.complete(ok) }
            assertTrue(done.await())

            val state = manager.stateOf(VoiceModelLanguage.SPANISH)
            assertTrue("Expected Ready state, got $state", state is VoiceModelDownloadState.Ready)
            assertTrue(manager.isInstalled(VoiceModelLanguage.SPANISH))
            val dir = manager.modelDir(VoiceModelLanguage.SPANISH)
            assertNotNull(dir)
            assertFalse(File(tempRoot, "voice_models/es.tmp").exists())

            manager.deleteModel(VoiceModelLanguage.SPANISH)
            assertFalse(manager.isInstalled(VoiceModelLanguage.SPANISH))
            assertEquals(VoiceModelDownloadState.NotDownloaded, manager.stateOf(VoiceModelLanguage.SPANISH))
            assertFalse(dir!!.exists())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun insufficientSpaceReportsNoSpaceWithoutDownloading() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val attempts = AtomicInteger(0)
            val downloader = object : VoiceFileDownloader {
                override suspend fun download(
                    url: String,
                    destination: File,
                    onBytesRead: (deltaBytes: Long) -> Unit,
                ) {
                    attempts.incrementAndGet()
                }
            }
            val done = CompletableDeferred<Boolean>()
            val manager = VoiceModelManager(
                modelsRoot = File(tempRoot, "voice_models"),
                downloader = downloader,
                freeSpaceBytes = { 10L * 1024L * 1024L },
                verifyChecksums = false,
                scope = scope,
            )

            manager.startDownload(VoiceModelLanguage.ENGLISH) { ok -> done.complete(ok) }
            assertFalse(done.await())
            assertEquals(0, attempts.get())
            assertEquals(
                VoiceModelDownloadState.Error(VoiceDownloadError.NO_SPACE),
                manager.stateOf(VoiceModelLanguage.ENGLISH),
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun integrityMismatchAndNetworkFailureCleanStagingDirectory() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            // 1. Corrupt file size -> INTEGRITY error
            val corruptDone = CompletableDeferred<Boolean>()
            val corruptManager = VoiceModelManager(
                modelsRoot = File(tempRoot, "corrupt_models"),
                downloader = object : VoiceFileDownloader {
                    override suspend fun download(
                        url: String,
                        destination: File,
                        onBytesRead: (deltaBytes: Long) -> Unit,
                    ) {
                        destination.parentFile?.mkdirs()
                        destination.writeBytes(byteArrayOf(1, 2, 3))
                        onBytesRead(3L)
                    }
                },
                freeSpaceBytes = { 500L * 1024L * 1024L },
                verifyChecksums = true,
                scope = scope,
            )
            corruptManager.startDownload(VoiceModelLanguage.SPANISH) { ok -> corruptDone.complete(ok) }
            assertFalse(corruptDone.await())
            assertEquals(
                VoiceModelDownloadState.Error(VoiceDownloadError.INTEGRITY),
                corruptManager.stateOf(VoiceModelLanguage.SPANISH),
            )
            assertFalse(File(tempRoot, "corrupt_models/es.tmp").exists())
            assertFalse(corruptManager.isInstalled(VoiceModelLanguage.SPANISH))

            // 2. Network IOException -> NETWORK error
            val netDone = CompletableDeferred<Boolean>()
            val netManager = VoiceModelManager(
                modelsRoot = File(tempRoot, "net_models"),
                downloader = object : VoiceFileDownloader {
                    override suspend fun download(
                        url: String,
                        destination: File,
                        onBytesRead: (deltaBytes: Long) -> Unit,
                    ) {
                        throw IOException("offline")
                    }
                },
                freeSpaceBytes = { 500L * 1024L * 1024L },
                verifyChecksums = false,
                scope = scope,
            )
            netManager.startDownload(VoiceModelLanguage.ENGLISH) { ok -> netDone.complete(ok) }
            assertFalse(netDone.await())
            assertEquals(
                VoiceModelDownloadState.Error(VoiceDownloadError.NETWORK),
                netManager.stateOf(VoiceModelLanguage.ENGLISH),
            )
            assertFalse(File(tempRoot, "net_models/en.tmp").exists())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancelDownloadAbortsAndCleansTempFiles() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val started = CompletableDeferred<Unit>()
            val manager = VoiceModelManager(
                modelsRoot = File(tempRoot, "cancel_models"),
                downloader = object : VoiceFileDownloader {
                    override suspend fun download(
                        url: String,
                        destination: File,
                        onBytesRead: (deltaBytes: Long) -> Unit,
                    ) {
                        destination.parentFile?.mkdirs()
                        destination.writeBytes(byteArrayOf(7, 8, 9))
                        started.complete(Unit)
                        delay(10_000L)
                    }
                },
                freeSpaceBytes = { 500L * 1024L * 1024L },
                verifyChecksums = false,
                scope = scope,
            )

            manager.startDownload(VoiceModelLanguage.SPANISH)
            started.await()
            manager.cancelDownload(VoiceModelLanguage.SPANISH)
            delay(40L)

            assertEquals(
                VoiceModelDownloadState.NotDownloaded,
                manager.stateOf(VoiceModelLanguage.SPANISH),
            )
            assertFalse(File(tempRoot, "cancel_models/es.tmp").exists())
            assertFalse(manager.isInstalled(VoiceModelLanguage.SPANISH))
        } finally {
            scope.cancel()
        }
    }
}
