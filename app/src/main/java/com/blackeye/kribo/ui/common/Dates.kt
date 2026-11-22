package com.blackeye.kribo.ui.common

import java.text.DateFormat
import java.util.Date

/** Locale-aware note timestamp. The app locale is applied by AppCompatDelegate. */
fun formatNoteDate(epochMillis: Long): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return formatter.format(Date(epochMillis))
}

/** Short locale date for Bento tiles, where horizontal space is scarce. */
fun formatNoteDateShort(epochMillis: Long): String {
    val formatter = DateFormat.getDateInstance(DateFormat.SHORT)
    return formatter.format(Date(epochMillis))
}

