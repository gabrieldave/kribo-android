package com.blackeye.kribo.ui.note

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.blackeye.kribo.ui.settings.AppLanguage
import java.util.Locale

enum class DictationState {
    IDLE,
    LISTENING,
}

enum class DictationError {
    UNAVAILABLE,
    PERMISSION_DENIED,
    NO_MATCH,
    GENERAL,
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

/**
 * Native Android [SpeechRecognizer] wrapper (#44).
 * Runs strictly on demand when the user taps the editor microphone button — zero external
 * servers, zero paid APIs, and zero work during app cold start.
 */
class VoiceDictator(
    context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onStateChange: (DictationState) -> Unit,
    private val onError: (DictationError) -> Unit,
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var manualCancel = false

    fun isAvailable(): Boolean = runCatching {
        SpeechRecognizer.isRecognitionAvailable(appContext)
    }.getOrDefault(false)

    fun start(languageTag: String) {
        if (!isAvailable()) {
            onStateChange(DictationState.IDLE)
            onError(DictationError.UNAVAILABLE)
            return
        }
        manualCancel = false
        val active = ensureRecognizer()
        if (active == null) {
            onStateChange(DictationState.IDLE)
            onError(DictationError.UNAVAILABLE)
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
        onPartial("")
        onStateChange(DictationState.LISTENING)
        runCatching {
            active.startListening(intent)
        }.onFailure {
            listening = false
            onPartial("")
            onStateChange(DictationState.IDLE)
            onError(DictationError.UNAVAILABLE)
        }
    }

    /** Stops recording and lets Android deliver the final recognized phrase if any. */
    fun stop() {
        if (!listening) return
        listening = false
        runCatching { recognizer?.stopListening() }
        onStateChange(DictationState.IDLE)
    }

    fun cancel() {
        manualCancel = true
        listening = false
        onPartial("")
        onStateChange(DictationState.IDLE)
        runCatching { recognizer?.cancel() }
    }

    fun destroy() {
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
                    if (listening) onStateChange(DictationState.LISTENING)
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    listening = false
                    onStateChange(DictationState.IDLE)
                }

                override fun onError(error: Int) {
                    listening = false
                    onPartial("")
                    onStateChange(DictationState.IDLE)
                    if (manualCancel && error == SpeechRecognizer.ERROR_CLIENT) return
                    val mapped = when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> DictationError.PERMISSION_DENIED
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        -> DictationError.NO_MATCH
                        else -> DictationError.GENERAL
                    }
                    onError(mapped)
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    onPartial("")
                    onStateChange(DictationState.IDLE)
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (spoken.isNotBlank()) {
                        onFinal(spoken)
                    } else if (!manualCancel) {
                        onError(DictationError.NO_MATCH)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        onPartial(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )
        recognizer = created
        return created
    }
}
