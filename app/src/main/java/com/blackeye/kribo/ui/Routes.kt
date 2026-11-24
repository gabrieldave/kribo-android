package com.blackeye.kribo.ui

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val NOTE_ID_ARG = "noteId"
    const val QUERY_ARG = "query"
    const val NOTE_EDIT = "note/{$NOTE_ID_ARG}?$QUERY_ARG={$QUERY_ARG}"
    const val SETTINGS = "settings"
    const val LICENSES = "licenses"
    const val WIFI = "wifi-transfer"
    const val DRIVE = "drive-sync"
    const val GUIDE = "welcome-guide"
    const val MANUAL = "user-manual"

    fun noteEdit(id: Long, query: String = ""): String =
        "note/$id?$QUERY_ARG=${Uri.encode(query)}"
}
