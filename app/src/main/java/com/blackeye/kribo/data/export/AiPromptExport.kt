package com.blackeye.kribo.data.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.blackeye.kribo.R
import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.LinkBlock
import com.blackeye.kribo.data.blocks.NoteBlock
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.QuoteBlock
import com.blackeye.kribo.data.blocks.TaskBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import com.blackeye.kribo.data.blocks.ToggleBlock
import java.io.File

/**
 * #43: one or several notes exported as a single clean `.txt` plus a ready-to-paste prompt
 * for ChatGPT, Gemini or Claude. The builders are pure so they stay unit-testable.
 */
data class NoteExportPayload(
    val title: String,
    val folder: String,
    val blocks: List<NoteBlock>,
    /** Attachment id → OCR text, so scans travel with the exported notes. */
    val ocrTexts: Map<Long, String> = emptyMap(),
)

/** The four one-tap prompts exposed by [com.blackeye.kribo.ui.share.AiShareSheet]. */
enum class AiPreset { SUMMARY, INFOGRAPHIC, HTML, ACTION_PLAN }

fun AiPreset.prompt(isSpanish: Boolean): String = when (this) {
    AiPreset.SUMMARY -> if (isSpanish) {
        "Actúa como analista experto. Te adjunto un archivo .txt con una o varias notas de " +
            "Kribo. Extrae y organiza: 1) ideas clave, 2) decisiones tomadas, 3) fechas y " +
            "plazos mencionados y 4) conclusiones y próximos pasos. Devuelve un resumen " +
            "ejecutivo claro en español, con viñetas, sin inventar información que no esté " +
            "en el archivo."
    } else {
        "Act as an expert analyst. Attached is a .txt file with one or more Kribo notes. " +
            "Extract and organize: 1) key ideas, 2) decisions made, 3) dates and deadlines " +
            "mentioned and 4) conclusions and next steps. Return a clear executive summary " +
            "in English, with bullet points, without inventing anything that is not in the file."
    }

    AiPreset.INFOGRAPHIC -> if (isSpanish) {
        "Actúa como diseñador de información. A partir del archivo adjunto, estructura el " +
            "contenido en bloques visuales, datos clave y una jerarquía clara (título, " +
            "secciones y cifras destacadas) lista para diseñar o generar una infografía. " +
            "Propón también una paleta de color y una disposición breve."
    } else {
        "Act as an information designer. From the attached file, structure the content into " +
            "visual blocks, key data and a clear hierarchy (title, sections and highlighted " +
            "figures) ready to design or generate an infographic. Also suggest a short color " +
            "palette and layout."
    }

    AiPreset.HTML -> if (isSpanish) {
        "Actúa como desarrollador front-end. Transforma el archivo adjunto en un único " +
            "archivo .html limpio, moderno y responsivo, con HTML y CSS embebidos, sin " +
            "dependencias externas, tipografía legible y buen contraste. Devuelve solo el " +
            "código completo listo para guardar."
    } else {
        "Act as a front-end developer. Turn the attached file into a single clean, modern, " +
            "responsive .html file with embedded HTML and CSS, no external dependencies, " +
            "readable typography and good contrast. Return only the complete code ready to save."
    }

    AiPreset.ACTION_PLAN -> if (isSpanish) {
        "Actúa como jefe de proyecto. Del archivo adjunto extrae las tareas concretas, " +
            "prioridades y siguientes pasos accionables. Devuelve una tabla con: tarea, " +
            "prioridad (alta/media/baja), responsable si aparece y fecha límite si aparece. " +
            "No inventes datos que no estén en el archivo."
    } else {
        "Act as a project lead. From the attached file, extract concrete tasks, priorities " +
            "and actionable next steps. Return a table with: task, priority (high/medium/low), " +
            "owner if mentioned and due date if mentioned. Do not invent data that is not in " +
            "the file."
    }
}

private fun labels(isSpanish: Boolean): Triple<String, String, String> = if (isSpanish) {
    Triple("Título", "Carpeta", "Notas combinadas")
} else {
    Triple("Title", "Folder", "Combined notes")
}

private fun renderBlock(
    block: NoteBlock,
    ocrTexts: Map<Long, String>,
    isSpanish: Boolean,
): String? = when (block) {
    is TitleBlock -> block.text.trim().takeIf { it.isNotEmpty() }
    is ParagraphBlock -> block.text.trim().takeIf { it.isNotEmpty() }
    is QuoteBlock -> block.text.trim().takeIf { it.isNotEmpty() }?.lineSequence()
        ?.joinToString(separator = "\n") { line -> if (line.isBlank()) ">" else "> $line" }
        ?.takeIf { it.isNotBlank() }
    is TaskBlock -> block.text.trim().takeIf { it.isNotEmpty() }?.let { text ->
        (if (block.checked) "[x] " else "[ ] ") + text
    }
    is ToggleBlock -> buildString {
        if (block.title.isNotBlank()) append(block.title.trim())
        if (block.content.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append(block.content.trim())
        }
    }.takeIf { it.isNotBlank() }
    is LinkBlock -> {
        val url = block.url.trim()
        val label = block.label.trim()
        when {
            url.isEmpty() -> null
            label.isEmpty() -> url
            else -> "$label ($url)"
        }
    }
    is AttachmentBlock -> {
        val ocr = block.attachmentId?.let { ocrTexts[it] }.orEmpty().trim()
        buildString {
            if (block.fileName.isNotBlank()) {
                append(if (isSpanish) "Imagen: " else "Image: ").append(block.fileName.trim())
            }
            if (ocr.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(if (isSpanish) "Texto OCR:" else "OCR text:").append("\n").append(ocr)
            }
        }.takeIf { it.isNotBlank() }
    }
}

private fun renderBlocks(
    blocks: List<NoteBlock>,
    ocrTexts: Map<Long, String>,
    isSpanish: Boolean,
): String = blocks
    .mapNotNull { renderBlock(it, ocrTexts, isSpanish) }
    .joinToString(separator = "\n\n")

/**
 * #43: a single note keeps its title, folder and every clean block. Several notes get a
 * "Notas combinadas" header and one `=== NOTA N: … ===` separator per note.
 */
fun buildCombinedNotesText(notes: List<NoteExportPayload>, isSpanish: Boolean): String {
    if (notes.isEmpty()) return ""
    val (titleLabel, folderLabel, combinedLabel) = labels(isSpanish)
    if (notes.size == 1) {
        val note = notes.first()
        return buildString {
            append(titleLabel).append(": ").append(note.title.ifBlank {
                if (isSpanish) "Sin título" else "Untitled"
            }).append('\n')
            append(folderLabel).append(": ").append(note.folder.ifBlank {
                if (isSpanish) "General" else "General"
            }).append("\n\n")
            append(renderBlocks(note.blocks, note.ocrTexts, isSpanish))
        }.trimEnd() + "\n"
    }

    val sections = notes.mapIndexed { index, note ->
        val heading = if (isSpanish) {
            "=== NOTA ${index + 1}: ${note.title.ifBlank { "Sin título" }} (${note.folder}) ==="
        } else {
            "=== NOTE ${index + 1}: ${note.title.ifBlank { "Untitled" }} (${note.folder}) ==="
        }
        buildString {
            append(heading).append("\n\n")
            append(renderBlocks(note.blocks, note.ocrTexts, isSpanish))
        }.trimEnd()
    }
    return buildString {
        append(combinedLabel).append(": ").append(notes.size).append("\n\n")
        append(sections.joinToString(separator = "\n\n"))
    }.trimEnd() + "\n"
}

/** Safe, readable file name: `Kribo_Plan-de-lanzamiento.txt` or `Kribo_3_notas.txt`. */
fun buildShareFileName(notes: List<NoteExportPayload>, isSpanish: Boolean): String {
    if (notes.size > 1) {
        val suffix = if (isSpanish) "notas" else "notes"
        return "Kribo_${notes.size}_$suffix.txt"
    }
    val raw = notes.firstOrNull()?.title.orEmpty()
    val safe = raw
        .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]+"), " ")
        .replace(Regex("\\s+"), "-")
        .replace(Regex("[^\\p{L}\\p{N}._-]"), "")
        .trim('-', '.', ' ')
        .take(60)
        .ifBlank { if (isSpanish) "nota" else "note" }
    return "Kribo_$safe.txt"
}

/** True when the app is currently rendered in Spanish (explicit or system locale). */
fun isSpanishContext(context: Context): Boolean =
    context.resources.configuration.locales.get(0).language.equals("es", ignoreCase = true)

/**
 * Writes the combined `.txt` into `cacheDir/shared_ai/`, cleans files older than one hour,
 * copies [promptText] to the clipboard when present and opens the system share chooser.
 * Returns false only when the file or the chooser could not be produced.
 */
fun shareNotesToAi(
    context: Context,
    notes: List<NoteExportPayload>,
    promptText: String?,
): Boolean {
    if (notes.isEmpty()) return false
    val isSpanish = isSpanishContext(context)
    return runCatching {
        val dir = File(context.cacheDir, "shared_ai").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
        val file = File(dir, buildShareFileName(notes, isSpanish))
        file.writeText(buildCombinedNotesText(notes, isSpanish), Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val hasPrompt = !promptText.isNullOrBlank()
        if (hasPrompt) {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("Kribo prompt", promptText))
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (hasPrompt) putExtra(Intent.EXTRA_TEXT, promptText)
            clipData = ClipData.newUri(context.contentResolver, "Kribo notes", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.ai_share_title)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
        if (hasPrompt) {
            Toast.makeText(
                context,
                context.getString(R.string.ai_share_prompt_copied),
                Toast.LENGTH_LONG,
            ).show()
        }
        true
    }.getOrDefault(false)
}
