package com.blackeye.kribo.voice

import ai.moonshine.voice.JNI
import com.blackeye.kribo.ui.settings.AppLanguage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Official Moonshine MIT Streaming Tiny model specs (`MOONSHINE_MODEL_ARCH_TINY_STREAMING`).
 * Every package listed here is licensed under the MIT License (never Community License).
 */
data class VoiceModelFileSpec(
    val name: String,
    val expectedBytes: Long,
    val crc32cBase64: String,
)

enum class VoiceModelLanguage(
    val code: String,
    val modelArch: Int,
    val baseUrl: String,
    val files: List<VoiceModelFileSpec>,
) {
    SPANISH(
        code = "es",
        modelArch = JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING,
        baseUrl = "https://download.moonshine.ai/model/tiny-streaming-es/quantized_26_08_24",
        files = listOf(
            VoiceModelFileSpec("adapter.ort", 1_318_472L, "+JOBGg=="),
            VoiceModelFileSpec("cross_kv.ort", 1_288_120L, "lvRCDw=="),
            VoiceModelFileSpec("decoder_kv.ort", 19_717_336L, "KEm/xA=="),
            VoiceModelFileSpec("encoder.ort", 7_772_792L, "AAXCVA=="),
            VoiceModelFileSpec("frontend.model.ort", 23_176L, "v7nfyQ=="),
            VoiceModelFileSpec("frontend.weights.ort", 2_093_280L, "LYbiaA=="),
            VoiceModelFileSpec("streaming_config.json", 509L, "wH/VeA=="),
            VoiceModelFileSpec("tokenizer.bin", 102_888L, "/7v8NQ=="),
        ),
    ),
    ENGLISH(
        code = "en",
        modelArch = JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING,
        baseUrl = "https://download.moonshine.ai/model/tiny-streaming-en/quantized_26_11_02",
        files = listOf(
            VoiceModelFileSpec("adapter.ort", 1_319_616L, "8tL9VQ=="),
            VoiceModelFileSpec("cross_kv.ort", 1_287_504L, "nwGoTw=="),
            VoiceModelFileSpec("decoder_kv.ort", 32_748_720L, "rpn9LA=="),
            VoiceModelFileSpec("encoder.ort", 7_675_392L, "9j4G6g=="),
            VoiceModelFileSpec("frontend.model.ort", 23_392L, "AxTn9Q=="),
            VoiceModelFileSpec("frontend.weights.ort", 2_093_416L, "lNoOfg=="),
            VoiceModelFileSpec("streaming_config.json", 509L, "HGL0Ug=="),
            VoiceModelFileSpec("tokenizer.bin", 249_974L, "B7s10Q=="),
        ),
    ),
    ;

    val totalBytes: Long
        get() = files.sumOf { it.expectedBytes }

    val approxMb: Int
        get() = ((totalBytes + 524_288L) / 1_048_576L).toInt().coerceAtLeast(1)

    fun fileUrl(spec: VoiceModelFileSpec): String = "$baseUrl/${spec.name}"
}

fun resolveVoiceModelLanguage(
    appLanguage: AppLanguage,
    systemLocale: Locale = Locale.getDefault(),
): VoiceModelLanguage = when (appLanguage) {
    AppLanguage.SPANISH -> VoiceModelLanguage.SPANISH
    AppLanguage.ENGLISH -> VoiceModelLanguage.ENGLISH
    AppLanguage.SYSTEM -> if (systemLocale.language.equals("es", ignoreCase = true)) {
        VoiceModelLanguage.SPANISH
    } else {
        VoiceModelLanguage.ENGLISH
    }
}

enum class VoiceDownloadError {
    NETWORK,
    NO_SPACE,
    INTEGRITY,
    UNKNOWN,
}

sealed interface VoiceModelDownloadState {
    data object NotDownloaded : VoiceModelDownloadState
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : VoiceModelDownloadState

    data class Ready(val installedBytes: Long) : VoiceModelDownloadState
    data class Error(val kind: VoiceDownloadError) : VoiceModelDownloadState
}

interface VoiceFileDownloader {
    @Throws(IOException::class)
    suspend fun download(
        url: String,
        destination: File,
        onBytesRead: (deltaBytes: Long) -> Unit,
    )
}

/** Standard HTTPS downloader using a clean User-Agent compatible with Moonshine's CDN. */
class HttpVoiceFileDownloader : VoiceFileDownloader {
    override suspend fun download(
        url: String,
        destination: File,
        onBytesRead: (deltaBytes: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code downloading $url")
            }
            destination.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            onBytesRead(read.toLong())
                        }
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val BUFFER_SIZE = 32 * 1024
        const val USER_AGENT = "Kribo-Android/1.0.20"
    }
}

/** Castagnoli CRC32C matching Moonshine's official CDN metadata checksums (big-endian Base64). */
internal object Crc32c {
    private val table = IntArray(256).apply {
        for (i in 0 until 256) {
            var crc = i
            repeat(8) {
                crc = if ((crc and 1) != 0) (crc ushr 1) xor 0x82F63B78.toInt() else crc ushr 1
            }
            this[i] = crc
        }
    }

    fun checksumBase64(file: File): String {
        var crc = 0xFFFFFFFF.toInt()
        val buffer = ByteArray(32 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                for (i in 0 until read) {
                    val idx = (crc xor (buffer[i].toInt() and 0xFF)) and 0xFF
                    crc = (crc ushr 8) xor table[idx]
                }
            }
        }
        val finalCrc = crc xor 0xFFFFFFFF.toInt()
        val bytes = byteArrayOf(
            ((finalCrc ushr 24) and 0xFF).toByte(),
            ((finalCrc ushr 16) and 0xFF).toByte(),
            ((finalCrc ushr 8) and 0xFF).toByte(),
            (finalCrc and 0xFF).toByte(),
        )
        return Base64.getEncoder().encodeToString(bytes)
    }
}

/**
 * Manages on-demand download, integrity validation and deletion of Moonshine MIT Streaming
 * voice packs inside `filesDir/voice_models/<lang>/`.
 *
 * No model weights are ever bundled inside APK/AAB assets.
 */
class VoiceModelManager(
    private val modelsRoot: File,
    private val downloader: VoiceFileDownloader = HttpVoiceFileDownloader(),
    private val freeSpaceBytes: (File) -> Long = { dir ->
        val existing = if (dir.exists()) dir else dir.parentFile ?: dir
        existing.usableSpace
    },
    private val verifyChecksums: Boolean = true,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val jobLock = Any()
    private val activeJobs = mutableMapOf<VoiceModelLanguage, Job>()

    private val _states = MutableStateFlow(initialStates())
    val states: StateFlow<Map<VoiceModelLanguage, VoiceModelDownloadState>> = _states.asStateFlow()

    fun stateOf(language: VoiceModelLanguage): VoiceModelDownloadState =
        _states.value[language] ?: VoiceModelDownloadState.NotDownloaded

    fun isInstalled(language: VoiceModelLanguage): Boolean =
        validateInstalledDir(language) != null

    /** Returns the validated local directory `filesDir/voice_models/<lang>/` or null if absent. */
    fun modelDir(language: VoiceModelLanguage): File? = validateInstalledDir(language)

    fun refreshStates() {
        _states.update { current ->
            VoiceModelLanguage.entries.associateWith { lang ->
                val active = synchronized(jobLock) { activeJobs[lang]?.isActive == true }
                if (active) {
                    current[lang] ?: VoiceModelDownloadState.NotDownloaded
                } else {
                    val installed = validateInstalledDir(lang)
                    if (installed != null) {
                        VoiceModelDownloadState.Ready(installedSize(lang, installed))
                    } else {
                        VoiceModelDownloadState.NotDownloaded
                    }
                }
            }
        }
    }

    fun startDownload(
        language: VoiceModelLanguage,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        synchronized(jobLock) {
            if (activeJobs[language]?.isActive == true) return
            val existing = validateInstalledDir(language)
            if (existing != null) {
                setState(language, VoiceModelDownloadState.Ready(installedSize(language, existing)))
                onComplete?.invoke(true)
                return
            }
            val totalBytes = language.totalBytes
            setState(
                language,
                VoiceModelDownloadState.Downloading(
                    progressPercent = 0,
                    downloadedBytes = 0L,
                    totalBytes = totalBytes,
                ),
            )
            activeJobs[language] = scope.launch {
                val ok = runDownload(language)
                synchronized(jobLock) { activeJobs.remove(language) }
                onComplete?.invoke(ok)
            }
        }
    }

    fun cancelDownload(language: VoiceModelLanguage) {
        val job = synchronized(jobLock) { activeJobs.remove(language) }
        job?.cancel()
        tempDirFor(language).deleteRecursively()
        val installed = validateInstalledDir(language)
        setState(
            language,
            if (installed != null) {
                VoiceModelDownloadState.Ready(installedSize(language, installed))
            } else {
                VoiceModelDownloadState.NotDownloaded
            },
        )
    }

    fun deleteModel(language: VoiceModelLanguage) {
        val job = synchronized(jobLock) { activeJobs.remove(language) }
        job?.cancel()
        tempDirFor(language).deleteRecursively()
        dirFor(language).deleteRecursively()
        setState(language, VoiceModelDownloadState.NotDownloaded)
    }

    private suspend fun runDownload(language: VoiceModelLanguage): Boolean {
        val stagingDir = tempDirFor(language)
        val finalDir = dirFor(language)
        val totalBytes = language.totalBytes
        try {
            if (!modelsRoot.exists() && !modelsRoot.mkdirs() && !modelsRoot.isDirectory) {
                setState(language, VoiceModelDownloadState.Error(VoiceDownloadError.UNKNOWN))
                return false
            }
            val available = freeSpaceBytes(modelsRoot)
            if (available < totalBytes + SPACE_MARGIN_BYTES) {
                setState(language, VoiceModelDownloadState.Error(VoiceDownloadError.NO_SPACE))
                return false
            }

            stagingDir.deleteRecursively()
            if (!stagingDir.mkdirs()) {
                setState(language, VoiceModelDownloadState.Error(VoiceDownloadError.UNKNOWN))
                return false
            }

            var downloadedSoFar = 0L
            var lastReportedPct = -1

            for (spec in language.files) {
                currentCoroutineContext().ensureActive()
                val partFile = File(stagingDir, "${spec.name}$PART_SUFFIX")
                val targetFile = File(stagingDir, spec.name)
                downloader.download(
                    url = language.fileUrl(spec),
                    destination = partFile,
                ) { delta ->
                    downloadedSoFar += delta
                    val pct = if (totalBytes > 0L) {
                        ((downloadedSoFar * 100L) / totalBytes).toInt().coerceIn(0, 99)
                    } else {
                        0
                    }
                    if (pct != lastReportedPct) {
                        lastReportedPct = pct
                        setState(
                            language,
                            VoiceModelDownloadState.Downloading(
                                progressPercent = pct,
                                downloadedBytes = downloadedSoFar.coerceAtMost(totalBytes),
                                totalBytes = totalBytes,
                            ),
                        )
                    }
                }

                currentCoroutineContext().ensureActive()
                if (!partFile.isFile || partFile.length() != spec.expectedBytes) {
                    stagingDir.deleteRecursively()
                    setState(language, VoiceModelDownloadState.Error(VoiceDownloadError.INTEGRITY))
                    return false
                }
                if (verifyChecksums && spec.crc32cBase64.isNotBlank()) {
                    val actualCrc = Crc32c.checksumBase64(partFile)
                    if (actualCrc != spec.crc32cBase64) {
                        stagingDir.deleteRecursively()
                        setState(language, VoiceModelDownloadState.Error(VoiceDownloadError.INTEGRITY))
                        return false
                    }
                }
                if (!partFile.renameTo(targetFile)) {
                    partFile.copyTo(targetFile, overwrite = true)
                    partFile.delete()
                }
            }

            File(stagingDir, READY_MARKER).writeText(language.baseUrl)
            if (finalDir.exists()) finalDir.deleteRecursively()
            if (!stagingDir.renameTo(finalDir)) {
                stagingDir.copyRecursively(finalDir, overwrite = true)
                stagingDir.deleteRecursively()
            }

            val validated = validateInstalledDir(language)
            if (validated == null) {
                finalDir.deleteRecursively()
                setState(language, VoiceModelDownloadState.Error(VoiceDownloadError.INTEGRITY))
                return false
            }
            setState(language, VoiceModelDownloadState.Ready(installedSize(language, validated)))
            return true
        } catch (cancelled: CancellationException) {
            stagingDir.deleteRecursively()
            val installed = validateInstalledDir(language)
            setState(
                language,
                if (installed != null) {
                    VoiceModelDownloadState.Ready(installedSize(language, installed))
                } else {
                    VoiceModelDownloadState.NotDownloaded
                },
            )
            throw cancelled
        } catch (io: IOException) {
            stagingDir.deleteRecursively()
            val message = io.message.orEmpty().lowercase(Locale.ROOT)
            val kind = if (message.contains("enospc") || message.contains("no space")) {
                VoiceDownloadError.NO_SPACE
            } else {
                VoiceDownloadError.NETWORK
            }
            setState(language, VoiceModelDownloadState.Error(kind))
            return false
        } catch (_: Throwable) {
            stagingDir.deleteRecursively()
            setState(language, VoiceModelDownloadState.Error(VoiceDownloadError.UNKNOWN))
            return false
        }
    }

    private fun initialStates(): Map<VoiceModelLanguage, VoiceModelDownloadState> =
        VoiceModelLanguage.entries.associateWith { lang ->
            val installed = validateInstalledDir(lang)
            if (installed != null) {
                VoiceModelDownloadState.Ready(installedSize(lang, installed))
            } else {
                VoiceModelDownloadState.NotDownloaded
            }
        }

    private fun dirFor(language: VoiceModelLanguage): File = File(modelsRoot, language.code)

    private fun tempDirFor(language: VoiceModelLanguage): File =
        File(modelsRoot, "${language.code}.tmp")

    private fun validateInstalledDir(language: VoiceModelLanguage): File? {
        val dir = dirFor(language)
        if (!dir.isDirectory) return null
        if (!File(dir, READY_MARKER).isFile) return null
        val allPresent = language.files.all { spec ->
            val file = File(dir, spec.name)
            file.isFile && file.length() == spec.expectedBytes
        }
        return if (allPresent) dir else null
    }

    private fun installedSize(language: VoiceModelLanguage, dir: File): Long =
        language.files.sumOf { File(dir, it.name).length() }.coerceAtLeast(language.totalBytes)

    private fun setState(language: VoiceModelLanguage, state: VoiceModelDownloadState) {
        _states.update { current -> current + (language to state) }
    }

    companion object {
        const val MODELS_DIR_NAME = "voice_models"
        const val READY_MARKER = ".ready"
        private const val PART_SUFFIX = ".part"
        private const val SPACE_MARGIN_BYTES = 12L * 1024L * 1024L
    }
}
