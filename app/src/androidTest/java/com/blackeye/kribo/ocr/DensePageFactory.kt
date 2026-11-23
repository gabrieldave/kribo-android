package com.blackeye.kribo.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File

/**
 * Deterministic dense documents for OCR device tests. Small body text (not giant titles) and
 * multiple paragraphs, so a page-number-only result cannot pass.
 */
object DensePageFactory {

    private val paragraphs = listOf(
        "Kribo conserva todas tus notas y documentos en el dispositivo, sin cuentas y sin enviar nada a la nube.",
        "El motor de reconocimiento lee párrafos completos incluso cuando la página tiene sombras suaves o una curvatura ligera.",
        "Cada escaneo guarda la imagen recortada y el texto extraído en la carpeta Recibos para poder buscar después.",
        "La búsqueda ignora acentos y mayúsculas, de modo que escribir cancion también encuentra la palabra canción.",
        "Los libros abiertos se dividen por el lomo y cada mitad se endereza con el modelo UVDoc antes del reconocimiento.",
        "El texto reconocido se puede expandir, copiar y editar libremente dentro del editor de notas de Kribo.",
        "Las fotografías de recibos conservan sus colores originales, mientras que las páginas de libros reciben limpieza de sombras.",
        "Este párrafo adicional sirve para comprobar que el detector no omite líneas al final de una página densa de lectura.",
    )

    fun singlePage(file: File, width: Int = 1400, height: Int = 1900): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = bodyPaint(textSize = 30f)
        val maxWidth = width - 120f
        var y = 90f
        val text = StringBuilder()
        for ((index, paragraph) in paragraphs.withIndex()) {
            val lines = wrap(paragraph, paint, maxWidth)
            for (line in lines) {
                if (y > height - 80f) break
                canvas.drawText(line, 60f, y, paint)
                text.append(line).append('\n')
                y += paint.textSize * 1.45f
            }
            y += paint.textSize * 0.8f
            if (index == 3) {
                // Subheading-like line, still small.
                val heading = "Lectura de documentos y notas"
                canvas.drawText(heading, 60f, y, bodyPaint(textSize = 32f, bold = true))
                text.append(heading).append('\n')
                y += 32f * 1.8f
            }
        }
        write(bitmap, file)
        return text.toString()
    }

    fun doublePage(file: File): String {
        val pageWidth = 900
        val height = 1200
        val spine = 34
        val width = pageWidth * 2 + spine
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        // Strong vertical spine so SpineSplitter can find a stable path.
        canvas.drawRect(
            pageWidth.toFloat(),
            0f,
            (pageWidth + spine).toFloat(),
            height.toFloat(),
            Paint().apply { color = Color.rgb(24, 22, 20) },
        )
        val text = StringBuilder()
        for (side in 0..1) {
            val left = (side * (pageWidth + spine)).toFloat()
            val paint = bodyPaint(textSize = 24f)
            val margin = 42f
            val maxWidth = pageWidth - margin * 2
            var y = 70f
            val prefix = if (side == 0) "izquierda" else "derecha"
            for ((index, paragraph) in paragraphs.withIndex()) {
                val source = "$prefix ${index + 1}. $paragraph"
                for (line in wrap(source, paint, maxWidth)) {
                    if (y > height - 60f) break
                    canvas.drawText(line, left + margin, y, paint)
                    text.append(line).append('\n')
                    y += paint.textSize * 1.42f
                }
                y += paint.textSize * 0.6f
            }
        }
        write(bitmap, file)
        return text.toString()
    }

    private fun bodyPaint(textSize: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(20, 20, 20)
        this.textSize = textSize
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines.ifEmpty { listOf("") }
    }

    private fun write(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        require(file.length() > 0L && file.length() < 12_000_000L)
    }
}
