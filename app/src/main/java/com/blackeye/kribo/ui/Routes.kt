package com.blackeye.kribo.ui

object Routes {
    const val HOME = "home"
    const val NOTE_ID_ARG = "noteId"
    const val NOTE_EDIT = "note/{$NOTE_ID_ARG}"
    const val SETTINGS = "settings"

    fun noteEdit(id: Long): String = "note/$id"
}
