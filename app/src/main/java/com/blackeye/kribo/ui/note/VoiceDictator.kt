package com.blackeye.kribo.ui.note

import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriptEvent
import ai.moonshine.voice.TranscriptEventListener
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.blackeye.kribo.ui.preferences.VoiceEngineMode
import com.blackeye.kribo.ui.settings.AppLanguage
import com.blackeye.kribo.voice.VoiceModelLanguage
import com.blackeye.kribo.voice.VoiceModelManager
import com.blackeye.kribo.voice.resolveVoiceModelLanguage
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class DictationState {
    IDLE,
    LOADING,
    LISTENING,
}

enum class DictationError {
    UNAVAILABLE,
    PERMISSION_DENIED,
    NO_MATCH,
    MODEL_MISSING,
    AUDIO_ERROR,
    GENERAL,
}

/** Common contract shared by System SpeechRecognizer (0 MB) and Moonshine Offline Streaming. */
interface VoiceDictationEngine {
    fun isAvailable(): Boolean
    fun start(language: AppLanguage)
    fun stop()
    fun cancel()
    fun destroy()
}

interface VoiceDictationCallbacks {
    fun onPartial(text: String)
    fun onFinal(text: String)
    fun onStateChange(state: DictationState)
    fun onError(error: DictationError)
}

/** Pure language resolution for Android SpeechRecognizer based on Kribo's active language. */
fun resolveDictationLanguageTag(
    appLanguage: AppLanguage,
    systemLocale: Locale = Locale.getDefault(),
): String = when (appLanguage) {
    AppLanguage.SPANISH -> {
        if (systemLocale.language.equals("es", ignoreCase = true) && systemLocale.country.isNotBlank()) {
            systemLocale.toLanguageTag()
        } else {
            "es-MX"
        }
    }

    AppLanguage.ENGLISH -> {
        if (systemLocale.language.equals("en", ignoreCase = true) && systemLocale.country.isNotBlank()) {
            systemLocale.toLanguageTag()
        } else {
            "en-US"
        }
    }

    AppLanguage.SYSTEM -> systemLocale.toLanguageTag().ifBlank { "en-US" }
}

/** Pure PCM-16 signed short to normalized float [-1, 1] converter for 16 kHz Moonshine input. */
internal fun pcm16ToNormalizedFloat(samples: ShortArray, count: Int): FloatArray {
    val safeCount = count.coerceIn(0, samples.size)
    val out = FloatArray(safeCount)
    for (i in 0 until safeCount) {
        out[i] = samples[i] / 32768f
    }
    return out
}

/**
 * Engine 1 (default, 0 MB): Native Android [SpeechRecognizer] wrapper.
 * Runs strictly on demand when the user taps the editor microphone button.
 */
class SystemSpeechDictationEngine(
    context: Context,
    private val callbacks: VoiceDictationCallbacks,
) : VoiceDictationEngine {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var manualCancel = false

    override fun isAvailable(): Boolean = runCatching {
        SpeechRecognizer.isRecognitionAvailable(appContext)
    }.getOrDefault(false)

    override fun start(language: AppLanguage) {
        startWithTag(resolveDictationLanguageTag(language))
    }

    fun startWithTag(languageTag: String) {
        if (!isAvailable()) {
            callbacks.onStateChange(DictationState.IDLE)
            callbacks.onError(DictationError.UNAVAILABLE)
            return
        }
        manualCancel = false
        val active = ensureRecognizer()
        if (active == null) {
            callbacks.onStateChange(DictationState.IDLE)
            callbacks.onError(DictationError.UNAVAILABLE)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }
        listening = true
        callbacks.onPartial("")
        callbacks.onStateChange(DictationState.LISTENING)
        runCatching {
            active.startListening(intent)
        }.onFailure {
            listening = false
            callbacks.onPartial("")
            callbacks.onStateChange(DictationState.IDLE)
            callbacks.onError(DictationError.UNAVAILABLE)
        }
    }

    override fun stop() {
        if (!listening) return
        listening = false
        runCatching { recognizer?.stopListening() }
        callbacks.onStateChange(DictationState.IDLE)
    }

    override fun cancel() {
        manualCancel = true
        listening = false
        callbacks.onPartial("")
        callbacks.onStateChange(DictationState.IDLE)
        runCatching { recognizer?.cancel() }
    }

    override fun destroy() {
        manualCancel = true
        listening = false
        val current = recognizer
        recognizer = null
        runCatching {
            current?.cancel()
            current?.destroy()
        }
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        val created = runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
            ?: return null
        created.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (listening) callbacks.onStateChange(DictationState.LISTENING)
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    listening = false
                    callbacks.onStateChange(DictationState.IDLE)
                }

                override fun onError(error: Int) {
                    listening = false
                    callbacks.onPartial("")
                    callbacks.onStateChange(DictationState.IDLE)
                    if (manualCancel && error == SpeechRecognizer.ERROR_CLIENT) return
                    val mapped = when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> DictationError.PERMISSION_DENIED
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        -> DictationError.NO_MATCH
                        else -> DictationError.GENERAL
                    }
                    callbacks.onError(mapped)
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    callbacks.onPartial("")
                    callbacks.onStateChange(DictationState.IDLE)
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (spoken.isNotBlank()) {
                        callbacks.onFinal(spoken)
                    } else if (!manualCancel) {
                        callbacks.onError(DictationError.NO_MATCH)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        callbacks.onPartial(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )
        recognizer = created
        return created
    }
}

/**
 * Engine 2 (optional on-demand, MIT Streaming): Moonshine Voice continuous offline streaming
 * over 16 kHz mono PCM [AudioRecord] on [Dispatchers.IO].
 *
 * Works 100% in airplane mode without system beeps or abrupt cutoffs on short pauses.
 */
class MoonshineOfflineDictationEngine(
    private val modelManager: VoiceModelManager,
    private val callbacks: VoiceDictationCallbacks,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val mainPoster: ((() -> Unit) -> Unit) = { block ->
        val mainLooper = Looper.getMainLooper()
        if (mainLooper != null && Looper.myLooper() != mainLooper) {
            Handler(mainLooper).post(block)
        } else {
            block()
        }
    },
) : VoiceDictationEngine {

    private val engineLock = Any()
    private val capturing = AtomicBoolean(false)
    private val suppressResults = AtomicBoolean(false)
    private val completedLineIds = Collections.synchronizedSet(mutableSetOf<Long>())

    @Volatile
    private var lastPartialText = ""

    @Volatile
    private var loadedLanguage: VoiceModelLanguage? = null

    @Volatile
    private var transcriber: Transcriber? = null

    @Volatile
    private var captureJob: Job? = null

    override fun isAvailable(): Boolean = true

    override fun start(language: AppLanguage) {
        val modelLang = resolveVoiceModelLanguage(language)
        val dir = modelManager.modelDir(modelLang)
        if (dir == null) {
            postMain {
                callbacks.onStateChange(DictationState.IDLE)
                callbacks.onError(DictationError.MODEL_MISSING)
            }
            return
        }
        if (!capturing.compareAndSet(false, true)) return

        suppressResults.set(false)
        completedLineIds.clear()
        lastPartialText = ""
        postMain {
            callbacks.onPartial("")
            callbacks.onStateChange(DictationState.LOADING)
        }

        captureJob = scope.launch {
            runStreamingSession(modelLang, dir)
        }
    }

    override fun stop() {
        if (!capturing.compareAndSet(true, false)) return
        // The capture loop exits cleanly on the next read chunk, releases AudioRecord,
        // flushes Moonshine's final streaming buffer and delivers any remaining text.
    }

    override fun cancel() {
        suppressResults.set(true)
        capturing.set(false)
        captureJob?.cancel()
        captureJob = null
        postMain {
            callbacks.onPartial("")
            callbacks.onStateChange(DictationState.IDLE)
        }
    }

    override fun destroy() {
        suppressResults.set(true)
        capturing.set(false)
        val job = captureJob
        captureJob = null
        job?.cancel()
        val toClose = synchronized(engineLock) {
            val existing = transcriber
            transcriber = null
            loadedLanguage = null
            existing
        }
        if (toClose != null) {
            scope.launch {
                runCatching { toClose.removeAllListeners() }
                runCatching { toClose.close() }
                scope.cancel()
            }
        } else {
            scope.cancel()
        }
    }

    private suspend fun runStreamingSession(modelLang: VoiceModelLanguage, dir: File) {
        var recorder: AudioRecord? = null
        var activeTranscriber: Transcriber? = null
        var streamStarted = false
        try {
            val loaded = ensureTranscriber(modelLang, dir)
            activeTranscriber = loaded
            if (!capturing.get() || !scope.isActive) return

            recorder = createAudioRecorder()
            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                capturing.set(false)
                postMain {
                    callbacks.onPartial("")
                    callbacks.onStateChange(DictationState.IDLE)
                    callbacks.onError(DictationError.AUDIO_ERROR)
                }
                return
            }

            loaded.start()
            streamStarted = true
            recorder.startRecording()
            postMain {
                if (capturing.get()) {
                    callbacks.onStateChange(DictationState.LISTENING)
                }
            }

            val pcmBuffer = ShortArray(CHUNK_SAMPLES)
            while (capturing.get() && scope.isActive) {
                val read = recorder.read(pcmBuffer, 0, pcmBuffer.size)
                if (read > 0) {
                    val floats = pcm16ToNormalizedFloat(pcmBuffer, read)
                    loaded.addAudio(floats, SAMPLE_RATE_HZ)
                } else if (read < 0) {
                    break
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            postMain { callbacks.onError(DictationError.PERMISSION_DENIED) }
        } catch (_: Throwable) {
            if (!suppressResults.get()) {
                postMain { callbacks.onError(DictationError.GENERAL) }
            }
        } finally {
            capturing.set(false)
            runCatching {
                if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            }
            runCatching { recorder?.release() }

            if (streamStarted && activeTranscriber != null) {
                runCatching { activeTranscriber.stop() }
            }

            val danglingPartial = lastPartialText.trim()
            lastPartialText = ""
            if (!suppressResults.get() && danglingPartial.isNotEmpty()) {
                postMain { callbacks.onFinal(danglingPartial) }
            }
            postMain {
                callbacks.onPartial("")
                callbacks.onStateChange(DictationState.IDLE)
            }
        }
    }

    private fun ensureTranscriber(language: VoiceModelLanguage, dir: File): Transcriber =
        synchronized(engineLock) {
            val existing = transcriber
            if (existing != null && loadedLanguage == language && existing.isLoaded) {
                return@synchronized existing
            }
            if (existing != null) {
                runCatching { existing.removeAllListeners() }
                runCatching { existing.close() }
                transcriber = null
                loadedLanguage = null
            }
            val created = Transcriber()
            created.setUpdateInterval(STREAM_UPDATE_INTERVAL_SECONDS)
            created.loadFromFiles(dir.absolutePath, language.modelArch)
            val listener = object : TranscriptEventListener() {
                override fun onLineStarted(event: TranscriptEvent.LineStarted) {
                    handlePartial(event.line?.text)
                }

                override fun onLineUpdated(event: TranscriptEvent.LineUpdated) {
                    handlePartial(event.line?.text)
                }

                override fun onLineTextChanged(event: TranscriptEvent.LineTextChanged) {
                    handlePartial(event.line?.text)
                }

                override fun onLineCompleted(event: TranscriptEvent.LineCompleted) {
                    if (suppressResults.get()) return
                    val line = event.line ?: return
                    val clean = line.text?.trim().orEmpty()
                    lastPartialText = ""
                    postMain { callbacks.onPartial("") }
                    if (clean.isNotEmpty() && completedLineIds.add(line.id)) {
                        postMain { callbacks.onFinal(clean) }
                    }
                }

                override fun onError(event: TranscriptEvent.Error) {
                    if (!suppressResults.get()) {
                        postMain { callbacks.onError(DictationError.GENERAL) }
                    }
                }
            }
            created.addListener { event -> event.accept(listener) }
            transcriber = created
            loadedLanguage = language
            created
        }

    private fun handlePartial(raw: String?) {
        if (suppressResults.get()) return
        val clean = raw?.trim().orEmpty()
        if (clean.isNotEmpty()) {
            lastPartialText = clean
            postMain { callbacks.onPartial(clean) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecorder(): AudioRecord? {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(CHUNK_SAMPLES * 2)
        val bufferBytes = maxOf(minBuf * 2, CHUNK_SAMPLES * 4)
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
        for (source in sources) {
            val candidate = runCatching {
                AudioRecord(
                    source,
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes,
                )
            }.getOrNull()
            if (candidate != null && candidate.state == AudioRecord.STATE_INITIALIZED) {
                return candidate
            }
            runCatching { candidate?.release() }
        }
        return null
    }

    private fun postMain(block: () -> Unit) {
        mainPoster(block)
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        private const val CHUNK_SAMPLES = 1_600 // 100 ms at 16 kHz mono PCM
        private const val STREAM_UPDATE_INTERVAL_SECONDS = 0.25
    }
}

/**
 * Dual-engine coordinator owned by the note editor (#44).
 * Routes to [SystemSpeechDictationEngine] (0 MB default) or [MoonshineOfflineDictationEngine]
 * (optional on-demand MIT streaming) behind [VoiceDictationEngine].
 */
class VoiceDictator(
    context: Context,
    modelManager: VoiceModelManager? = null,
    private val modeProvider: () -> VoiceEngineMode = { VoiceEngineMode.SYSTEM },
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onStateChange: (DictationState) -> Unit,
    onError: (DictationError) -> Unit,
) : VoiceDictationEngine {

    private val callbacks = object : VoiceDictationCallbacks {
        override fun onPartial(text: String) = onPartial(text)
        override fun onFinal(text: String) = onFinal(text)
        override fun onStateChange(state: DictationState) = onStateChange(state)
        override fun onError(error: DictationError) = onError(error)
    }

    private val systemEngine = SystemSpeechDictationEngine(context, callbacks)
    private val offlineEngine: MoonshineOfflineDictationEngine? =
        modelManager?.let { MoonshineOfflineDictationEngine(it, callbacks) }

    @Volatile
    private var activeEngine: VoiceDictationEngine = systemEngine

    override fun isAvailable(): Boolean = isAvailableFor(modeProvider())

    fun isAvailableFor(mode: VoiceEngineMode): Boolean = when (mode) {
        VoiceEngineMode.SYSTEM -> systemEngine.isAvailable()
        VoiceEngineMode.OFFLINE_MOONSHINE -> offlineEngine?.isAvailable() == true
    }

    override fun start(language: AppLanguage) {
        startWithMode(modeProvider(), language)
    }

    fun startWithMode(mode: VoiceEngineMode, language: AppLanguage) {
        val target = when (mode) {
            VoiceEngineMode.SYSTEM -> systemEngine
            VoiceEngineMode.OFFLINE_MOONSHINE -> offlineEngine ?: systemEngine
        }
        if (activeEngine !== target) {
            activeEngine.cancel()
            activeEngine = target
        }
        target.start(language)
    }

    override fun stop() {
        activeEngine.stop()
    }

    override fun cancel() {
        systemEngine.cancel()
        offlineEngine?.cancel()
    }

    override fun destroy() {
        systemEngine.destroy()
        offlineEngine?.destroy()
    }
}
