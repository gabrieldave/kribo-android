package com.blackeye.kribo.ui.theme

import androidx.compose.ui.graphics.Color
import com.blackeye.kribo.data.blocks.AccentTone

/**
 * Inline palette (#42) for the 9 app themes. Text colors are chosen to stay legible on
 * both dark and light surfaces; highlights use soft backgrounds; [pdf] is safe for white
 * paper exports.
 */
data class TonePalette(
    val textDark: Color,
    val textLight: Color,
    val highlightDark: Color,
    val highlightLight: Color,
    val pdf: Color,
)

object InlineColors {

    private val palettes: Map<AccentTone, TonePalette> = mapOf(
        AccentTone.CYAN to TonePalette(
            textDark = Color(0xFF00D8FF), textLight = Color(0xFF007A99),
            highlightDark = Color(0xFF0E3A44), highlightLight = Color(0xFFE0F7FA),
            pdf = Color(0xFF007A99),
        ),
        AccentTone.EMERALD to TonePalette(
            textDark = Color(0xFF34D399), textLight = Color(0xFF047857),
            highlightDark = Color(0xFF12342A), highlightLight = Color(0xFFD1FAE5),
            pdf = Color(0xFF047857),
        ),
        AccentTone.AMBER to TonePalette(
            textDark = Color(0xFFFBBF24), textLight = Color(0xFFB45309),
            highlightDark = Color(0xFF3D2C0B), highlightLight = Color(0xFFFEF3C7),
            pdf = Color(0xFFB45309),
        ),
        AccentTone.CORAL to TonePalette(
            textDark = Color(0xFFFB7185), textLight = Color(0xFFBE123C),
            highlightDark = Color(0xFF3E1420), highlightLight = Color(0xFFFFE4E6),
            pdf = Color(0xFFBE123C),
        ),
        AccentTone.PURPLE to TonePalette(
            textDark = Color(0xFFC084FC), textLight = Color(0xFF7E22CE),
            highlightDark = Color(0xFF2A1745), highlightLight = Color(0xFFF3E8FF),
            pdf = Color(0xFF7E22CE),
        ),
    )

    fun palette(tone: AccentTone): TonePalette = palettes.getValue(tone)

    fun textColor(tone: AccentTone, darkTheme: Boolean): Color =
        if (darkTheme) palette(tone).textDark else palette(tone).textLight

    fun highlightColor(tone: AccentTone, darkTheme: Boolean): Color =
        if (darkTheme) palette(tone).highlightDark else palette(tone).highlightLight

    fun pdfColor(tone: AccentTone): Color = palette(tone).pdf

    /** The "Automatic / none" row: theme color with a slash in the dot. */
    val automatic = Color(0xFF9BA7B4)
}
