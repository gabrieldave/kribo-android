package com.blackeye.kribo.ocr

/** Limpieza de texto OCR antiguo o de cajas CTC que quedaron letra por letra. */
object OcrTextSanitizer {

    private val letterRuns = Regex("\\b(?:\\p{L}\\s+){3,}\\p{L}\\b")

    fun needsReextract(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return letterRuns.containsMatchIn(text)
    }

    /** "C u a n d o" -> "Cuando" sin tocar palabras normales. */
    /** Une guiones de corte y deja párrafos/palabras listos para TTS/OCR. */
    fun cleanForOcr(text: String): String {
        var clean = joinLetterRuns(text)
        clean = clean.replace(Regex("(\\p{Ll})-\\s*\\n\\s*(\\p{Ll})"), "$1$2")
        clean = clean.replace(Regex("\\n{2,}"), "\n")
        clean = clean.replace(Regex("(?<![.!?…:;])\\n"), " ")
        clean = clean.replace(Regex("[ \t]+"), " ")
        return clean.trim()
    }

    fun joinLetterRuns(text: String): String {
        if (text.isBlank()) return text
        return letterRuns.replace(text) { match ->
            match.value.replace(Regex("\\s+"), "")
        }
    }
}
