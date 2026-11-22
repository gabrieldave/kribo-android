package com.blackeye.kribo.ocr

/**
 * Decodificador CTC codicioso para PP-OCRv5.
 *
 * El modelo entrega `[1, T, 504]`: el índice 0 es el blanco CTC, los índices
 * 1..502 son el diccionario latino y el 503 es el espacio final que PaddleOCR
 * agrega con `use_space_char`. Se colapsan repeticiones consecutivas y se
 * descartan los blancos, nunca se recorta un carácter real.
 */
object CtcDecoder {

    const val BLANK_INDEX = 0

    fun decode(indices: IntArray, characters: List<String>): String {
        if (indices.isEmpty()) return ""
        val out = StringBuilder()
        var previous = -1
        for (index in indices) {
            if (index != BLANK_INDEX && index != previous) {
                out.append(characterAt(index, characters))
            }
            previous = index
        }
        return out.toString()
    }

    /** Convierte una matriz `[T, C]` de logits/probabilidades a índices. */
    fun argmax(logits: Array<FloatArray>): IntArray {
        if (logits.isEmpty()) return IntArray(0)
        return IntArray(logits.size) { t ->
            val row = logits[t]
            var best = 0
            var bestValue = row[0]
            for (c in 1 until row.size) {
                if (row[c] > bestValue) {
                    bestValue = row[c]
                    best = c
                }
            }
            best
        }
    }

    fun decode(logits: Array<FloatArray>, characters: List<String>): String =
        decode(argmax(logits), characters)

    fun characterAt(index: Int, characters: List<String>): String = when {
        index <= BLANK_INDEX -> ""
        index <= characters.size -> characters[index - 1]
        // PaddleOCR inserta el espacio después del diccionario (use_space_char).
        index == characters.size + 1 -> " "
        else -> ""
    }
}
