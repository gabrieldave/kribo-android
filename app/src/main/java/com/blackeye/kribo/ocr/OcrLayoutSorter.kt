package com.blackeye.kribo.ocr

/**
 * Orden de lectura para las cajas devueltas por DBNet.
 *
 * Primero busca canales verticales de espacio blanco (XY-Cut simplificado)
 * para separar 2 o 3 columnas. Si la página es de una sola columna, aplica
 * exactamente el agrupamiento por renglones de siempre.
 */
object OcrLayoutSorter {

    data class Box(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val text: String,
    ) {
        val height: Int get() = (bottom - top).coerceAtLeast(1)
        val centerY: Int get() = (top + bottom) / 2
        val width: Int get() = (right - left).coerceAtLeast(1)
    }

    @Volatile
    var lastDecision: String = ""
        private set

    fun sort(boxes: List<Box>): List<Box> {
        if (boxes.size <= 1) return boxes

        val minX = boxes.minOf { it.left }.toFloat()
        val maxX = boxes.maxOf { it.right }.toFloat()
        val pageWidth = (maxX - minX).coerceAtLeast(1f)
        val wideThreshold = pageWidth * WIDE_FRACTION
        val narrow = boxes.filter { it.width < wideThreshold }
        // Hacen falta varias líneas por columna para no confundir una página
        // de una columna con líneas cortas y un número de página a la derecha.
        if (narrow.size < MIN_COLUMN_BOXES * 2) {
            lastDecision = "single (narrow=${narrow.size})"
            return sortRows(boxes)
        }

        val gutters = findGutters(narrow, minX, maxX, pageWidth)
        if (gutters.isEmpty()) {
            lastDecision = "single (no gutters, narrow=${narrow.size})"
            return sortRows(boxes)
        }

        val boundaries = buildList {
            add(minX)
            gutters.forEach { (start, end) -> add((start + end) / 2f) }
            add(maxX)
        }
        val columns = MutableList(boundaries.size - 1) { mutableListOf<Box>() }
        val fullWidth = mutableListOf<Box>()
        for (box in boxes) {
            if (box.width >= wideThreshold) {
                fullWidth += box
                continue
            }
            val center = (box.left + box.right) / 2f
            val index = boundaries.indexOfLast { center >= it }.coerceIn(0, columns.lastIndex)
            columns[index] += box
        }
        if (columns.any { it.size < MIN_COLUMN_BOXES }) {
            lastDecision = "single (short cols ${columns.joinToString(",") { it.size.toString() }})"
            return sortRows(boxes)
        }

        val columnTop = columns.flatten().minOf { it.top }
        val columnBottom = columns.flatten().maxOf { it.bottom }
        val headers = fullWidth
            .filter { it.bottom <= columnTop + EDGE_TOLERANCE }
            .sortedWith(compareBy({ it.top }, { it.left }))
        val footers = fullWidth
            .filter { it.top >= columnBottom - EDGE_TOLERANCE }
            .sortedWith(compareBy({ it.top }, { it.left }))
        val middle = fullWidth - headers.toSet() - footers.toSet()

        lastDecision = "cols=${columns.size} gutters=${gutters.size} full=${fullWidth.size} " +
            "sizes=${columns.joinToString(",") { it.size.toString() }}"
        return buildList {
            addAll(headers)
            columns.forEach { column -> addAll(sortRows(column)) }
            addAll(middle.sortedWith(compareBy({ it.top }, { it.left })))
            addAll(footers)
        }
    }

    /**
     * Canales verticales donde como máximo una caja angosta cruza. Tolerar una
     * caja suelta evita que una etiqueta de figura cierre la columna completa.
     */
    private fun findGutters(
        narrow: List<Box>,
        minX: Float,
        maxX: Float,
        pageWidth: Float,
    ): List<Pair<Float, Float>> {
        val events = ArrayList<Pair<Float, Int>>(narrow.size * 2)
        narrow.forEach { box ->
            events += box.left.toFloat() to 1
            events += box.right.toFloat() to -1
        }
        events.sortBy { it.first }
        var count = 0
        var previous = minX
        val raw = ArrayList<Pair<Float, Float>>()
        for ((x, delta) in events) {
            if (x > previous && count <= MAX_GUTTER_BOXES) raw += previous to x
            count += delta
            previous = x
        }
        if (previous < maxX && count <= MAX_GUTTER_BOXES) raw += previous to maxX

        val merged = ArrayList<Pair<Float, Float>>()
        raw.forEach { range ->
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.second + pageWidth * TOUCH_FRACTION) {
                merged[merged.lastIndex] = last.first to maxOf(last.second, range.second)
            } else {
                merged.add(range)
            }
        }
        val margin = pageWidth * MARGIN_FRACTION
        return merged
            .filter { (start, end) ->
                start > minX + margin &&
                    end < maxX - margin &&
                    end - start >= pageWidth * GUTTER_FRACTION
            }
            .sortedByDescending { (start, end) -> end - start }
            .take(2)
            .sortedBy { (start, _) -> start }
    }

    /** Lógica original: agrupa renglones por banda vertical y ordena izquierda→derecha. */
    private fun sortRows(boxes: List<Box>): List<Box> {
        if (boxes.size <= 1) return boxes
        val sorted = boxes.sortedWith(compareBy({ it.top }, { it.left }))
        val rows = ArrayList<MutableList<Box>>()
        var rowBottom = Int.MIN_VALUE
        var rowHeight = 1
        for (box in sorted) {
            val sameRow = rows.isNotEmpty() &&
                box.centerY <= rowBottom &&
                box.centerY >= rowBottom - rowHeight
            if (sameRow) {
                rows.last().add(box)
                rowBottom = maxOf(rowBottom, box.bottom)
                rowHeight = maxOf(rowHeight, box.height)
            } else {
                rows.add(mutableListOf(box))
                rowBottom = box.bottom
                rowHeight = box.height
            }
        }
        return rows.flatMap { row -> row.sortedBy { it.left } }
    }

    private const val WIDE_FRACTION = 0.55f
    private const val GUTTER_FRACTION = 0.012f
    private const val MARGIN_FRACTION = 0.01f
    private const val TOUCH_FRACTION = 0.005f
    private const val MAX_GUTTER_BOXES = 1
    private const val MIN_COLUMN_BOXES = 3
    private const val EDGE_TOLERANCE = 4
}
